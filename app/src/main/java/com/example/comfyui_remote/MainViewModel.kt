package com.example.comfyui_remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.comfyui_remote.network.ComfyWebSocket
import com.example.comfyui_remote.network.WebSocketState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainViewModel : ViewModel() {

    private val _serverAddress = MutableStateFlow("192.168.1.X:8188")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()

    private var comfyWebSocket: ComfyWebSocket? = null

    // In a real app, inject this via Hilt
    private val okHttpClient = OkHttpClient.Builder().build()

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

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

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
