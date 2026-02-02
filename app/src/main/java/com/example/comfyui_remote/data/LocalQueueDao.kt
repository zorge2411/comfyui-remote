package com.example.comfyui_remote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalQueueDao {
    @Query("SELECT * FROM local_queue ORDER BY createdAt DESC")
    fun getAll(): Flow<List<LocalQueueItem>>

    @Insert
    suspend fun insert(item: LocalQueueItem): Long
}
