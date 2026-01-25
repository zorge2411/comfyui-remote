package com.example.comfyui_remote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {
    
    private val HOST_KEY = stringPreferencesKey("host_ip")
    private val PORT_KEY = intPreferencesKey("host_port")
    private val SAVE_FOLDER_URI_KEY = stringPreferencesKey("save_folder_uri")
    private val THEME_MODE_KEY = intPreferencesKey("theme_mode")

    val savedHost: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[HOST_KEY] ?: ""
        }
        
    val savedPort: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PORT_KEY] ?: 8188 
        }

    val saveFolderUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SAVE_FOLDER_URI_KEY]
        }

    val themeMode: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE_KEY] ?: 0
        }

    suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun saveSaveFolderUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[SAVE_FOLDER_URI_KEY] = uri
        }
    }

    suspend fun saveConnectionDetails(host: String, port: Int) {
        context.dataStore.edit { preferences ->
            preferences[HOST_KEY] = host
            preferences[PORT_KEY] = port
        }
    }
}
