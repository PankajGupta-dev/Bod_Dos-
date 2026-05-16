package com.alertnet.bordersentinelalert.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.alertnet.bordersentinelalert.data.repository.AlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlertReceiverService : Service() {

    @Inject
    lateinit var repository: AlertRepository

    // SupervisorJob ensures child coroutine failures don't cancel the whole scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        serviceScope.launch {
            while (true) {
                // Room's Flow-based repository auto-notifies observers on DB changes.
                // No manual refresh needed — alerts inserted via AlertRepository.insertAlert()
                // are immediately reflected in allAlerts / unreadAlerts flows.
                delay(30000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the scope to stop the polling loop and prevent coroutine leaks
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
