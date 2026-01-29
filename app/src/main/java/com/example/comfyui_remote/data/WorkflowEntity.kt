package com.example.comfyui_remote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val jsonContent: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastImageName: String? = null,
    val baseModelName: String? = null, // legacy field
    val baseModels: String? = null, // Comma-separated list for searching
    val source: String? = null, // "LOCAL_IMPORT", "SERVER_USERDATA", etc.
    val formatVersion: Int = 1,
    val missingNodes: String? = null // Comma-separated list of missing node types
)
