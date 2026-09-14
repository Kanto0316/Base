package com.netk.mvola.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.netk.mvola.data.FileCategory
import com.netk.mvola.data.LocalFile
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseAndroidApp(modifier: Modifier = Modifier, viewModel: FileViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var openError by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember(state.category) { mutableStateOf(hasPermissionFor(context, state.category)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionGranted = grants.values.any { it } || hasPermissionFor(context, state.category)
        if (permissionGranted) viewModel.scan()
    }
    LaunchedEffect(Unit) { if (permissionGranted) viewModel.scan() }
    val visibleFiles = remember(state.files, query) {
        state.files.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(title = { Text("NetK File Manager", fontWeight = FontWeight.Bold) })
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("Rechercher un fichier") }, leadingIcon = { Text("⌕") },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                )
                FileTabs(state.category) { category ->
                    query = ""
                    permissionGranted = hasPermissionFor(context, category)
                    viewModel.selectCategory(category, scanNow = permissionGranted)
                    requiredPermissions(category).takeIf { !permissionGranted && it.isNotEmpty() }
                        ?.let(permissionLauncher::launch)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionGranted -> PermissionState {
                    requiredPermissions(state.category).takeIf { it.isNotEmpty() }?.let(permissionLauncher::launch)
                }
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                visibleFiles.isEmpty() -> EmptyState(if (query.isBlank()) {
                    "Aucun fichier ${state.category.label} trouvé sur cet appareil."
                } else "Aucun résultat pour « ${query.trim()} ».")
                else -> FileList(visibleFiles, state.category) { file ->
                    openFile(context, file).onFailure {
                        openError = if (it is NoFileViewerException) {
                            "Aucune application compatible n’est installée pour ouvrir ${file.name}."
                        } else {
                            "Impossible d’ouvrir ${file.name} : ${it.localizedMessage ?: "erreur inconnue"}."
                        }
                    }
                }
            }
        }
    }

    (state.error ?: openError)?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError(); openError = null },
            title = { Text("Action impossible") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.clearError(); openError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun FileTabs(selected: FileCategory, onSelected: (FileCategory) -> Unit) {
    val categories = FileCategory.entries
    TabRow(selectedTabIndex = categories.indexOf(selected)) {
        categories.forEach { category ->
            Tab(selected == category, { onSelected(category) }, text = { Text(category.label, maxLines = 1) })
        }
    }
}

@Composable
private fun FileList(files: List<LocalFile>, category: FileCategory, open: (LocalFile) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(files, key = { it.uri.toString() }) { file ->
            Row(
                Modifier.fillMaxWidth().clickable { open(file) }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(Modifier.size(42.dp), RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(category.badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        "${formatDate(file.modifiedAtMillis)} · ${formatSize(file.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PermissionState(request: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("L’accès a été refusé. Autorisez l’accès aux fichiers dans les réglages Android pour afficher cette catégorie.")
        Button(request, Modifier.padding(top = 16.dp)) { Text("Autoriser l’accès") }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openFile(context: Context, file: LocalFile): Result<Unit> = runCatching {
    Log.i(FILE_OPEN_TAG, "Opening selected file: name=${file.name}, uri=${file.uri}")
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, file.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (intent.resolveActivity(context.packageManager) == null) {
        throw NoFileViewerException()
    }
    context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
}.onFailure {
    Log.e(FILE_OPEN_TAG, "Failed to open ${file.uri}: ${it.message}", it)
}

private class NoFileViewerException : Exception("No application handles this file type")

private fun requiredPermissions(category: FileCategory): Array<String> = when {
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    category == FileCategory.IMAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    category == FileCategory.IMAGES -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> emptyArray()
}

private fun hasPermissionFor(context: Context, category: FileCategory): Boolean {
    val permissions = requiredPermissions(category)
    return permissions.isEmpty() || permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private const val FILE_OPEN_TAG = "NetKFileOpen"

private fun formatDate(timestamp: Long) =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))

private fun formatSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes o"
    bytes < 1_048_576 -> String.format(Locale.getDefault(), "%.1f Ko", bytes / 1_024.0)
    bytes < 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f Mo", bytes / 1_048_576.0)
    else -> String.format(Locale.getDefault(), "%.1f Go", bytes / 1_073_741_824.0)
}
