package com.netk.mvola.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class FileCategory(
    val label: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val badge: String,
) {
    PDF("PDF", setOf("pdf"), setOf("application/pdf"), "PDF"),
    EXCEL(
        "Excel", setOf("xls", "xlsx"),
        setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ), "XLS",
    ),
    WORD(
        "Word", setOf("doc", "docx"),
        setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ), "DOC",
    ),
    TXT("TXT", setOf("txt"), setOf("text/plain"), "TXT"),
    IMAGES("Images", setOf("jpg", "jpeg", "png", "webp"), setOf("image/jpeg", "image/png", "image/webp"), "IMG"),
    ;

    fun matches(name: String, mimeType: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in extensions || mimeType?.lowercase()?.let(mimeTypes::contains) == true
    }
}

data class LocalFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
)

data class FileScanResult(
    val filesByCategory: Map<FileCategory, List<LocalFile>>,
    val mediaStoreErrors: List<String>,
)

/** Reads indexed shared storage and, when supplied, a user-authorized SAF tree. */
class FileRepository(private val context: Context) {
    suspend fun scanAll(treeUri: Uri?, includeImages: Boolean): FileScanResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val result = FileCategory.entries.associateWith { category ->
            if (category == FileCategory.IMAGES && !includeImages) {
                emptyList()
            } else {
                runCatching { scanMediaStore(category) }
                    .onFailure { error ->
                        Log.e(TAG, "MediaStore scan failed for ${category.name}", error)
                        errors += "${category.label} : ${error.localizedMessage ?: error.javaClass.simpleName}"
                    }.getOrDefault(emptyList())
            }
        }.mapValues { (_, files) -> files.toMutableList() }

        if (treeUri != null) {
            runCatching { scanDocumentTree(treeUri) }
                .onSuccess { treeFiles ->
                    treeFiles.forEach { file ->
                        FileCategory.entries.firstOrNull { it.matches(file.name, file.mimeType) }
                            ?.let { result.getValue(it).add(file) }
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "SAF tree scan failed for $treeUri", error)
                    errors += "Dossier sélectionné : ${error.localizedMessage ?: error.javaClass.simpleName}"
                }
        }

        val deduplicated = result.mapValues { (_, files) -> files.distinctBy { it.uri }.sortedByDescending { it.modifiedAtMillis } }
        Log.i(TAG, "Scan counts: ${deduplicated.map { "${it.key.name}=${it.value.size}" }.joinToString()}")
        FileScanResult(deduplicated, errors)
    }

    private suspend fun scanMediaStore(category: FileCategory): List<LocalFile> {
        val collection = when {
            category == FileCategory.IMAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            category == FileCategory.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
        )
        val clauses = category.extensions.map {
            "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?"
        }.toMutableList()
        val arguments = category.extensions.map { "%.${it.lowercase()}" }.toMutableList()
        category.mimeTypes.forEach { mime ->
            clauses += "LOWER(${MediaStore.Files.FileColumns.MIME_TYPE}) = ?"
            arguments += mime.lowercase()
        }
        return context.contentResolver.query(
            collection, projection, "(${clauses.joinToString(" OR ")})", arguments.toTypedArray(),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val id = cursor.getLong(idColumn)
                    add(LocalFile(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameColumn).orEmpty().ifBlank {
                        "Fichier sans nom"
                    }, cursor.getString(mimeColumn), cursor.getLong(dateColumn) * 1_000L, cursor.getLong(sizeColumn)))
                }
            }
        } ?: emptyList()
    }

    private suspend fun scanDocumentTree(treeUri: Uri): List<LocalFile> {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<String>().apply { add(rootId) }
        val files = mutableListOf<LocalFile>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parentId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val documentId = cursor.getString(0)
                    val mime = cursor.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(documentId)
                    } else {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        files += LocalFile(
                            id = documentId.hashCode().toLong(), uri = uri,
                            name = cursor.getString(1).orEmpty().ifBlank { "Fichier sans nom" }, mimeType = mime,
                            modifiedAtMillis = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                            sizeBytes = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                        )
                    }
                }
            }
        }
        return files
    }

    private companion object { const val TAG = "NetKFileRepository" }
}
