package com.netk.mvola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities = [Note::class, ImageEntity::class], version = 2, exportSchema = false)
abstract class NetKDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var instance: NetKDatabase? = null

        fun getInstance(context: Context): NetKDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NetKDatabase::class.java,
                "netk-notes.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
