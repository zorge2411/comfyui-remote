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
     * Patches the workflow JSON with uploaded images and injects input values: the prompt as it
     * will be sent. Pure, so the pre-flight check (Phase 91) can run on it before queueing.
     */
    fun prepare(
        workflowJson: String,
        uploadedFilenames: Map<String, String>,
        inputs: List<InputField>
    ): String {
        val patchedJson = WorkflowPatchingService.patchWorkflow(workflowJson, uploadedFilenames)
        return workflowExecutor.injectValues(patchedJson, inputs)
    }

    /** Queues a prompt produced by [prepare]. */
    suspend fun queue(api: ComfyApiService, clientId: String, preparedJson: String): PromptResponse =
        withContext(Dispatchers.Default) {
            api.queuePrompt(
                PromptRequest(
                    prompt = JsonParser.parseString(preparedJson).asJsonObject,
                    client_id = clientId
                )
            )
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
        val injectedJson = prepare(workflowJson, uploadedFilenames, inputs)
        Pair(injectedJson, queue(api, clientId, injectedJson))
    }
}
