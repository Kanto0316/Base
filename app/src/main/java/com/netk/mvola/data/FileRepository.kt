package com.netk.mvola.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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
    suspend fun scan(category: FileCategory): Result<List<LocalFile>> = runCatching {
        withContext(Dispatchers.IO) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.SIZE,
            )
            val selection = category.extensions.joinToString(" OR ") {
                "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?"
            }
            val arguments = category.extensions.map { "%.${it.lowercase(Locale.ROOT)}" }.toTypedArray()

            context.contentResolver.query(
                collection, projection, "($selection)", arguments,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                buildList {
                    while (cursor.moveToNext()) {
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
        }
    }
}
