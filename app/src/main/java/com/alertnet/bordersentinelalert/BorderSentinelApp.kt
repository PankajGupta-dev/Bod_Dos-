package com.alertnet.bordersentinelalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.alertnet.bordersentinelalert.BuildConfig
import com.alertnet.bordersentinelalert.service.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BorderSentinelApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Logging Setup
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // PRODUCTION: Only log high-priority errors, no sensitive data
            Timber.plant(ReleaseTree())
        }

        createNotificationChannel()
        setupPeriodicSync()
    }

    /**
     * Custom Timber Tree for production releases.
     * Prevents sensitive logs from leaking and handles crash reporting.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return
            }
            // In a real app, you would send high-priority logs (Error/Warning) 
            // to a crash reporting tool like Firebase Crashlytics here.
        }
    }

    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AlertSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            val channels = listOf(
                NotificationChannel("BORDER_ALERT_CHANNEL", "Border Alerts", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("EMERGENCY_ALERT_CHANNEL", "Critical Emergency", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                },
                NotificationChannel("ALERT_SERVICE_CHANNEL", "Service Status", NotificationManager.IMPORTANCE_LOW)
            )
            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }
}
