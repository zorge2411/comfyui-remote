package com.example.comfyui_remote.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: GeneratedMediaDao) {
    val allMedia: Flow<List<GeneratedMediaEntity>> = mediaDao.getAll()

    suspend fun insert(media: GeneratedMediaEntity) {
        mediaDao.insert(media)
    }

    suspend fun delete(media: GeneratedMediaEntity) {
        mediaDao.delete(media)
    }

    suspend fun delete(mediaList: List<GeneratedMediaEntity>) {
        mediaDao.delete(mediaList)
    }

    suspend fun getAllPromptIds(): List<String> = mediaDao.getAllPromptIds()
}
