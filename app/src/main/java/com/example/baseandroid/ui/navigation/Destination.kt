package com.example.baseandroid.ui.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Settings : Destination("settings")
}

