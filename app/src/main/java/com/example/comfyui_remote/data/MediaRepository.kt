package com.example.comfyui_remote.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: GeneratedMediaDao) {
    val allMedia: Flow<List<GeneratedMediaEntity>> = mediaDao.getAll()

    suspend fun insert(media: GeneratedMediaEntity): Long {
        return mediaDao.insert(media)
    }

    suspend fun insert(mediaList: List<GeneratedMediaEntity>): List<Long> {
        return mediaDao.insert(mediaList)
    }

    suspend fun delete(media: GeneratedMediaEntity) {
        mediaDao.delete(media)
    }

    suspend fun delete(mediaList: List<GeneratedMediaEntity>) {
        mediaDao.delete(mediaList)
    }

    suspend fun getAllPromptIds(): List<String> = mediaDao.getAllPromptIds()

    val allMediaListings: Flow<List<GeneratedMediaListing>> = mediaDao.getAllListings()

    suspend fun getById(id: Long): GeneratedMediaEntity? = mediaDao.getById(id)

    suspend fun getLatestByFilename(filename: String): GeneratedMediaEntity? = mediaDao.getLatestByFilename(filename)
}
