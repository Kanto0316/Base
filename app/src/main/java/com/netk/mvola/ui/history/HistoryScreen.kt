package com.netk.mvola.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netk.mvola.data.local.PaymentMethod
import com.netk.mvola.data.local.Sale
import com.netk.mvola.ui.SalesViewModel
import com.netk.mvola.ui.asCurrency
import com.netk.mvola.ui.asDate

@Composable
fun HistoryScreen(viewModel: SalesViewModel) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Historique", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
        OutlinedTextField(query, viewModel::search, Modifier.fillMaxWidth(), label = { Text("Rechercher un produit") }, leadingIcon = { Text("⌕") }, singleLine = true)
        if (sales.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp)) { Text(if (query.isBlank()) "Aucune vente enregistrée." else "Aucun résultat.") }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sales, key = Sale::id) { sale -> SaleCard(sale) { viewModel.delete(sale) } }
            }
        }
    }
}

@Composable
private fun SaleCard(sale: Sale, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sale.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(sale.totalAmount.asCurrency(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(sale.date.asDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Quantité : ${sale.quantity}  •  ${if (sale.paymentMethod == PaymentMethod.CASH) "Espèces" else "MVola"}")
                TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
