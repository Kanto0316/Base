package com.netk.mvola.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE productName LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchSales(query: String): Flow<List<Sale>>

    @Insert
    suspend fun insert(sale: Sale)

    @Delete
    suspend fun delete(sale: Sale)
}
