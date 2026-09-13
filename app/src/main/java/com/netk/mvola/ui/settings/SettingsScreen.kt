package com.netk.mvola.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("netk_settings", Context.MODE_PRIVATE) }
    var shopName by remember { mutableStateOf(preferences.getString("shop_name", "Ma boutique").orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(shopName, { shopName = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Nom de la boutique") }, singleLine = true)
        OutlinedTextField("Ariary (Ar)", {}, Modifier.fillMaxWidth(), label = { Text("Devise par défaut") }, enabled = false)
        Button(onClick = { preferences.edit().putString("shop_name", shopName.trim()).apply(); saved = true }, modifier = Modifier.fillMaxWidth()) { Text("ENREGISTRER") }
        if (saved) Text("Paramètres enregistrés sur cet appareil.", color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("Informations application", style = MaterialTheme.typography.titleMedium)
        Text("NetK Mini Caisse • Version 1.0\nGestion de ventes simple, rapide et entièrement hors ligne.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Prêt pour les futures extensions : synchronisation Firebase, lecture SMS MVola, notifications et export PDF.", style = MaterialTheme.typography.bodySmall)
    }
}
