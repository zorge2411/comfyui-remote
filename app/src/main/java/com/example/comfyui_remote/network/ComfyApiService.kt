package com.example.comfyui_remote.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ComfyApiService {
    @GET("system_stats")
    suspend fun getSystemStats(): JsonObject
    
    @POST("prompt")
    suspend fun queuePrompt(@Body prompt: PromptRequest): PromptResponse
    
    @GET("history")
    suspend fun getHistory(): JsonObject

    @GET("history/{prompt_id}")
    suspend fun getHistory(@Path("prompt_id") promptId: String): JsonObject
    @GET("models/{folder}")
    suspend fun getModels(@Path("folder") folder: String): List<String>

    @GET("object_info")
    suspend fun getObjectInfo(): JsonObject
}

data class PromptRequest(val prompt: JsonObject, val client_id: String? = null)
data class PromptResponse(val prompt_id: String)
