package com.alertnet.bordersentinelalert.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alertnet.bordersentinelalert.MainActivity
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import com.alertnet.bordersentinelalert.ui.screens.EmergencyAlertActivity

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "EMERGENCY_ALERT_CHANNEL"

    fun showEmergencyNotification(alert: AlertEntity) {
        val title = "⚠ ${alert.alertType.uppercase()} DETECTED"
        
        val statusText = when (alert.blockchainStatus) {
            "SUCCESS" -> "✓ Blockchain Verified"
            "FAILED" -> "⚠ Verification Failed (Possible Tampering)"
            else -> "Verifying..."
        }
        
        val body = """
            ${alert.personName ?: alert.unknownId ?: "Unknown Subject"}
            
            Camera:
            ${alert.cameraId}
            
            Confidence:
            ${(alert.confidence * 100).toInt()}%
            
            Threat:
            ${alert.threatLevel}
            
            Status:
            $statusText
        """.trimIndent()

        // 1. View Feed Action (Opens Main Dashboard)
        val feedIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ACTION", "VIEW_FEED")
            putExtra("CAMERA_ID", alert.cameraId)
        }
        val feedPendingIntent = PendingIntent.getActivity(
            context, alert.id + 1, feedIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. View Snapshot Action (Opens Emergency UI)
        val snapshotIntent = Intent(context, EmergencyAlertActivity::class.java).apply {
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_DESC", body)
            putExtra("EXTRA_IMAGE", alert.snapshotUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val snapshotPendingIntent = PendingIntent.getActivity(
            context, alert.id + 2, snapshotIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Dismiss Action
        val dismissIntent = Intent(context, com.alertnet.bordersentinelalert.service.NotificationActionReceiver::class.java).apply {
            action = "ACTION_DISMISS"
            putExtra("ALERT_ID", alert.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, alert.id + 3, dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(snapshotPendingIntent, true) // High Priority Full Screen
            .setContentIntent(feedPendingIntent)
            .setColor(Color.RED)
            .setOngoing(true) 
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .addAction(android.R.drawable.ic_menu_view, "View Feed", feedPendingIntent)
            .addAction(android.R.drawable.ic_menu_gallery, "View Snapshot", snapshotPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

        notificationManager.notify(alert.id, builder.build())
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Border Alerts"
            val descriptionText = "Critical alerts for border intrusions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
                lightColor = Color.RED
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
