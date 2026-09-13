package com.netk.mvola.data

import com.netk.mvola.data.local.Sale
import com.netk.mvola.data.local.SaleDao

class SaleRepository(private val dao: SaleDao) {
    val sales = dao.observeSales()
    fun search(query: String) = if (query.isBlank()) sales else dao.searchSales(query.trim())
    suspend fun add(sale: Sale) = dao.insert(sale)
    suspend fun delete(sale: Sale) = dao.delete(sale)
}
