package com.example.comfyui_remote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.savedGalleryListsDataStore: DataStore<Preferences> by preferencesDataStore(name = "saved_gallery_lists")

/**
 * Repository for managing saved gallery lists using DataStore for persistence.
 * Lists are stored as JSON and can be either snapshots or live filters.
 */
class SavedGalleryListRepository(private val context: Context) {

    private val SAVED_LISTS_KEY = stringPreferencesKey("saved_gallery_lists")
    private val gson = Gson()

    /**
     * Get all saved gallery lists as a Flow.
     */
    val allLists: Flow<List<SavedGalleryList>> = context.savedGalleryListsDataStore.data
        .map { preferences ->
            val json = preferences[SAVED_LISTS_KEY] ?: "[]"
            val type = object : TypeToken<List<SavedGalleryList>>() {}.type
            try {
                gson.fromJson<List<SavedGalleryList>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("SavedGalleryListRepo", "Error parsing saved lists", e)
                emptyList()
            }
        }

    /**
     * Get a specific list by ID.
     */
    suspend fun getListById(id: String): SavedGalleryList? {
        val lists = allLists.first()
        return lists.find { it.id == id }
    }

    /**
     * Save a new gallery list or update an existing one.
     */
    suspend fun saveList(list: SavedGalleryList) {
        context.savedGalleryListsDataStore.edit { preferences ->
            val json = preferences[SAVED_LISTS_KEY] ?: "[]"
            val type = object : TypeToken<List<SavedGalleryList>>() {}.type
            val currentLists = try {
                gson.fromJson<List<SavedGalleryList>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                android.util.Log.e("SavedGalleryListRepo", "Error parsing current lists", e)
                mutableListOf()
            }

            // Remove if already exists (same ID)
            currentLists.removeAll { it.id == list.id }

            // Add at the beginning (most recent first)
            currentLists.add(0, list)

            preferences[SAVED_LISTS_KEY] = gson.toJson(currentLists)
        }
    }

    /**
     * Delete a gallery list by ID.
     */
    suspend fun deleteList(id: String) {
        context.savedGalleryListsDataStore.edit { preferences ->
            val json = preferences[SAVED_LISTS_KEY] ?: "[]"
            val type = object : TypeToken<List<SavedGalleryList>>() {}.type
            val currentLists = try {
                gson.fromJson<List<SavedGalleryList>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                android.util.Log.e("SavedGalleryListRepo", "Error parsing current lists", e)
                mutableListOf()
            }

            currentLists.removeAll { it.id == id }
            preferences[SAVED_LISTS_KEY] = gson.toJson(currentLists)
        }
    }

    /**
     * Delete all saved gallery lists.
     */
    suspend fun deleteAllLists() {
        context.savedGalleryListsDataStore.edit { preferences ->
            preferences[SAVED_LISTS_KEY] = "[]"
        }
    }

    /**
     * Rename a saved gallery list.
     */
    suspend fun renameList(id: String, newName: String) {
        context.savedGalleryListsDataStore.edit { preferences ->
            val json = preferences[SAVED_LISTS_KEY] ?: "[]"
            val type = object : TypeToken<List<SavedGalleryList>>() {}.type
            val currentLists = try {
                gson.fromJson<List<SavedGalleryList>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                android.util.Log.e("SavedGalleryListRepo", "Error parsing current lists", e)
                mutableListOf()
            }

            val index = currentLists.indexOfFirst { it.id == id }
            if (index != -1) {
                currentLists[index] = currentLists[index].copy(name = newName)
                preferences[SAVED_LISTS_KEY] = gson.toJson(currentLists)
            }
        }
    }

    /**
     * Duplicate a saved gallery list.
     */
    suspend fun duplicateList(id: String): SavedGalleryList? {
        val originalList = getListById(id) ?: return null
        val duplicatedList = originalList.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${originalList.name} (Copy)",
            savedAt = System.currentTimeMillis()
        )
        saveList(duplicatedList)
        return duplicatedList
    }

    /**
     * Get the count of saved lists.
     */
    suspend fun getListCount(): Int {
        return allLists.first().size
    }
}
