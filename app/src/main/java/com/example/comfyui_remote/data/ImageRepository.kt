package com.example.comfyui_remote.data

import android.content.ContentResolver
import android.net.Uri
import com.example.comfyui_remote.network.ComfyApiService
import com.example.comfyui_remote.network.ImageUploadResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class ImageRepository {
    suspend fun uploadImage(
        api: ComfyApiService, 
        uri: Uri, 
        contentResolver: ContentResolver,
        fileName: String? = null
    ): ImageUploadResponse = withContext(Dispatchers.IO) {
        
        val name = fileName ?: "upload_${System.currentTimeMillis()}.png"
        
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val bytes = inputStream?.use { it.readBytes() } ?: throw Exception("Could not read file from URI")
        
        val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", name, requestFile)
        
        return@withContext api.uploadImage(body)
    }
}
