package com.example.comfyui_remote.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

object StorageUtils {

    suspend fun saveMediaToFolder(
        context: Context,
        url: String,
        folderUri: String,
        fileName: String,
        mediaType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(folderUri)
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
            
            if (pickedDir == null || !pickedDir.canWrite()) {
                return@withContext false
            }

            // Mime type detection
            val mimeType = if (mediaType == "VIDEO") {
                val extension = fileName.substringAfterLast('.', "").lowercase()
                when (extension) {
                    "mp4" -> "video/mp4"
                    "gif" -> "image/gif"
                    "webm" -> "video/webm"
                    else -> "video/*"
                }
            } else {
                "image/png"
            }

            // Create file in selected directory
            // Check if file already exists to avoid duplicates with (1) suffixes handled by SAF
            val newFile = pickedDir.createFile(mimeType, fileName)
                ?: return@withContext false

            // Download and write
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return@withContext false
            
            val inputStream: InputStream = response.body?.byteStream() ?: return@withContext false
            
            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
