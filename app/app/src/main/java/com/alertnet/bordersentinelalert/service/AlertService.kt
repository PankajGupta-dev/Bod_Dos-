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
import com.alertnet.bordersentinelalert.data.remote.AlertApiService
import com.alertnet.bordersentinelalert.data.remote.VerificationResponse
import com.alertnet.bordersentinelalert.data.repository.AlertRepository
import com.alertnet.bordersentinelalert.util.ConnectivityObserver
import com.alertnet.bordersentinelalert.util.NotificationHelper
import com.alertnet.bordersentinelalert.util.SoundUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AlertService : Service() {

    @Inject
    lateinit var webSocketManager: AlertWebSocketManager

    @Inject
    lateinit var repository: AlertRepository

    @Inject
    lateinit var apiService: AlertApiService

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var connectivityObserver: ConnectivityObserver
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val alertCooldownMap = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 30000L

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
        connectivityObserver = ConnectivityObserver(this)
        
        startForegroundService()
        
        // Monitor Internet Connection
        connectivityObserver.observe().onEach { status ->
            Timber.d("Network Status Changed: $status")
            if (status == ConnectivityObserver.Status.Available) {
                webSocketManager.connect()
            }
        }.launchIn(serviceScope)

        webSocketManager.onAlertReceived = { alert ->
            handleIncomingAlert(alert)
        }
    }

    private fun handleIncomingAlert(alert: AlertEntity) {
        val currentTime = System.currentTimeMillis()
        val key = "${alert.alertType}_${alert.cameraId}"
        val lastTime = alertCooldownMap[key] ?: 0L

        if (currentTime - lastTime < COOLDOWN_MS) {
            Timber.d("Filtered redundant alert: $key")
            return
        }

        alertCooldownMap[key] = currentTime

        serviceScope.launch {
            repository.insertAlert(alert)
            Timber.i("New Surveillance Event: ${alert.alertType} from ${alert.cameraId}")
            
            // Trigger initial notification with 'Verifying...' status
            notificationHelper.showEmergencyNotification(alert)
            
            if (alert.confidence > 0.8 || alert.alertType == "INTRUSION" || alert.alertType == "WEAPON" || alert.alertType == "FIRE") {
                SoundUtils.playEmergencyBuzzer(this@AlertService)
            }

            // Start Blockchain Verification in background
            verifyBlockchain(alert)
        }
    }

    private fun verifyBlockchain(alert: AlertEntity) {
        serviceScope.launch {
            try {
                if (alert.blockchainHash.isNullOrEmpty()) {
                    repository.updateBlockchainStatus(alert.id, "FAILED")
                    return@launch
                }

                val response = apiService.verifyBlockchainHash(mapOf("hash" to alert.blockchainHash))
                val newStatus = if (response.verified) "SUCCESS" else "FAILED"
                
                repository.updateBlockchainStatus(alert.id, newStatus)
                
                // Update the existing notification with the result
                val updatedAlert = alert.copy(blockchainStatus = newStatus)
                notificationHelper.showEmergencyNotification(updatedAlert)
                
                Timber.i("Blockchain Verification result for alert ${alert.id}: $newStatus")
            } catch (e: Exception) {
                Timber.e(e, "Blockchain Verification failed for alert ${alert.id}")
                repository.updateBlockchainStatus(alert.id, "FAILED")
                notificationHelper.showEmergencyNotification(alert.copy(blockchainStatus = "FAILED"))
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
