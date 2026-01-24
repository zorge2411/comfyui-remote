package com.example.comfyui_remote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedMediaDao {
    @Query("SELECT * FROM generated_media ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GeneratedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(media: GeneratedMediaEntity)

    @Delete
    suspend fun delete(media: GeneratedMediaEntity)

    @Delete
    suspend fun delete(media: List<GeneratedMediaEntity>)

    @Query("DELETE FROM generated_media")
    suspend fun deleteAll()

    @Query("SELECT promptId FROM generated_media WHERE promptId IS NOT NULL")
    suspend fun getAllPromptIds(): List<String>
}
