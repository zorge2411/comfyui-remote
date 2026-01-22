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

    val savedHost: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[HOST_KEY] ?: "192.168.1.X"
        }
        
    val savedPort: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PORT_KEY] ?: 8188 
        }

    suspend fun saveConnectionDetails(host: String, port: Int) {
        context.dataStore.edit { preferences ->
            preferences[HOST_KEY] = host
            preferences[PORT_KEY] = port
        }
    }
}
