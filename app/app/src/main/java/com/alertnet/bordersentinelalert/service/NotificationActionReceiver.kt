package com.alertnet.bordersentinelalert.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val alertId = intent.getIntExtra("ALERT_ID", -1)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            "ACTION_DISMISS" -> {
                Log.d("NotificationAction", "Dismissing alert: $alertId")
                notificationManager.cancel(alertId)
            }
            "ACTION_VIEW_SNAPSHOT" -> {
                // This could be handled directly by the PendingIntent in NotificationHelper, 
                // but if we want to log it or do something else, we do it here.
                Log.d("NotificationAction", "Viewing snapshot for alert: $alertId")
            }
        }
    }
}
