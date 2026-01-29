package com.example.comfyui_remote.domain

enum class WorkflowSource {
    LOCAL_IMPORT,
    SERVER_USERDATA,
    SERVER_HISTORY,
    UNKNOWN
}

data class NormalizedWorkflow(
    val name: String,
    val jsonContent: String,
    val source: WorkflowSource,
    val baseModels: List<String>,
    val formatVersion: Int = 1,
    val missingNodes: List<String> = emptyList()
)
