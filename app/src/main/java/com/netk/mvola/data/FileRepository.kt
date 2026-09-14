package com.netk.mvola.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class FileCategory(val label: String, val extensions: Set<String>, val badge: String) {
    PDF("PDF", setOf("pdf"), "PDF"),
    EXCEL("Excel", setOf("xls", "xlsx"), "XLS"),
    WORD("Word", setOf("doc", "docx"), "DOC"),
    TXT("TXT", setOf("txt"), "TXT"),
    IMAGES("Images", setOf("jpg", "jpeg", "png", "webp"), "IMG"),
}

data class LocalFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
)

/** Reads indexed shared-storage files without copying or modifying them. */
class FileRepository(private val context: Context) {
    suspend fun scan(category: FileCategory): List<LocalFile> = withContext(Dispatchers.IO) {
        val collection = when {
            category == FileCategory.IMAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            category == FileCategory.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
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
        val arguments = category.extensions.map { "%.$it" }.toMutableList()
        if (category == FileCategory.PDF) {
            clauses += "LOWER(${MediaStore.Files.FileColumns.MIME_TYPE}) = ?"
            arguments += "application/pdf"
        } else if (category == FileCategory.IMAGES) {
            clauses += "LOWER(${MediaStore.Files.FileColumns.MIME_TYPE}) LIKE ?"
            arguments += "image/%"
        }

        val files = context.contentResolver.query(
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
                    add(LocalFile(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameColumn).orEmpty().ifBlank { "Fichier sans nom" },
                        mimeType = cursor.getString(mimeColumn),
                        modifiedAtMillis = cursor.getLong(dateColumn) * 1_000L,
                        sizeBytes = cursor.getLong(sizeColumn),
                    ))
                }
            }
        } ?: emptyList()
        Log.i(TAG, "MediaStore scan ${category.name}: ${files.size} file(s) found")
        files
    }

    private companion object {
        const val TAG = "NetKFileRepository"
    }
}
