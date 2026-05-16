package com.alertnet.bordersentinelalert.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Details : Screen("details/{alertId}") {
        fun createRoute(alertId: Int) = "details/$alertId"
    }
    object History : Screen("history")
    object Settings : Screen("settings")
}
