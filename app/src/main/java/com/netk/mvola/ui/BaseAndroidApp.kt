package com.netk.mvola.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.netk.mvola.ui.home.HomeRoute
import com.netk.mvola.ui.navigation.Destination
import com.netk.mvola.ui.settings.SettingsScreen

@Composable
fun BaseAndroidApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            HomeRoute(onOpenSettings = { navController.navigate(Destination.Settings.route) })
        }
        composable(Destination.Settings.route) {
            SettingsScreen(onBack = navController::navigateUp)
        }
    }
}

