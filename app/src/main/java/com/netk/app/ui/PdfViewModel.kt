package com.netk.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netk.app.data.PdfRepository
import com.netk.app.data.ProjectWithImages
import com.netk.app.data.SelectedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PdfUiState(
    val selectedImages: List<SelectedImage> = emptyList(),
    val isWorking: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)
    private val _state = MutableStateFlow(PdfUiState())
    val state = _state.asStateFlow()
    val projects = repository.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addImages(uris: List<Uri>) = launchWork {
        val incoming = repository.resolveImages(uris)
        _state.value = _state.value.copy(
            selectedImages = (_state.value.selectedImages + incoming).distinctBy { it.uri },
        )
    }

    fun selectAllImages() = launchWork {
        _state.value = _state.value.copy(selectedImages = repository.allDeviceImages())
    }

    fun removeImage(image: SelectedImage) {
        _state.value = _state.value.copy(selectedImages = _state.value.selectedImages - image)
    }

    fun loadProject(project: ProjectWithImages) {
        _state.value = _state.value.copy(selectedImages = project.orderedImages.map {
            SelectedImage(Uri.parse(it.uri), it.displayName)
        })
    }

    fun convert(projectName: String, completed: () -> Unit) = launchWork {
        repository.createProject(projectName, _state.value.selectedImages)
        _state.value = _state.value.copy(selectedImages = emptyList(), message = "PDF créé et projet sauvegardé.")
        completed()
    }

    fun clearNotice() { _state.value = _state.value.copy(message = null, error = null) }

    private fun launchWork(block: suspend () -> Unit) {
        if (_state.value.isWorking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, error = null, message = null)
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.localizedMessage ?: "Une erreur est survenue.")
            } finally {
                _state.value = _state.value.copy(isWorking = false)
            }
        }
    }
}
