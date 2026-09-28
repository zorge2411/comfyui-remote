package com.example.comfyui_remote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedMediaDao {
    @Query("SELECT * FROM generated_media WHERE hidden = 0 ORDER BY timestamp DESC")
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

    /** Clears the gallery but keeps removed items, so a re-sync skips them (Phase 104). */
    @Query("DELETE FROM generated_media WHERE hidden = 0")
    suspend fun deleteVisible()

    @Query("UPDATE generated_media SET hidden = 1 WHERE id IN (:ids)")
    suspend fun hide(ids: List<Long>)

    @Query("UPDATE generated_media SET hidden = 0 WHERE hidden = 1")
    suspend fun unhideAll()

    @Query("SELECT COUNT(*) FROM generated_media WHERE hidden = 1")
    fun hiddenCount(): Flow<Int>

    // Includes removed items, so syncs skip their executions
    @Query("SELECT promptId FROM generated_media WHERE promptId IS NOT NULL")
    suspend fun getAllPromptIds(): List<String>

    @Query("SELECT id, workflowName, fileName, subfolder, serverHost, serverPort, timestamp, mediaType, serverType FROM generated_media WHERE hidden = 0 ORDER BY timestamp DESC")
    fun getAllListings(): Flow<List<GeneratedMediaListing>>

    @Query("SELECT * FROM generated_media WHERE id = :id")
    suspend fun getById(id: Long): GeneratedMediaEntity?

    @Query("SELECT * FROM generated_media WHERE fileName = :filename ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByFilename(filename: String): GeneratedMediaEntity?
}
