package com.example.comfyui_remote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedMediaDao {
    @Query("SELECT * FROM generated_media ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GeneratedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(media: GeneratedMediaEntity): Long

    @Delete
    suspend fun delete(media: GeneratedMediaEntity)

    @Delete
    suspend fun delete(media: List<GeneratedMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(media: List<GeneratedMediaEntity>): List<Long>

    @Query("DELETE FROM generated_media")
    suspend fun deleteAll()

    @Query("SELECT promptId FROM generated_media WHERE promptId IS NOT NULL")
    suspend fun getAllPromptIds(): List<String>

    @Query("SELECT id, workflowName, fileName, subfolder, serverHost, serverPort, timestamp, mediaType, serverType FROM generated_media ORDER BY timestamp DESC")
    fun getAllListings(): Flow<List<GeneratedMediaListing>>

    @Query("SELECT * FROM generated_media WHERE id = :id")
    suspend fun getById(id: Long): GeneratedMediaEntity?

    @Query("SELECT * FROM generated_media WHERE fileName = :filename ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByFilename(filename: String): GeneratedMediaEntity?
}
