package com.example.comfyui_remote.network

import com.google.gson.annotations.SerializedName

data class ServerWorkflow(
    val name: String,
    val fullpath: String,
    val type: String,
    val size: Long? = null,
    val mtime: Double? = null
)

data class ServerWorkflowFile(
    val path: String? = null,
    val size: Number? = null,
    val modified: Double? = null,
    val created: Double? = null,
    val type: String? = "workflow"
) {
    val name: String? get() = path
    // Since we query dir="workflows", the path returned is relative to that directory.
    // We prepend "workflows/" to make it relative to "userdata/" for getFileContent.
    val fullpath: String? get() = if (path != null) "workflows/$path" else null
}

