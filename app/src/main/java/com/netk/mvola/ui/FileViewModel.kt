package com.netk.mvola.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netk.mvola.data.FileCategory
import com.netk.mvola.data.FileRepository
import com.netk.mvola.data.LocalFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FileBrowserState(
    val category: FileCategory = FileCategory.PDF,
    val filesByCategory: Map<FileCategory, List<LocalFile>> = FileCategory.entries.associateWith { emptyList() },
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val selectedTreeUri: Uri? = null,
    val error: String? = null,
) {
    val files: List<LocalFile> get() = filesByCategory[category].orEmpty()
    val totalFileCount: Int get() = filesByCategory.values.sumOf { it.size }
}

class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileRepository(application)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val _state = MutableStateFlow(
        FileBrowserState(selectedTreeUri = preferences.getString(TREE_URI, null)?.let(Uri::parse)),
    )
    val state = _state.asStateFlow()
    private var scanJob: Job? = null

    fun selectCategory(category: FileCategory) {
        _state.value = _state.value.copy(category = category)
    }

    fun updatePermission(granted: Boolean, scanNow: Boolean = true) {
        _state.value = _state.value.copy(permissionGranted = granted)
        if (scanNow) scan()
    }

    fun selectDocumentTree(uri: Uri) {
        preferences.edit().putString(TREE_URI, uri.toString()).apply()
        _state.value = _state.value.copy(selectedTreeUri = uri)
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = repository.scanAll(_state.value.selectedTreeUri, _state.value.permissionGranted)
                _state.value = _state.value.copy(
                    filesByCategory = result.filesByCategory,
                    isLoading = false,
                    error = result.mediaStoreErrors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    filesByCategory = FileCategory.entries.associateWith { emptyList() }, isLoading = false,
                    error = error.localizedMessage ?: "Impossible de lire les fichiers locaux.",
                )
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private companion object {
        const val PREFERENCES = "file_browser"
        const val TREE_URI = "selected_tree_uri"
    }
}
