package com.netk.mvola.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Transaction
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeNotes(): Flow<List<NoteWithImages>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteWithImages?>

    @Insert suspend fun insertNote(note: Note): Long
    @Update suspend fun updateNote(note: Note)
    @Insert suspend fun insertImages(images: List<ImageEntity>)
    @Query("DELETE FROM images WHERE id = :id") suspend fun deleteImage(id: Long)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)
}
