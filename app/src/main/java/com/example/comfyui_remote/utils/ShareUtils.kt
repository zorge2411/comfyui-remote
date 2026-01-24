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
                val file = downloadFile(context, url)
                shareFile(context, file)
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error (maybe callback or return result)
            }
        }
    }

    private fun downloadFile(context: Context, url: String): File {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        val inputStream = response.body?.byteStream() ?: throw Exception("Body is null")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        
        val cachePath = File(context.cacheDir, "shared")
        if (!cachePath.exists()) cachePath.mkdirs()
        
        val file = File(cachePath, "shared_image.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        return file
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share Image")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
