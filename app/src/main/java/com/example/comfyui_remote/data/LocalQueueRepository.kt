package com.example.comfyui_remote.data

class LocalQueueRepository(private val dao: LocalQueueDao) {
    suspend fun addToQueue(
        workflowId: Long,
        workflowName: String,
        workflowJson: String,
        inputValuesJson: String,
        batchCount: Int
    ) {
        dao.insert(
            LocalQueueItem(
                workflowId = workflowId,
                workflowName = workflowName,
                workflowJson = workflowJson,
                inputValuesJson = inputValuesJson,
                batchCount = batchCount
            )
        )
    }
}
