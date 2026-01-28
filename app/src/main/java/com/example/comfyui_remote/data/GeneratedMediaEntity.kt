package com.example.comfyui_remote.data

import androidx.room.*

@Entity(
    tableName = "generated_media",
    indices = [
        Index(value = ["promptId", "fileName"], unique = true),
        Index(value = ["timestamp"], name = "index_generated_media_timestamp")
    ]
)
data class GeneratedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowName: String,
    val fileName: String,
    val subfolder: String?,
    val serverHost: String,
    val serverPort: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaType: String = "IMAGE", // "IMAGE" or "VIDEO"
    val promptJson: String? = null, // The JSON used to generate this item
    val promptId: String? = null, // ComfyUI Execution ID
    val serverType: String = "output" // "output" (default) or "input"
)
