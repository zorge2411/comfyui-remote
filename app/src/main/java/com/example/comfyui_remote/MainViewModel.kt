package com.example.comfyui_remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.data.WorkflowRepository
import com.example.comfyui_remote.network.ComfyWebSocket
import com.example.comfyui_remote.network.WebSocketState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainViewModel(private val repository: WorkflowRepository) : ViewModel() {

    private val _serverAddress = MutableStateFlow("192.168.1.X:8188")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()

    private var comfyWebSocket: ComfyWebSocket? = null
    // In a real app, inject this via Hilt
    private val okHttpClient = OkHttpClient.Builder().build()

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // Phase 2: Workflow Operations
    val allWorkflows = repository.allWorkflows

    fun updateServerAddress(address: String) {
        _serverAddress.value = address
    }

    fun connect() {
        if (comfyWebSocket != null) {
            disconnect()
        }

        comfyWebSocket = ComfyWebSocket(okHttpClient, _serverAddress.value).also { ws ->
            viewModelScope.launch {
                ws.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }
            ws.connect()
        }
    }

    fun disconnect() {
        comfyWebSocket?.disconnect()
        comfyWebSocket = null
    }
    
    fun importWorkflow(name: String, json: String) {
        viewModelScope.launch {
            repository.addWorkflow(name, json)
        }
    }

    fun deleteWorkflow(workflow: WorkflowEntity) {
        viewModelScope.launch {
            repository.deleteWorkflow(workflow)
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

class MainViewModelFactory(private val repository: WorkflowRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
