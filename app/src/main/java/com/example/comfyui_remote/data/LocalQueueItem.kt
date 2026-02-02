package com.example.comfyui_remote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_queue")
data class LocalQueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workflowId: Long,
    val workflowName: String,
    val workflowJson: String,
    val inputValuesJson: String,
    val batchCount: Int = 1,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)
