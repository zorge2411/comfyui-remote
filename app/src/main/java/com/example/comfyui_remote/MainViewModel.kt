package com.example.comfyui_remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.data.WorkflowRepository
import com.example.comfyui_remote.network.ComfyWebSocket
import com.example.comfyui_remote.network.ExecutionStatus
import com.example.comfyui_remote.network.WebSocketState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

import com.example.comfyui_remote.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first

class MainViewModel(
    private val repository: WorkflowRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // Split state for UI
    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()
    
    private val _port = MutableStateFlow("8188")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _serverAddress = MutableStateFlow("")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()
    
    // One-shot event logic for navigation
    private val _shouldNavigateToWorkflows = MutableStateFlow(false)
    val shouldNavigateToWorkflows: StateFlow<Boolean> = _shouldNavigateToWorkflows.asStateFlow()
    
    fun onNavigatedToWorkflows() {
        _shouldNavigateToWorkflows.value = false
    }
    
    init {
        // Load saved values
        viewModelScope.launch {
            val savedHost = userPreferencesRepository.savedHost.first()
            val savedPort = userPreferencesRepository.savedPort.first()
            _host.value = savedHost
            _port.value = savedPort.toString()
            updateServerAddressFull()
        }
    }

    private fun updateServerAddressFull() {
        _serverAddress.value = "${_host.value}:${_port.value}"
    }

    fun updateHost(newHost: String) {
        _host.value = newHost
        updateServerAddressFull()
    }
    
    fun updatePort(newPort: String) {
        _port.value = newPort
        updateServerAddressFull()
    }
    
    fun saveConnection() {
        viewModelScope.launch {
            val p = _port.value.toIntOrNull() ?: 8188
            userPreferencesRepository.saveConnectionDetails(_host.value, p)
        }
    }

    private var comfyWebSocket: ComfyWebSocket? = null
    // In a real app, inject this via Hilt
    private val okHttpClient = OkHttpClient.Builder().build()

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // Phase 2: Workflow Operations
    val allWorkflows = repository.allWorkflows

    // Phase 3: Execution Logic
    private val workflowParser = com.example.comfyui_remote.domain.WorkflowParser()
    private val workflowExecutor = com.example.comfyui_remote.domain.WorkflowExecutor()
    
    private val _executionStatus = MutableStateFlow(ExecutionStatus.IDLE)
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()

    private val _selectedWorkflow = MutableStateFlow<WorkflowEntity?>(null)
    val selectedWorkflow: StateFlow<WorkflowEntity?> = _selectedWorkflow.asStateFlow()

    fun selectWorkflow(workflow: WorkflowEntity) {
        _selectedWorkflow.value = workflow
    }

    fun updateServerAddress(address: String) {
        _serverAddress.value = address
    }
    
    // Create API Service dynamically or verify lazy creation. 
    // For simplicity, we create a new retrofit instance or reuse one based on server address.
    // In this MVP, we assume address doesn't change mid-session or we handle it crudely.
    private fun getApiService(): androidx.lifecycle.LiveData<com.example.comfyui_remote.network.ComfyApiService?> {
        // Real implementation would use DI or Factory to rebuild Retrofit on address change
        // For MVP, assume it's set before use. 
        return androidx.lifecycle.MutableLiveData(null) // simplified
    }

    private fun buildApiService(): com.example.comfyui_remote.network.ComfyApiService {
         // Create a temporary retrofit instance for the call
         return retrofit2.Retrofit.Builder()
            .baseUrl("http://${_serverAddress.value}/")
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.example.comfyui_remote.network.ComfyApiService::class.java)
    }

    fun parseWorkflowInputs(json: String): List<com.example.comfyui_remote.domain.InputField> {
        return workflowParser.parse(json)
    }

    fun executeWorkflow(workflow: WorkflowEntity, inputs: List<com.example.comfyui_remote.domain.InputField>) {
        viewModelScope.launch {
            _executionStatus.value = ExecutionStatus.QUEUED
            try {
                // 1. Inject values
                val updatedJson = workflowExecutor.injectValues(workflow.jsonContent, inputs)
                val promptJson = com.google.gson.JsonParser.parseString(updatedJson).asJsonObject
                
                // 2. Queue Prompt
                val api = buildApiService()
                val response = api.queuePrompt(
                    com.example.comfyui_remote.network.PromptRequest(
                        prompt = promptJson, 
                        // If we want accurate WS tracking we need to send client_id (socketID)
                        // comfyWebSocket?.clientId 
                    )
                )
                
                // 3. Monitor (Basic implementation: assume QUEUED -> EXECUTING via WS later)
                 _executionStatus.value = ExecutionStatus.EXECUTING

            } catch (e: Exception) {
                e.printStackTrace()
                _executionStatus.value = ExecutionStatus.ERROR
            }
        }
    }


    private val _generatedImage = MutableStateFlow<String?>(null)
    val generatedImage: StateFlow<String?> = _generatedImage.asStateFlow()

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
            viewModelScope.launch {
                 ws.messages.collect { message ->
                     if (message != null) {
                         handleMessage(message)
                     }
                 }
            }
            ws.connect()
        }
    }

    private fun handleMessage(json: String) {
        try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val type = obj.get("type").asString
            if (type == "executed") {
                val data = obj.getAsJsonObject("data")
                val output = data.getAsJsonObject("output")
                if (output != null && output.has("images")) {
                    val images = output.getAsJsonArray("images")
                    if (images.size() > 0) {
                        val image = images.get(0).asJsonObject
                        val filename = image.get("filename").asString
                        // Construct URL: http://host/view?filename=...
                        val url = "http://${_serverAddress.value}/view?filename=$filename&type=output"
                        _generatedImage.value = url
                        _executionStatus.value = ExecutionStatus.FINISHED
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors for non-matching messages
        }
    }

    fun disconnect() {
        comfyWebSocket?.disconnect()
        comfyWebSocket = null
        _connectionState.value = WebSocketState.DISCONNECTED
        _shouldNavigateToWorkflows.value = false
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

class MainViewModelFactory(
    private val repository: WorkflowRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
