package com.example.comfyui_remote

import android.app.Application
import com.example.comfyui_remote.data.AppDatabase
import com.example.comfyui_remote.data.ConnectionRepository
import com.example.comfyui_remote.data.MediaRepository
import com.example.comfyui_remote.data.UserPreferencesRepository
import com.example.comfyui_remote.data.WorkflowRepository

class ComfyApplication : Application(), coil.ImageLoaderFactory {
    // Database instance
    val database by lazy { AppDatabase.getDatabase(this) }
    
    // Repositories
    val workflowRepository by lazy { WorkflowRepository(database.workflowDao()) }
    val mediaRepository by lazy { MediaRepository(database.generatedMediaDao()) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }
    
    // Global connection state
    val connectionRepository by lazy { ConnectionRepository() }

    // Shared OkHttpClient for API and Image Loading
    val okHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                // Only log non-view requests to avoid log spam from images
                if (BuildConfig.DEBUG && !request.url.toString().contains("/view?")) {
                    android.util.Log.d("API_DEBUG", "Sending request: ${request.method} ${request.url}")
                }
                val response = chain.proceed(request)
                if (BuildConfig.DEBUG && !request.url.toString().contains("/view?")) {
                    android.util.Log.d("API_DEBUG", "Received response: ${response.code} for ${request.url}")
                }
                response
            }
            .build()
    }

    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
            .okHttpClient(okHttpClient) // Use shared client
            .crossfade(true)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(coil.intercept.Interceptor { chain ->
                    try {
                        chain.proceed(chain.request)
                    } catch (e: java.io.InterruptedIOException) {
                        val ce = java.util.concurrent.CancellationException("Blocked I/O Interrupted")
                        ce.initCause(e)
                        throw ce
                    }
                })
            }
            .respectCacheHeaders(false) // Generated images are immutable by filename
            .build()
    }
}
