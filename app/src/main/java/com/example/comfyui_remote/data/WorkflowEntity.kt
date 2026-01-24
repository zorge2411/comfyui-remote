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
    val baseModelName: String? = null // e.g. "v1-5-pruned-emaonly.ckpt"
)
