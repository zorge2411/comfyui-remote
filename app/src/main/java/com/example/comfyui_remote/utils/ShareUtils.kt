package com.example.comfyui_remote.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ShareUtils {

    suspend fun downloadAndShare(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            try {
                val filename = url.substringAfter("filename=").substringBefore("&")
                val file = downloadFile(context, url, filename)
                shareFile(context, file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun downloadFile(context: Context, url: String, filename: String): File {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        val inputStream = response.body?.byteStream() ?: throw Exception("Body is null")
        
        val cachePath = File(context.cacheDir, "shared")
        if (!cachePath.exists()) cachePath.mkdirs()
        
        val file = File(cachePath, filename)
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        outputStream.close()
        
        return file
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val extension = file.extension.lowercase()
        val mimeType = when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "gif" -> "image/gif"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> if (listOf("mp4", "webm", "mkv").contains(extension)) "video/*" else "image/*"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share Media")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
