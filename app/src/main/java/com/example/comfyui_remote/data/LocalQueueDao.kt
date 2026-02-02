package com.example.comfyui_remote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalQueueDao {
    @Query("SELECT * FROM local_queue ORDER BY createdAt ASC")
    fun getAll(): Flow<List<LocalQueueItem>>

    @Query("SELECT * FROM local_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingItems(): List<LocalQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LocalQueueItem): Long

    @Update
    suspend fun update(item: LocalQueueItem)

    @Delete
    suspend fun delete(item: LocalQueueItem)

    @Query("UPDATE local_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: QueueStatus)
    
    @Query("DELETE FROM local_queue WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
