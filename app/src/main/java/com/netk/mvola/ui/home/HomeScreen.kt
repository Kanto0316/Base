package com.netk.mvola.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netk.mvola.ui.SalesViewModel
import com.netk.mvola.ui.asCurrency

@Composable
fun HomeScreen(viewModel: SalesViewModel, onAddSale: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("NetK", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Mini Caisse", style = MaterialTheme.typography.titleLarge)
            Text("Votre activité aujourd'hui", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { StatCard("Chiffre d'affaires du jour", stats.todayRevenue.asCurrency(), true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { StatCard("Ventes totales", stats.saleCount.toString()) }
                Box(Modifier.weight(1f)) { StatCard("Paiements MVola", stats.mvolaTotal.asCurrency()) }
            }
        }
        item { StatCard("Paiements espèces", stats.cashTotal.asCurrency()) }
        item { Button(onClick = onAddSale, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("+  AJOUTER UNE VENTE") } }
    }
}

@Composable
private fun StatCard(label: String, value: String, featured: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (featured) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
