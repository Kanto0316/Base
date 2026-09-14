package com.netk.mvola.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT uri FROM favorites ORDER BY title COLLATE NOCASE") fun observeFavoriteUris(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri)") suspend fun isFavorite(uri: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFavorite(item: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE uri = :uri") suspend fun deleteFavorite(uri: String)
    @Insert suspend fun insertHistory(item: HistoryEntity)
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") fun observePlaylists(): Flow<List<PlaylistEntity>>
    @Insert suspend fun insertPlaylist(item: PlaylistEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlaylistSong(item: PlaylistSongEntity)
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY title COLLATE NOCASE") fun observePlaylistSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>
}
