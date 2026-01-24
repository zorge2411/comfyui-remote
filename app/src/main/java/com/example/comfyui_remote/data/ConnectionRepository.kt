package com.example.comfyui_remote.data

import com.example.comfyui_remote.network.ComfyWebSocket
import com.example.comfyui_remote.network.WebSocketState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class ConnectionRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder().build()
    
    private var comfyWebSocket: ComfyWebSocket? = null

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // Using SharedFlow for messages to allow multiple subscribers if needed
    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    

    
    // Track connection details for reconnection
    private var currentHost: String? = null
    private var currentPort: Int? = null
    private var isUserDisconnected = false
    private var reconnectAttempt = 0
    
    init {
        scope.launch {
            connectionState.collect { state ->
                if ((state == WebSocketState.DISCONNECTED || state == WebSocketState.ERROR) && !isUserDisconnected) {
                    // Auto-reconnect
                    if (currentHost != null && currentPort != null) {
                        _connectionState.value = WebSocketState.RECONNECTING
                        val delayMs = (2000L * (1L shl kotlin.math.min(reconnectAttempt, 5))) // Exp backoff
                        kotlinx.coroutines.delay(delayMs)
                        reconnectAttempt++
                        connect(currentHost!!, currentPort!!)
                    }
                } else if (state == WebSocketState.CONNECTED) {
                    reconnectAttempt = 0
                }
            }
        }
    }
    
    val clientId: String?
        get() = comfyWebSocket?.clientId

    fun connect(host: String, port: Int) {
        currentHost = host
        currentPort = port
        isUserDisconnected = false
        
        val serverAddress = "$host:$port"
        
        if (comfyWebSocket != null) {
            // If already connected to same, do nothing? Or force reconnect?
            // For now, simple logic:
            if (_connectionState.value == WebSocketState.CONNECTED) {
                 // Force close if we are calling connect again explicitly
                 comfyWebSocket?.disconnect() 
            }
        }

        comfyWebSocket = ComfyWebSocket(okHttpClient, serverAddress).also { ws ->
            scope.launch {
                ws.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }
            scope.launch {
                ws.messages.collect { message ->
                    _messages.emit(message)
                }
            }
            ws.connect()
        }
    }

    fun disconnect() {
        isUserDisconnected = true
        comfyWebSocket?.disconnect()
        comfyWebSocket = null
        _connectionState.value = WebSocketState.DISCONNECTED
    }
}
