package com.example.comfyui_remote

import android.app.Application
import com.example.comfyui_remote.data.AppDatabase
import com.example.comfyui_remote.data.ConnectionRepository
import com.example.comfyui_remote.data.MediaRepository
import com.example.comfyui_remote.data.UserPreferencesRepository
import com.example.comfyui_remote.data.WorkflowRepository

class ComfyApplication : Application() {
    // Database instance
    val database by lazy { AppDatabase.getDatabase(this) }
    
    // Repositories
    val workflowRepository by lazy { WorkflowRepository(database.workflowDao()) }
    val mediaRepository by lazy { MediaRepository(database.generatedMediaDao()) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }
    
    // Global connection state
    val connectionRepository by lazy { ConnectionRepository() }
}
