package com.netk.mvola.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.netk.mvola.data.MusicRepository
import com.netk.mvola.data.Song
import com.netk.mvola.data.local.NetKDatabase
import com.netk.mvola.playback.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application, NetKDatabase.getInstance(application).musicDao())
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    val favorites = repository.favoriteUris.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _loading = MutableStateFlow(false); val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    private val _playback = MutableStateFlow(PlaybackState()); val playback = _playback.asStateFlow()
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application, SessionToken(application, ComponentName(application, MusicService::class.java))).buildAsync()

    init {
        controllerFuture.addListener({
            controller = runCatching { controllerFuture.get() }.getOrElse { _error.value = "Connexion au lecteur impossible : ${it.message}"; return@addListener }
            updatePlayback()
        }, application.mainExecutor)
        viewModelScope.launch { while (isActive) { updatePlayback(); delay(500) } }
    }

    fun scan() { viewModelScope.launch {
        _loading.value = true; _error.value = null
        repository.scanSongs().fold({ _songs.value = it }, { _error.value = "Lecture de la bibliothèque impossible : ${it.message}" })
        _loading.value = false
    } }

    fun play(song: Song) {
        val player = controller ?: return
        val list = songs.value
        player.setMediaItems(list.map(::mediaItem), list.indexOf(song).coerceAtLeast(0), 0)
        player.prepare(); player.play(); updatePlayback()
    }
    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }; updatePlayback() }
    fun next() { controller?.seekToNextMediaItem(); controller?.play(); updatePlayback() }
    fun previous() { controller?.seekToPreviousMediaItem(); controller?.play(); updatePlayback() }
    fun seekTo(position: Long) { controller?.seekTo(position); updatePlayback() }
    fun toggleFavorite(song: Song) { viewModelScope.launch { repository.toggleFavorite(song) } }
    fun createPlaylist(name: String) { if (name.isNotBlank()) viewModelScope.launch { repository.createPlaylist(name) } }
    fun addToPlaylist(id: Long, song: Song) { viewModelScope.launch { repository.addToPlaylist(id, song) } }
    fun clearError() { _error.value = null }

    private fun updatePlayback() { controller?.let { player ->
        val duration = player.duration.takeIf { it > 0 } ?: 0
        _playback.value = PlaybackState(player.currentMediaItem?.mediaId, player.mediaMetadata.title?.toString().orEmpty(), player.mediaMetadata.artist?.toString().orEmpty(), player.isPlaying, player.currentPosition.coerceAtLeast(0), duration)
    } }
    private fun mediaItem(song: Song) = MediaItem.Builder().setMediaId(song.uri).setUri(song.uri).setMediaMetadata(
        MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()
    ).build()
    override fun onCleared() { MediaController.releaseFuture(controllerFuture); super.onCleared() }
}

data class PlaybackState(val mediaId: String? = null, val title: String = "", val artist: String = "", val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)
