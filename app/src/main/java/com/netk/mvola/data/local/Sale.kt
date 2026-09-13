package com.netk.mvola.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val totalAmount: Long,
    val paymentMethod: PaymentMethod,
)

enum class PaymentMethod { CASH, MVOLA }
