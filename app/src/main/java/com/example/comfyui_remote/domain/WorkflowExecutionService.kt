package com.example.comfyui_remote.domain

import com.example.comfyui_remote.network.ComfyApiService
import com.example.comfyui_remote.data.ImageRepository
import android.content.ContentResolver
import com.example.comfyui_remote.network.PromptResponse

class WorkflowExecutionService(
    private val imageRepository: ImageRepository,
    private val workflowExecutor: WorkflowExecutor
) {
    suspend fun uploadImages(
        api: ComfyApiService,
        inputs: Map<String, String?>,
        resolver: ContentResolver
    ): Map<String, String> {
        return emptyMap()
    }

    suspend fun prepareAndQueue(
        api: ComfyApiService,
        clientId: String,
        workflowJson: String,
        uploadedFilenames: Map<String, String>,
        inputs: List<InputField>
    ): Pair<String, PromptResponse> {
        return Pair(workflowJson, PromptResponse("stub_prompt_id"))
    }
}
