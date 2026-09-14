package com.netk.mvola.ui.notes

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netk.mvola.data.local.ImageEntity
import com.netk.mvola.data.local.NoteWithImages
import com.netk.mvola.ui.NotesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: NotesViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("NetK Notes", fontWeight = FontWeight.Bold) }) }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = onCreate, text = { Text("Nouvelle note") }, icon = { Text("+") })
    }) { padding ->
        if (notes.isEmpty()) Box(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
            Text("Aucune note. Touchez « Nouvelle note » pour commencer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(notes, key = { it.note.id }) { item -> NoteCard(item) { onOpen(item.note.id) } }
        }
    }
}

@Composable private fun NoteCard(item: NoteWithImages, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(item.note.title.ifBlank { "Sans titre" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (item.note.content.isNotBlank()) Text(item.note.content, maxLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDate(item.note.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.images.isNotEmpty()) Text("${item.images.size} image(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditorScreen(viewModel: NotesViewModel, id: Long?, onBack: () -> Unit) {
    val existing by (id?.let(viewModel::note) ?: flowOf(null)).collectAsStateWithLifecycle(initialValue = null)
    var title by rememberSaveable(id) { mutableStateOf("") }; var content by rememberSaveable(id) { mutableStateOf("") }
    var initialized by remember(id) { mutableStateOf(id == null) }; var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
    LaunchedEffect(existing) { if (!initialized && existing != null) { title = existing!!.note.title; content = existing!!.note.content; initialized = true } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected = selected + it }
    Scaffold(topBar = { TopAppBar(title = { Text(if (id == null) "Créer une note" else "Modifier la note") }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Titre") }, singleLine = true)
            OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Contenu") })
            TextButton(onClick = { picker.launch("image/*") }) { Text("+ Ajouter des images${if (selected.isEmpty()) "" else " (${selected.size})"}") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving && (title.isNotBlank() || content.isNotBlank()), onClick = {
                saving = true; error = null
                viewModel.save(id, title, content, selected) { result -> saving = false; result.fold({ onBack() }, { error = it.message ?: "Erreur lors de l'enregistrement." }) }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Enregistrement…" else "Enregistrer") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NoteDetailScreen(viewModel: NotesViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val item by viewModel.note(id).collectAsStateWithLifecycle(initialValue = null); var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("NetK Notes") }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }, actions = { TextButton(onClick = onEdit) { Text("Modifier") } }) }) { padding ->
        val current = item
        if (current == null) Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("Note introuvable.") }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(current.note.title.ifBlank { "Sans titre" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            item { Text("Créée ${formatDate(current.note.createdAt)}\nModifiée ${formatDate(current.note.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (current.note.content.isNotBlank()) item { Text(current.note.content, style = MaterialTheme.typography.bodyLarge) }
            if (current.images.isNotEmpty()) item { Text("Images", style = MaterialTheme.typography.titleMedium); ImageStrip(current.images) { image -> viewModel.deleteImage(image) { it.exceptionOrNull()?.let { e -> error = e.message } } } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { OutlinedButton(onClick = { viewModel.delete(id) { result -> result.fold({ onBack() }, { error = it.message ?: "Suppression impossible." }) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Supprimer la note") } }
        }
    }
}

@Composable private fun ImageStrip(images: List<ImageEntity>, onDelete: (ImageEntity) -> Unit) { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(images, key = { it.id }) { image -> Column { StoredImage(image.filePath); TextButton(onClick = { onDelete(image) }) { Text("Retirer") } } } } }

@Composable private fun StoredImage(path: String) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { if (File(path).isFile) BitmapFactory.decodeFile(path) else null } }
    if (bitmap == null) Surface(Modifier.size(180.dp, 130.dp), color = MaterialTheme.colorScheme.errorContainer) { Box(Modifier.padding(12.dp)) { Text("Image indisponible") } }
    else Image(bitmap!!.asImageBitmap(), null, Modifier.size(180.dp, 130.dp), contentScale = ContentScale.Crop)
}

private fun formatDate(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
