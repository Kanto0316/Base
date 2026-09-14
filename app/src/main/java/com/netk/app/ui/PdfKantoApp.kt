package com.netk.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netk.app.data.ProjectWithImages
import com.netk.app.data.SelectedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class Destination { CONVERT, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfKantoApp(viewModel: PdfViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(Destination.CONVERT) }
    var detail by remember { mutableStateOf<ProjectWithImages?>(null) }
    var askName by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }
    var permissionRefused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.selectAllImages()
        permissionRefused = !granted
    }
    val selectAll = {
        val required = imagePermission()
        when {
            required == null || ContextCompat.checkSelfPermission(context, required) == PackageManager.PERMISSION_GRANTED ->
                viewModel.selectAllImages()
            else -> permission.launch(required)
        }
    }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (detail == null) "PDF by Kanto" else detail!!.project.name, fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (destination == Destination.CONVERT && detail == null) {
                Button(
                    onClick = { askName = true },
                    enabled = state.selectedImages.isNotEmpty() && !state.isWorking,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                ) { Text(if (state.isWorking) "Conversion…" else "Convertir en PDF") }
            } else if (detail == null) {
                NavigationBar {
                    NavigationBarItem(destination == Destination.CONVERT, { destination = Destination.CONVERT }, icon = { Text("＋") }, label = { Text("Créer") })
                    NavigationBarItem(destination == Destination.HISTORY, { destination = Destination.HISTORY }, icon = { Text("▤") }, label = { Text("Historique") })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                detail != null -> ProjectDetail(detail!!, { detail = null }, { openPdf(context, detail!!.project.pdfPath) })
                destination == Destination.HISTORY -> HistoryScreen(projects) { detail = it }
                else -> ConverterScreen(
                    state.selectedImages,
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    selectAll,
                    viewModel::removeImage,
                    { destination = Destination.HISTORY },
                )
            }
            if (state.isWorking) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (askName) AlertDialog(
        onDismissRequest = { askName = false },
        title = { Text("Nom du projet") },
        text = { OutlinedTextField(projectName, { projectName = it }, label = { Text("Projet") }, singleLine = true) },
        confirmButton = { Button({ askName = false; viewModel.convert(projectName) { destination = Destination.HISTORY }; projectName = "" }) { Text("Créer le PDF") } },
        dismissButton = { TextButton({ askName = false }) { Text("Annuler") } },
    )
    if (state.error != null) AlertDialog(
        onDismissRequest = viewModel::clearNotice,
        title = { Text("Action impossible") }, text = { Text(state.error!!) },
        confirmButton = { TextButton(viewModel::clearNotice) { Text("OK") } },
    )
    if (permissionRefused && imagePermission()?.let { ContextCompat.checkSelfPermission(context, it) } != PackageManager.PERMISSION_GRANTED) {
        AlertDialog(
            onDismissRequest = { permissionRefused = false },
            title = { Text("Accès aux images") },
            text = { Text("L’autorisation est nécessaire uniquement pour sélectionner toutes les images. Vous pouvez toujours utiliser le sélecteur Android sans cette autorisation.") },
            confirmButton = { TextButton({ permissionRefused = false }) { Text("Compris") } },
        )
    }
}

@Composable
private fun ConverterScreen(images: List<SelectedImage>, pick: () -> Unit, selectAll: () -> Unit, remove: (SelectedImage) -> Unit, history: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(pick, Modifier.weight(1f)) { Text("+ Sélectionner des images") }
            OutlinedButton(selectAll) { Text("Tout sélectionner") }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Images sélectionnées", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${images.size} image${if (images.size > 1) "s" else ""}", color = MaterialTheme.colorScheme.primary)
        }
        if (images.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Aucune image sélectionnée", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(history) { Text("Voir l’historique des projets") }
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(images, key = { it.uri.toString() }) { image -> ImageRow(image, remove) }
        }
        NavigationBar {
            NavigationBarItem(true, {}, icon = { Text("＋") }, label = { Text("Créer") })
            NavigationBarItem(false, history, icon = { Text("▤") }, label = { Text("Historique") })
        }
    }
}

@Composable
private fun ImageRow(image: SelectedImage, remove: ((SelectedImage) -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        ImageThumbnail(image.uri)
        Text(image.name, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (remove != null) TextButton({ remove(image) }) { Text("Retirer") }
    }
    HorizontalDivider(Modifier.padding(start = 84.dp))
}

@Composable
private fun ImageThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    Card(Modifier.size(56.dp), shape = RoundedCornerShape(10.dp)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("IMG", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun HistoryScreen(projects: List<ProjectWithImages>, open: (ProjectWithImages) -> Unit) {
    if (projects.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Aucun projet enregistré") }
    else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Historique des projets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(4.dp)) }
        items(projects, key = { it.project.id }) { item ->
            Card(Modifier.fillMaxWidth().clickable { open(item) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("PDF", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(item.project.name, fontWeight = FontWeight.SemiBold)
                        Text(formatDate(item.project.createdAt), style = MaterialTheme.typography.bodySmall)
                        Text("${item.images.size} image${if (item.images.size > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDetail(project: ProjectWithImages, back: () -> Unit, openPdf: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(back) { Text("← Retour") }
            Button(openPdf, Modifier.weight(1f)) { Text("Ouvrir le PDF") }
        }
        Text("${formatDate(project.project.createdAt)} · ${project.images.size} images", Modifier.padding(16.dp))
        LazyColumn { items(project.orderedImages, key = { it.id }) { ImageRow(SelectedImage(Uri.parse(it.uri), it.displayName), null) } }
    }
}

private fun openPdf(context: Context, path: String) {
    val file = File(path)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Ouvrir le PDF"))
}

private fun imagePermission(): String? = when {
    Build.VERSION.SDK_INT >= 33 -> Manifest.permission.READ_MEDIA_IMAGES
    Build.VERSION.SDK_INT >= 26 -> Manifest.permission.READ_EXTERNAL_STORAGE
    else -> null
}

private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
