package com.netk.mvola.ui.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.netk.mvola.data.local.PaymentMethod
import com.netk.mvola.ui.SalesViewModel
import com.netk.mvola.ui.asCurrency

@Composable
fun AddSaleScreen(viewModel: SalesViewModel, onSaved: () -> Unit) {
    var product by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val priceValue = price.toLongOrNull()
    val quantityValue = quantity.toIntOrNull()
    val valid = product.isNotBlank() && priceValue != null && priceValue > 0 && quantityValue != null && quantityValue > 0
    val total = (priceValue ?: 0L) * (quantityValue ?: 0)

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajouter une vente", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Nom du produit *") }, singleLine = true)
        OutlinedTextField(price, { price = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Prix unitaire (Ar) *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Quantité *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text("Mode de paiement", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(method == PaymentMethod.CASH, { method = PaymentMethod.CASH }, { Text("Espèces") })
            FilterChip(method == PaymentMethod.MVOLA, { method = PaymentMethod.MVOLA }, { Text("MVola") })
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Montant total")
                Text(total.asCurrency(), style = MaterialTheme.typography.titleLarge)
            }
        }
        if (attempted && !valid) Text("Renseignez correctement tous les champs obligatoires.", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                attempted = true
                if (valid) viewModel.addSale(product, priceValue!!, quantityValue!!, method, onSaved)
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("ENREGISTRER LA VENTE") }
    }
}
