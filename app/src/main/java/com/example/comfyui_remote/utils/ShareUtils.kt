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

    /**
     * Downloads several items and opens one share sheet for them (Phase 104). [items] are (url, file name).
     * Returns how many could be downloaded; nothing is shared when none could.
     */
    suspend fun downloadAndShareMultiple(context: Context, items: List<Pair<String, String>>): Int =
        withContext(Dispatchers.IO) {
            val files = items.mapNotNull { (url, name) ->
                try {
                    downloadFile(context, url, name)
                } catch (e: Exception) {
                    null
                }
            }
            if (files.isEmpty()) return@withContext 0
            if (files.size == 1) {
                shareFile(context, files[0])
                return@withContext 1
            }
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
            val types = files.map { mimeTypeOf(it).substringBefore('/') }.distinct()
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (types.size == 1) "${types[0]}/*" else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${files.size} items")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            files.size
        }

    private fun mimeTypeOf(file: File): String = when (file.extension.lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "gif" -> "image/gif"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/*"
    }

    private fun downloadFile(context: Context, url: String, filename: String): File {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

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
        
        val mimeType = mimeTypeOf(file)

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
