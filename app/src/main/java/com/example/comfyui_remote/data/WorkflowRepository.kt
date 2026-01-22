package com.example.comfyui_remote.data

import kotlinx.coroutines.flow.Flow

class WorkflowRepository(private val workflowDao: WorkflowDao) {

    val allWorkflows: Flow<List<WorkflowEntity>> = workflowDao.getAll()

    suspend fun addWorkflow(name: String, jsonContent: String) {
        val workflow = WorkflowEntity(name = name, jsonContent = jsonContent)
        workflowDao.insert(workflow)
    }

    suspend fun insert(workflow: WorkflowEntity) {
        workflowDao.insert(workflow)
    }

    suspend fun updateWorkflow(workflow: WorkflowEntity) {
        workflowDao.update(workflow)
    }

    suspend fun deleteWorkflow(workflow: WorkflowEntity) {
        workflowDao.delete(workflow)
    }
}
