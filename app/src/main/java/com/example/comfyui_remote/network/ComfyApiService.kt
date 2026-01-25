package com.example.comfyui_remote.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody

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

    @GET("api/userdata")
    suspend fun getUserData(
        @Query("dir") dir: String = "workflows",
        @Query("recurse") recurse: Boolean = true,
        @Query("split") split: Boolean = false,
        @Query("full_info") fullInfo: Boolean = true
    ): List<ServerWorkflowFile>

    @GET("{path}")
    suspend fun getFileContent(@Path("path", encoded = true) path: String): com.google.gson.JsonElement

    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part, 
        @Part("overwrite") overwrite: RequestBody? = null
    ): ImageUploadResponse
}

data class PromptRequest(val prompt: JsonObject, val client_id: String? = null)
data class PromptResponse(val prompt_id: String)
data class ImageUploadResponse(val name: String, val subfolder: String, val type: String)
