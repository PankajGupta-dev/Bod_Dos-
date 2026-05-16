package com.alertnet.bordersentinelalert.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alertnet.bordersentinelalert.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController, viewModel: AlertViewModel) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onNavigateToLogin = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }
        
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToDetails = { id -> navController.navigate(Screen.Details.createRoute(id)) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument("alertId") { type = NavType.IntType })
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getInt("alertId") ?: return@composable
            DetailsScreen(alertId, viewModel, onNavigateBack = { navController.popBackStack() })
        }
        
        composable(Screen.History.route) {
            HistoryScreen(viewModel) // Existing History screen
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
