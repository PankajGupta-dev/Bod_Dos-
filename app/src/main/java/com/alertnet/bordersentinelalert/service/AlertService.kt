package com.alertnet.bordersentinelalert.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity
import com.alertnet.bordersentinelalert.data.remote.AlertWebSocketManager
import com.alertnet.bordersentinelalert.data.repository.AlertRepository
import com.alertnet.bordersentinelalert.util.ConnectivityObserver
import com.alertnet.bordersentinelalert.util.NotificationHelper
import com.alertnet.bordersentinelalert.util.SoundUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlertService : Service() {

    @Inject
    lateinit var webSocketManager: AlertWebSocketManager

    @Inject
    lateinit var repository: AlertRepository

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var connectivityObserver: ConnectivityObserver
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
        connectivityObserver = ConnectivityObserver(this)
        
        startForegroundService()
        
        // Monitor Internet Connection
        connectivityObserver.observe().onEach { status ->
            Log.d("AlertService", "Network Status: $status")
            if (status == ConnectivityObserver.Status.Available) {
                webSocketManager.connect()
            }
        }.launchIn(serviceScope)

        webSocketManager.onAlertReceived = { alert ->
            handleIncomingAlert(alert)
        }
    }

    private fun handleIncomingAlert(alert: AlertEntity) {
        serviceScope.launch {
            repository.insertAlert(alert)
            if (alert.threatLevel == "HIGH") {
                notificationHelper.showEmergencyNotification(alert)
                SoundUtils.playEmergencyBuzzer(this@AlertService)
            } else {
                notificationHelper.showEmergencyNotification(alert)
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "ALERT_SERVICE_CHANNEL"
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Border Sentinel Active")
            .setContentText("Monitoring secure WebSocket stream...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.disconnect()
        SoundUtils.stopBuzzer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
