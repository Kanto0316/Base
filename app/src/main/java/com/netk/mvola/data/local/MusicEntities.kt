package com.netk.mvola.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val uri: String, val title: String, val artist: String, val album: String, val durationMs: Long)

@Entity(tableName = "history")
data class HistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uri: String, val title: String, val artist: String, val playedAt: Long = System.currentTimeMillis())

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long = System.currentTimeMillis())

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "uri"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId")],
)
data class PlaylistSongEntity(val playlistId: Long, val uri: String, val title: String, val artist: String, val album: String, val durationMs: Long)
