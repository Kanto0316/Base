package com.netk.mvola.ui.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Editor : Destination("editor?id={id}") {
        fun route(id: Long? = null) = "editor?id=${id ?: -1}"
    }
    data object Detail : Destination("detail/{id}") {
        fun route(id: Long) = "detail/$id"
    }
}
