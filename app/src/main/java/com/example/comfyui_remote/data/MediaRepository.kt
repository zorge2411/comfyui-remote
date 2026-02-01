package com.example.comfyui_remote.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: GeneratedMediaDao) {
    val allMedia: Flow<List<GeneratedMediaEntity>> = mediaDao.getAll()

    suspend fun insert(media: GeneratedMediaEntity): Long {
        android.util.Log.d("MEDIA_REPO", "Inserting single media item: ${media.fileName}")
        val result = mediaDao.insert(media)
        android.util.Log.d("MEDIA_REPO", "Insert result: $result")
        return result
    }

    suspend fun insert(mediaList: List<GeneratedMediaEntity>): List<Long> {
        android.util.Log.d("MEDIA_REPO", "Inserting ${mediaList.size} media items")
        val startTime = System.currentTimeMillis()
        val result = mediaDao.insert(mediaList)
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.d("MEDIA_REPO", "Insert complete in ${duration}ms, results: $result")
        return result
    }

    suspend fun delete(media: GeneratedMediaEntity) {
        android.util.Log.d("MEDIA_REPO", "Deleting media item: ${media.fileName}")
        mediaDao.delete(media)
    }

    suspend fun delete(mediaList: List<GeneratedMediaEntity>) {
        android.util.Log.d("MEDIA_REPO", "Deleting ${mediaList.size} media items")
        mediaDao.delete(mediaList)
    }

    suspend fun getAllPromptIds(): List<String> {
        android.util.Log.d("MEDIA_REPO", "Fetching all prompt IDs")
        val result = mediaDao.getAllPromptIds()
        android.util.Log.d("MEDIA_REPO", "Found ${result.size} prompt IDs")
        return result
    }

    val allMediaListings: Flow<List<GeneratedMediaListing>> = mediaDao.getAllListings()

    suspend fun getById(id: Long): GeneratedMediaEntity? = mediaDao.getById(id)

    suspend fun getLatestByFilename(filename: String): GeneratedMediaEntity? = mediaDao.getLatestByFilename(filename)
}
