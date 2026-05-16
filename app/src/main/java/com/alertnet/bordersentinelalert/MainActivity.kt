package com.alertnet.bordersentinelalert

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.alertnet.bordersentinelalert.service.AlertService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.alertnet.bordersentinelalert.ui.navigation.NavGraph
import com.alertnet.bordersentinelalert.ui.screens.AlertViewModel
import com.alertnet.bordersentinelalert.ui.theme.BorderSentinelAlertTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // Changed to FragmentActivity for Biometric support
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURITY: Prevent screenshot and screen recording on all app screens
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Start Real-time Alert Monitoring Service
        val serviceIntent = Intent(this, AlertService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            BorderSentinelAlertTheme {
                val navController = rememberNavController()
                val viewModel: AlertViewModel = hiltViewModel()
                NavGraph(navController = navController, viewModel = viewModel)
            }
        }
    }
}