package com.netk.mvola.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.netk.mvola.data.SaleRepository
import com.netk.mvola.data.local.PaymentMethod
import com.netk.mvola.data.local.Sale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardStats(
    val todayRevenue: Long = 0,
    val saleCount: Int = 0,
    val mvolaTotal: Long = 0,
    val cashTotal: Long = 0,
)

class SalesViewModel(private val repository: SaleRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query
    val sales = query.flatMapLatest(repository::search)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats = repository.sales.map { sales ->
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = sales.filter { it.date >= start }
        DashboardStats(
            todayRevenue = today.sumOf { it.totalAmount },
            saleCount = sales.size,
            mvolaTotal = today.filter { it.paymentMethod == PaymentMethod.MVOLA }.sumOf { it.totalAmount },
            cashTotal = today.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.totalAmount },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    fun search(value: String) { query.value = value }
    fun addSale(product: String, unitPrice: Long, quantity: Int, method: PaymentMethod, done: () -> Unit) {
        viewModelScope.launch {
            repository.add(Sale(productName = product.trim(), unitPrice = unitPrice,
                quantity = quantity, totalAmount = unitPrice * quantity, paymentMethod = method))
            done()
        }
    }
    fun delete(sale: Sale) = viewModelScope.launch { repository.delete(sale) }

    class Factory(private val repository: SaleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SalesViewModel(repository) as T
    }
}
