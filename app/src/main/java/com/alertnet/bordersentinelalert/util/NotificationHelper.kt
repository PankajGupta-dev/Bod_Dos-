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
        // Intent for clicking the notification (opens details)
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALERT_ID", alert.id)
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context, alert.id, clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for full screen emergency UI
        val fullScreenIntent = Intent(context, EmergencyAlertActivity::class.java).apply {
            putExtra("EXTRA_TITLE", alert.alertTitle)
            putExtra("EXTRA_DESC", alert.description)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, alert.id + 100, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 ${alert.alertTitle}")
            .setContentText(alert.description)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true) // High Priority Full Screen
            .setContentIntent(clickPendingIntent)
            .setColor(Color.RED)
            .setOngoing(true) // User must interact to dismiss
            .setVibrate(longArrayOf(0, 1000, 500, 1000))

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
