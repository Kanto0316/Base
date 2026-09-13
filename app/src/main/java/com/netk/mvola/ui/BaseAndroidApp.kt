package com.netk.mvola.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netk.mvola.data.SaleRepository
import com.netk.mvola.data.local.NetKDatabase
import com.netk.mvola.ui.history.HistoryScreen
import com.netk.mvola.ui.home.HomeScreen
import com.netk.mvola.ui.navigation.Destination
import com.netk.mvola.ui.sales.AddSaleScreen
import com.netk.mvola.ui.settings.SettingsScreen

private data class NavItem(val destination: Destination, val label: String, val symbol: String)

@Composable
fun BaseAndroidApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = NetKDatabase.getInstance(context)
    val salesViewModel: SalesViewModel = viewModel(factory = SalesViewModel.Factory(SaleRepository(database.saleDao())))
    val items = listOf(
        NavItem(Destination.Home, "Accueil", "⌂"),
        NavItem(Destination.AddSale, "Ventes", "+"),
        NavItem(Destination.History, "Historique", "≡"),
        NavItem(Destination.Settings, "Paramètres", "⚙"),
    )
    val backStack by navController.currentBackStackEntryAsState()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = backStack?.destination?.route == item.destination.route,
                        onClick = { navController.navigate(item.destination.route) {
                            popUpTo(Destination.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        } },
                        icon = { Text(item.symbol) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, Destination.Home.route, Modifier.padding(padding)) {
            composable(Destination.Home.route) { HomeScreen(salesViewModel) { navController.navigate(Destination.AddSale.route) } }
            composable(Destination.AddSale.route) { AddSaleScreen(salesViewModel) { navController.navigate(Destination.Home.route) { popUpTo(Destination.Home.route) { inclusive = true } } } }
            composable(Destination.History.route) { HistoryScreen(salesViewModel) }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
