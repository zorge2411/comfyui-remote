package com.example.comfyui_remote.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ComfyApiService {

    @GET("system_stats")
    suspend fun getSystemStats(): JsonObject

    @GET("object_info")
    suspend fun getObjectInfo(): JsonObject

    @POST("prompt")
    suspend fun queuePrompt(@Body prompt: JsonObject): JsonObject

    @POST("queue")
    suspend fun clearQueue(@Body body: JsonObject): JsonObject

    @POST("interrupt")
    suspend fun interrupt(): JsonObject
}
