package com.netk.mvola.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromPaymentMethod(value: PaymentMethod): String = value.name
    @TypeConverter fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)
}
