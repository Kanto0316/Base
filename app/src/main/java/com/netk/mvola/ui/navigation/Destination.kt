package com.netk.mvola.ui.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object AddSale : Destination("sales")
    data object History : Destination("history")
    data object Settings : Destination("settings")
}
