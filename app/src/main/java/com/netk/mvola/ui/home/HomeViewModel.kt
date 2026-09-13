package com.netk.mvola.ui.home

import androidx.lifecycle.ViewModel

data class HomeUiState(
    val capabilities: List<String> = listOf(
        "Authentification utilisateur",
        "Services Firebase",
        "API REST",
        "Stockage local Room",
        "Notifications",
    ),
)

class HomeViewModel : ViewModel() {
    val uiState = HomeUiState()
}

