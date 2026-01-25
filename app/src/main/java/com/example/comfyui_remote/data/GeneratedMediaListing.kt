package com.example.comfyui_remote.data

import androidx.room.ColumnInfo

/**
 * A lightweight projection of GeneratedMediaEntity for the Gallery UI.
 * Excludes heavy fields like 'promptJson'.
 */
data class GeneratedMediaListing(
    val id: Long,
    val workflowName: String,
    val fileName: String,
    val subfolder: String?,
    val serverHost: String,
    val serverPort: Int,
    val timestamp: Long,
    val mediaType: String,
    val serverType: String
)
