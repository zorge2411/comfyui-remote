package com.example.comfyui_remote.data

import kotlinx.coroutines.flow.Flow

class LocalQueueRepository(
    private val localQueueDao: LocalQueueDao
) {
    val allQueueItems: Flow<List<LocalQueueItem>> = localQueueDao.getAll()

    suspend fun addToQueue(
        workflowId: Long,
        workflowName: String,
        workflowJson: String,
        inputValuesJson: String,
        batchCount: Int
    ): Long {
        val item = LocalQueueItem(
            workflowId = workflowId,
            workflowName = workflowName,
            workflowJson = workflowJson,
            inputValuesJson = inputValuesJson,
            batchCount = batchCount,
            status = QueueStatus.PENDING
        )
        return localQueueDao.insert(item)
    }

    suspend fun getPendingItems(): List<LocalQueueItem> {
        return localQueueDao.getPendingItems()
    }

    suspend fun updateStatus(id: Long, status: QueueStatus) {
        localQueueDao.updateStatus(id, status)
    }

    suspend fun delete(item: LocalQueueItem) {
        localQueueDao.delete(item)
    }

    suspend fun clearCompleted() {
        localQueueDao.clearCompleted()
    }
}
