package com.netk.mvola.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netk.mvola.data.Song
import com.netk.mvola.data.local.PlaylistEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseAndroidApp(modifier: Modifier = Modifier, viewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }
    var audioGranted by remember { mutableStateOf(hasAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        audioGranted = result[audioPermission()] == true || hasAudioPermission(context)
        if (audioGranted) viewModel.scan()
    }
    LaunchedEffect(Unit) {
        if (audioGranted) viewModel.scan() else permissionLauncher.launch(requiredPermissions())
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("NetK Music", fontWeight = FontWeight.Bold) }) },
        bottomBar = { NavigationBar {
            listOf("Bibliothèque", "Lecteur", "Favoris", "Playlists").forEachIndexed { index, label ->
                NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Text(listOf("♫", "▶", "♥", "≡")[index]) }, label = { Text(label) })
            }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                0 -> LibraryPage(songs, loading, audioGranted, { permissionLauncher.launch(requiredPermissions()) }, viewModel::play, viewModel::toggleFavorite, favorites.toSet(), playlists, viewModel::addToPlaylist)
                1 -> PlayerPage(playback, viewModel::togglePlay, viewModel::previous, viewModel::next, viewModel::seekTo)
                2 -> SongList(songs.filter { it.uri in favorites }, "Aucun favori", viewModel::play, viewModel::toggleFavorite, favorites.toSet(), playlists, viewModel::addToPlaylist)
                else -> PlaylistsPage(playlists, viewModel::createPlaylist)
            }
            error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }, title = { Text("Une erreur est survenue") }, text = { Text(message) }) }
        }
    }
}

@Composable
private fun LibraryPage(songs: List<Song>, loading: Boolean, granted: Boolean, requestPermission: () -> Unit, play: (Song) -> Unit, favorite: (Song) -> Unit, favorites: Set<String>, playlists: List<PlaylistEntity>, addToPlaylist: (Long, Song) -> Unit) {
    when {
        !granted -> EmptyState("Autorisez l'accès aux fichiers audio pour détecter automatiquement vos musiques.") { Button(onClick = requestPermission) { Text("Autoriser l'accès") } }
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> SongList(songs, "Aucune musique MP3, WAV, M4A ou FLAC trouvée sur cet appareil.", play, favorite, favorites, playlists, addToPlaylist)
    }
}

@Composable
private fun SongList(songs: List<Song>, emptyText: String, play: (Song) -> Unit, favorite: (Song) -> Unit, favorites: Set<String>, playlists: List<PlaylistEntity>, addToPlaylist: (Long, Song) -> Unit) {
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    if (songs.isEmpty()) EmptyState(emptyText)
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(songs, key = { it.uri }) { song ->
            Card(Modifier.fillMaxWidth().clickable { play(song) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(song.durationMs), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { playlistSong = song }) { Text("+ Playlist") }
                        TextButton(onClick = { favorite(song) }) { Text(if (song.uri in favorites) "♥" else "♡") }
                    }
                }
            }
        }
    }
    playlistSong?.let { song -> AlertDialog(onDismissRequest = { playlistSong = null }, title = { Text("Ajouter à une playlist") }, text = {
        if (playlists.isEmpty()) Text("Créez d'abord une playlist dans l'onglet Playlists.")
        else Column { playlists.forEach { item -> TextButton(onClick = { addToPlaylist(item.id, song); playlistSong = null }) { Text(item.name) } } }
    }, confirmButton = { TextButton(onClick = { playlistSong = null }) { Text("Fermer") } }) }
}

@Composable
private fun PlayerPage(state: PlaybackState, toggle: () -> Unit, previous: () -> Unit, next: () -> Unit, seek: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (state.title.isBlank()) "Aucune lecture" else state.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(state.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Slider(value = state.positionMs.coerceAtMost(state.durationMs).toFloat(), onValueChange = { seek(it.toLong()) }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(), enabled = state.mediaId != null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(state.positionMs)); Text(formatDuration(state.durationMs)) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = previous, enabled = state.mediaId != null) { Text("Précédent") }
            Button(onClick = toggle, enabled = state.mediaId != null) { Text(if (state.isPlaying) "Pause" else "Lecture") }
            OutlinedButton(onClick = next, enabled = state.mediaId != null) { Text("Suivant") }
        }
    }
}

@Composable
private fun PlaylistsPage(playlists: List<PlaylistEntity>, create: (String) -> Unit) {
    var dialog by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { dialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Créer une playlist") }
        if (playlists.isEmpty()) Text("Aucune playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
        playlists.forEach { Card(Modifier.fillMaxWidth()) { Text(it.name, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium) } }
    }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("Nouvelle playlist") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { create(name); name = ""; dialog = false }) { Text("Créer") } }, dismissButton = { TextButton(onClick = { dialog = false }) { Text("Annuler") } })
}

@Composable private fun EmptyState(text: String, action: @Composable (() -> Unit)? = null) { Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant); action?.let { Spacer(Modifier.height(16.dp)); it() } } }
private fun formatDuration(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60) }
private fun audioPermission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
private fun requiredPermissions() = buildList { add(audioPermission()); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray()
private fun hasAudioPermission(context: android.content.Context) = ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
