package com.example.baseandroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.baseandroid.ui.home.HomeRoute
import com.example.baseandroid.ui.navigation.Destination
import com.example.baseandroid.ui.settings.SettingsScreen

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

