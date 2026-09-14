package com.netk.mvola.data

import android.content.Context
import android.net.Uri
import com.netk.mvola.data.local.ImageEntity
import com.netk.mvola.data.local.Note
import com.netk.mvola.data.local.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NoteRepository(private val context: Context, private val dao: NoteDao) {
    val notes = dao.observeNotes()
    fun note(id: Long) = dao.observeNote(id)

    suspend fun save(id: Long?, title: String, content: String, imageUris: List<Uri>): Result<Long> = runCatching {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val noteId = if (id == null) dao.insertNote(Note(title = title.trim(), content = content.trim())) else {
                val current = dao.observeNote(id).first()?.note ?: error("Cette note n'existe plus.")
                dao.updateNote(current.copy(title = title.trim(), content = content.trim(), updatedAt = now))
                id
            }
            val copied = mutableListOf<File>()
            try {
                val entities = imageUris.map { uri ->
                    val directory = File(context.filesDir, "note_images").apply { mkdirs() }
                    val target = File(directory, "${UUID.randomUUID()}.img")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use(input::copyTo)
                    } ?: error("Impossible de lire une image sélectionnée.")
                    copied += target
                    ImageEntity(noteId = noteId, filePath = target.absolutePath)
                }
                if (entities.isNotEmpty()) dao.insertImages(entities)
            } catch (error: Exception) {
                copied.forEach(File::delete)
                if (id == null) dao.deleteNote(noteId)
                throw error
            }
            noteId
        }
    }

    suspend fun delete(id: Long): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val paths = dao.observeNote(id).first()?.images.orEmpty().map { it.filePath }
            dao.deleteNote(id)
            val failed = paths.filter { File(it).exists() && !File(it).delete() }
            check(failed.isEmpty()) { "La note est supprimée, mais certaines images n'ont pas pu être effacées." }
        }
    }

    suspend fun deleteImage(image: ImageEntity): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            dao.deleteImage(image.id)
            val file = File(image.filePath)
            check(!file.exists() || file.delete()) { "L'image a été retirée, mais son fichier n'a pas pu être effacé." }
        }
    }
}
