package com.netk.mvola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FavoriteEntity::class, HistoryEntity::class, PlaylistEntity::class, PlaylistSongEntity::class], version = 3, exportSchema = false)
abstract class NetKDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    companion object {
        @Volatile private var instance: NetKDatabase? = null
        fun getInstance(context: Context): NetKDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NetKDatabase::class.java, "netk-music.db")
                .fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
