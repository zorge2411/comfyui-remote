package com.example.comfyui_remote.domain

import android.content.ContentResolver
import android.net.Uri
import com.example.comfyui_remote.data.ImageRepository
import com.example.comfyui_remote.network.ComfyApiService
import com.example.comfyui_remote.network.PromptRequest
import com.example.comfyui_remote.network.PromptResponse
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkflowExecutionService(
    private val imageRepository: ImageRepository,
    private val workflowExecutor: WorkflowExecutor
) {
    /**
     * Uploads images found in the inputs map.
     * Returns a map of NodeID -> UploadedFilename
     */
    suspend fun uploadImages(
        api: ComfyApiService,
        inputs: Map<String, String?>,
        contentResolver: ContentResolver
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val uploadedFilenames = mutableMapOf<String, String>()
        
        inputs.forEach { (nodeId, uriString) ->
            if (uriString != null) {
                try {
                    val uri = Uri.parse(uriString)
                    val response = imageRepository.uploadImage(api, uri, contentResolver)
                    uploadedFilenames[nodeId] = response.name
                } catch (e: Exception) {
                    throw Exception("Image upload failed: ${e.message}")
                }
            }
        }
        uploadedFilenames
    }

    /**
     * Patches the workflow JSON with uploaded images, injects input values, and queues the prompt.
     * Returns a Pair of (InjectedJSON, PromptResponse)
     */
    suspend fun prepareAndQueue(
        api: ComfyApiService,
        clientId: String,
        workflowJson: String,
        uploadedFilenames: Map<String, String>,
        inputs: List<InputField>
    ): Pair<String, PromptResponse> = withContext(Dispatchers.Default) {
        // 1. Patch Workflow with uploaded images
        val patchedJson = WorkflowPatchingService.patchWorkflow(workflowJson, uploadedFilenames)

        // 2. Inject Values
        val injectedJson = workflowExecutor.injectValues(patchedJson, inputs)
        val promptJsonObject = JsonParser.parseString(injectedJson).asJsonObject

        // 3. Queue Prompt
        val response = api.queuePrompt(
            PromptRequest(
                prompt = promptJsonObject,
                client_id = clientId
            )
        )

        Pair(injectedJson, response)
    }
}
