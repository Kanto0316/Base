package com.netk.mvola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Sale::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NetKDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao

    companion object {
        @Volatile private var instance: NetKDatabase? = null

        fun getInstance(context: Context): NetKDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NetKDatabase::class.java,
                "netk-mini-caisse.db",
            ).build().also { instance = it }
        }
    }
}
