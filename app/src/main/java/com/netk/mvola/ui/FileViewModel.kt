package com.netk.mvola.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netk.mvola.data.FileCategory
import com.netk.mvola.data.FileRepository
import com.netk.mvola.data.LocalFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FileBrowserState(
    val category: FileCategory = FileCategory.PDF,
    val files: List<LocalFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileRepository(application)
    private val _state = MutableStateFlow(FileBrowserState())
    val state = _state.asStateFlow()
    private var scanJob: Job? = null

    fun selectCategory(category: FileCategory) {
        if (_state.value.category != category) _state.value = FileBrowserState(category = category)
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.scan(_state.value.category).fold(
                onSuccess = { _state.value = _state.value.copy(files = it, isLoading = false) },
                onFailure = {
                    _state.value = _state.value.copy(
                        files = emptyList(), isLoading = false,
                        error = it.localizedMessage ?: "Impossible de lire les fichiers locaux.",
                    )
                },
            )
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
