package com.example.comfyui_remote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ServerProfile(
    val host: String,
    val port: Int,
    val isSecure: Boolean,
    val lastUsed: Long = System.currentTimeMillis()
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {
    
    private val HOST_KEY = stringPreferencesKey("host_ip")
    private val PORT_KEY = intPreferencesKey("host_port")
    private val IS_SECURE_KEY = booleanPreferencesKey("is_secure")
    private val SAVE_FOLDER_URI_KEY = stringPreferencesKey("save_folder_uri")
    private val THEME_MODE_KEY = intPreferencesKey("theme_mode")
    private val SERVER_PROFILES_KEY = stringPreferencesKey("server_profiles")

    private val gson = Gson()

    val savedHost: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[HOST_KEY] ?: ""
        }
        
    val savedPort: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PORT_KEY] ?: 8188 
        }

    val isSecure: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_SECURE_KEY] ?: false
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

    suspend fun saveConnectionDetails(host: String, port: Int, isSecure: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HOST_KEY] = host
            preferences[PORT_KEY] = port
            preferences[IS_SECURE_KEY] = isSecure
        }
    }

    val serverProfiles: Flow<List<ServerProfile>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[SERVER_PROFILES_KEY] ?: "[]"
            val type = object : TypeToken<List<ServerProfile>>() {}.type
            try {
                gson.fromJson<List<ServerProfile>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveServerProfile(profile: ServerProfile) {
        context.dataStore.edit { preferences ->
            val json = preferences[SERVER_PROFILES_KEY] ?: "[]"
            val type = object : TypeToken<List<ServerProfile>>() {}.type
            val currentProfiles = try {
                gson.fromJson<List<ServerProfile>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Remove if already exists (same host and port)
            currentProfiles.removeAll { it.host == profile.host && it.port == profile.port }
            
            // Add at the beginning
            currentProfiles.add(0, profile)
            
            // Limit to 5
            val limitedProfiles = currentProfiles.take(5)
            
            preferences[SERVER_PROFILES_KEY] = gson.toJson(limitedProfiles)
        }
    }

    suspend fun deleteServerProfile(profile: ServerProfile) {
        context.dataStore.edit { preferences ->
            val json = preferences[SERVER_PROFILES_KEY] ?: "[]"
            val type = object : TypeToken<List<ServerProfile>>() {}.type
            val currentProfiles = try {
                gson.fromJson<List<ServerProfile>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            currentProfiles.removeAll { it.host == profile.host && it.port == profile.port }
            preferences[SERVER_PROFILES_KEY] = gson.toJson(currentProfiles)
        }
    }
}
