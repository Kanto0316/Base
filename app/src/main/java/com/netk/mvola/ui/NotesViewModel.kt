package com.netk.mvola.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.netk.mvola.data.NoteRepository
import com.netk.mvola.data.local.ImageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: NoteRepository) : ViewModel() {
    val notes = repository.notes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun note(id: Long) = repository.note(id)

    fun save(id: Long?, title: String, content: String, images: List<Uri>, finished: (Result<Long>) -> Unit) {
        viewModelScope.launch { finished(repository.save(id, title, content, images)) }
    }

    fun delete(id: Long, finished: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { finished(repository.delete(id)) }
    }

    fun deleteImage(image: ImageEntity, finished: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { finished(repository.deleteImage(image)) }
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(repository) as T
    }
}
