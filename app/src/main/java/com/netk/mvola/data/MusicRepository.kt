package com.netk.mvola.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.netk.mvola.data.local.FavoriteEntity
import com.netk.mvola.data.local.HistoryEntity
import com.netk.mvola.data.local.MusicDao
import com.netk.mvola.data.local.PlaylistEntity
import com.netk.mvola.data.local.PlaylistSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A playable audio row returned by Android's shared MediaStore. */
data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String?,
)

class MusicRepository(private val context: Context, private val dao: MusicDao) {
    val favoriteUris: Flow<List<String>> = dao.observeFavoriteUris()
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()

    suspend fun scanSongs(): Result<List<Song>> = runCatching { withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID); add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST); add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION); add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.DATA)
            else add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()
        val extensions = arrayOf(".mp3", ".wav", ".m4a", ".flac")
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val pathColumn = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) MediaStore.Audio.Media.DATA else MediaStore.Audio.Media.RELATIVE_PATH
            val path = cursor.getColumnIndex(pathColumn)
            buildList {
                while (cursor.moveToNext()) {
                    val filePath = if (path >= 0) cursor.getString(path) else null
                    val type = cursor.getString(mime).orEmpty().lowercase()
                    if (type in SUPPORTED_MIME_TYPES || extensions.any { filePath?.lowercase()?.endsWith(it) == true }) {
                        val mediaId = cursor.getLong(id)
                        add(Song(mediaId, ContentUris.withAppendedId(collection, mediaId).toString(),
                            cursor.getString(title).orEmpty().ifBlank { "Titre inconnu" },
                            cursor.getString(artist).orEmpty().takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Artiste inconnu" },
                            cursor.getString(album).orEmpty().takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Album inconnu" },
                            cursor.getLong(duration), filePath))
                    }
                }
            }
        } ?: emptyList()
    } }

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        if (dao.isFavorite(song.uri)) dao.deleteFavorite(song.uri)
        else dao.insertFavorite(FavoriteEntity(song.uri, song.title, song.artist, song.album, song.durationMs))
    }
    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) { dao.insertPlaylist(PlaylistEntity(name = name.trim())) }
    suspend fun addToPlaylist(playlistId: Long, song: Song) = withContext(Dispatchers.IO) {
        dao.insertPlaylistSong(PlaylistSongEntity(playlistId, song.uri, song.title, song.artist, song.album, song.durationMs))
    }

    companion object {
        private val SUPPORTED_MIME_TYPES = setOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/x-m4a", "audio/flac")
    }
}
