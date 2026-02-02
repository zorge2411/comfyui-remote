package com.example.comfyui_remote.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromQueueStatus(status: QueueStatus): String {
        return status.name
    }

    @TypeConverter
    fun toQueueStatus(status: String): QueueStatus {
        return QueueStatus.valueOf(status)
    }
}
