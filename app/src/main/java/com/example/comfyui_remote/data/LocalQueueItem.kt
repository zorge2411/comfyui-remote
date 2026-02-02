package com.example.comfyui_remote.data

import androidx.room.*

enum class QueueStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    FAILED
}

@Entity(tableName = "local_queue")
data class LocalQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowId: Long, // 0 if local/unsaved
    val workflowName: String,
    val workflowJson: String, // Snapshot of workflow architecture
    val inputValuesJson: String, // Snapshot of user inputs
    val batchCount: Int = 1,
    val status: QueueStatus = QueueStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
