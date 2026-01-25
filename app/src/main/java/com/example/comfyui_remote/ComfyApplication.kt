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

    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
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
