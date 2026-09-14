package com.netk.mvola.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithImages(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val images: List<ImageEntity>,
)
