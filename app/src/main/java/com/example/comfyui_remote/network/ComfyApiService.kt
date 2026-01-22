package com.example.comfyui_remote.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ComfyApiService {
    @GET("system_stats")
    suspend fun getSystemStats(): JsonObject
    
    @POST("prompt")
    suspend fun queuePrompt(@Body prompt: PromptRequest): PromptResponse
}

data class PromptRequest(val prompt: JsonObject, val client_id: String? = null)
data class PromptResponse(val prompt_id: String)
