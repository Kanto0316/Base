package com.netk.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SelectedImage(val uri: Uri, val name: String)

class PdfRepository(private val context: Context) {
    private val dao = ProjectDatabase.get(context).projectDao()

    fun observeProjects(): Flow<List<ProjectWithImages>> = dao.observeProjects()

    suspend fun importImages(uris: List<Uri>): List<SelectedImage> = withContext(Dispatchers.IO) {
        uris.distinct().map(::importImage)
    }

    suspend fun allDeviceImages(): List<SelectedImage> = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(id),
                    )
                    add(importImage(uri, cursor.getString(name) ?: "Image"))
                }
            }
        }.orEmpty()
    }

    /**
     * Keeps the provider grant when it supports persistent grants, then creates an
     * app-owned copy. All subsequent previews and PDF reads use this local URI.
     */
    private fun importImage(sourceUri: Uri, knownName: String? = null): SelectedImage {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                sourceUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val name = knownName ?: displayName(sourceUri)
        val directory = File(context.filesDir, SELECTED_IMAGES_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Impossible de sauvegarder les images sélectionnées." }
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(sourceUri))
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }
        val target = File(directory, UUID.randomUUID().toString() + extension?.let { ".$it" }.orEmpty())
        val temporary = File(directory, "${target.name}.tmp")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: throw FileNotFoundException("Image inaccessible : $name")
            check(temporary.renameTo(target)) { "Impossible de sauvegarder l’image : $name" }
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            throw error
        }
        return SelectedImage(Uri.fromFile(target), name)
    }

    suspend fun createProject(name: String, images: List<SelectedImage>): ProjectEntity = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "Sélectionnez au moins une image." }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "pdf")
        check(directory.exists() || directory.mkdirs()) { "Impossible de créer le dossier PDF." }
        val output = File(directory, "PDF_Kanto_$stamp.pdf")

        try {
            val document = PdfDocument()
            try {
                images.forEachIndexed { index, image -> addPage(document, image.uri, index + 1) }
                output.outputStream().buffered().use { outputStream: OutputStream ->
                    document.writeTo(outputStream)
                }
            } finally {
                document.close()
            }
            val project = ProjectEntity(name = name.ifBlank { "Projet $stamp" }, createdAt = System.currentTimeMillis(), pdfPath = output.path)
            val id = dao.insertProject(project)
            dao.insertImages(images.mapIndexed { index, image ->
                ProjectImageEntity(projectId = id, position = index, uri = image.uri.toString(), displayName = image.name)
            })
            project.copy(id = id)
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    private fun addPage(document: PdfDocument, uri: Uri, pageNumber: Int) {
        val bitmap = loadBitmap(uri)
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageNumber).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw FileNotFoundException("Image inaccessible")
        var sample = 1
        while (bounds.outWidth / sample > MAX_PAGE_SIDE || bounds.outHeight / sample > MAX_PAGE_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: throw IllegalArgumentException("Format d’image non pris en charge")
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).rotationDegrees) { 90 -> 90f; 180 -> 180f; 270 -> 270f; else -> 0f }
        } ?: 0f
        if (rotation == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun displayName(uri: Uri): String = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment ?: "Image"

    private companion object {
        const val MAX_PAGE_SIDE = 2480
        const val SELECTED_IMAGES_DIRECTORY = "selected_images"
    }
}
