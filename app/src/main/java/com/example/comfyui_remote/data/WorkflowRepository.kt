package com.example.comfyui_remote.data

import kotlinx.coroutines.flow.Flow

class WorkflowRepository(private val workflowDao: WorkflowDao) {

    val allWorkflows: Flow<List<WorkflowEntity>> = workflowDao.getAll()

    suspend fun addWorkflow(name: String, jsonContent: String): Long {
        val workflow = WorkflowEntity(name = name, jsonContent = jsonContent)
        return workflowDao.insert(workflow)
    }

    suspend fun insert(workflow: WorkflowEntity): Long {
        return workflowDao.insert(workflow)
    }

    suspend fun updateWorkflow(workflow: WorkflowEntity) {
        workflowDao.update(workflow)
    }

    suspend fun deleteWorkflow(workflow: WorkflowEntity) {
        workflowDao.delete(workflow)
    }
}
