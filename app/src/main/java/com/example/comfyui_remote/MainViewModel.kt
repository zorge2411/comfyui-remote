package com.example.comfyui_remote

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.comfyui_remote.data.ConnectionRepository
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.data.WorkflowRepository
import com.example.comfyui_remote.network.ExecutionStatus
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.service.ExecutionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

import com.example.comfyui_remote.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first

class MainViewModel(
    application: Application,
    private val repository: WorkflowRepository,
    private val mediaRepository: com.example.comfyui_remote.data.MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectionRepository: ConnectionRepository
) : AndroidViewModel(application) {

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
    
    private val _saveFolderUri = MutableStateFlow<String?>(null)
    val saveFolderUri: StateFlow<String?> = _saveFolderUri.asStateFlow()
    
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
            _saveFolderUri.value = userPreferencesRepository.saveFolderUri.first()
            updateServerAddressFull()
        }
        
        // Observe global connection state from Repository
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                // Auto-navigate on successful connection if not already there?
                // Or just expose state. 
                // The original logic was inside connect(), but now it's reactive.
            }
        }
        
        // Observe messages from Repository
        viewModelScope.launch {
            connectionRepository.messages.collect { message ->
                handleMessage(message)
            }
        }
    }

    private fun updateServerAddressFull() {
        // Strip protocol if user added it
        val cleanHost = _host.value
            .removePrefix("http://")
            .removePrefix("https://")
        _serverAddress.value = "${cleanHost}:${_port.value}"
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

    fun saveSaveFolderUri(uri: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveSaveFolderUri(uri)
            _saveFolderUri.value = uri
        }
    }

    // Direct access for UI
    val connectionState: StateFlow<WebSocketState> = connectionRepository.connectionState

    // Phase 2: Workflow Operations
    val allWorkflows = repository.allWorkflows

    // Phase 8/9: Gallery Data
    val allMedia = mediaRepository.allMedia

    fun getMediaById(id: Long): kotlinx.coroutines.flow.Flow<com.example.comfyui_remote.data.GeneratedMediaEntity?> {
        return mediaRepository.allMedia.map { list ->
            list.find { it.id == id }
        }
    }

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
    
    // Create API Service dynamically
    private val okHttpClient = OkHttpClient.Builder().build()

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
                        client_id = connectionRepository.clientId 
                    )
                )
                
                // 3. Monitor
                 _executionStatus.value = ExecutionStatus.EXECUTING

            } catch (e: Exception) {
                e.printStackTrace()
                _executionStatus.value = ExecutionStatus.ERROR
            }
        }
    }


    private val _generatedImage = MutableStateFlow<String?>(null)
    val generatedImage: StateFlow<String?> = _generatedImage.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    fun fetchAvailableModels() {
        viewModelScope.launch {
            try {
                val api = buildApiService()
                val models = api.getModels("checkpoints")
                _availableModels.value = models
            } catch (e: Exception) {
                e.printStackTrace()
                // In a real app, expose error state
            }
        }
    }

    fun connect() {
        // Start Foreground Service
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, ExecutionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        // Connect via Repository
        val p = _port.value.toIntOrNull() ?: 8188
        connectionRepository.connect(_host.value, p)
        fetchAvailableModels()
    }
    
    fun disconnect() {
        connectionRepository.disconnect()
        _shouldNavigateToWorkflows.value = false
        
        // Stop Service
        val context = getApplication<Application>()
        context.stopService(Intent(context, ExecutionService::class.java))
    }

    private fun handleMessage(json: String) {
        try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val type = obj.get("type").asString
            
            when (type) {
                "execution_start" -> {
                    // Confirm execution has begun
                    _executionStatus.value = ExecutionStatus.EXECUTING
                }
                "executing" -> {
                    val data = obj.getAsJsonObject("data")
                    // When node is null, the prompt execution is complete
                    if (data.has("node") && data.get("node").isJsonNull) {
                        _executionStatus.value = ExecutionStatus.FINISHED
                    }
                }
                "executed" -> {
                    val data = obj.getAsJsonObject("data")
                    val output = data.getAsJsonObject("output")
                    if (output != null && output.has("images")) {
                        val images = output.getAsJsonArray("images")
                        if (images.size() > 0) {
                            val image = images.get(0).asJsonObject
                            val filename = image.get("filename").asString
                            val subfolder = if (image.has("subfolder")) image.get("subfolder").asString else null
                            
                            // Construct URL: http://host/view?filename=...
                            val url = "http://${_serverAddress.value}/view?filename=$filename&type=output"
                            _generatedImage.value = url

                            // Phase 8: Auto-save metadata
                            viewModelScope.launch {
                                val hostParts = _serverAddress.value.split(":")
                                val host = hostParts.getOrNull(0) ?: ""
                                val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8188
                                
                                val extension = filename.substringAfterLast('.', "").lowercase()
                                val isVideo = extension in listOf("mp4", "gif", "webm", "mkv")
                                val type = if (isVideo) "VIDEO" else "IMAGE"
                                
                                mediaRepository.insert(
                                    com.example.comfyui_remote.data.GeneratedMediaEntity(
                                        workflowName = _selectedWorkflow.value?.name ?: "Unknown",
                                        fileName = filename,
                                        subfolder = subfolder,
                                        serverHost = host,
                                        serverPort = port,
                                        mediaType = type
                                    )
                                )
                                
                                // Update workflow with the generated image name
                                _selectedWorkflow.value?.let { workflow ->
                                    repository.insert(workflow.copy(lastImageName = filename))
                                }
                            }
                        }
                    }
                    // Always transition to FINISHED on 'executed'
                    _executionStatus.value = ExecutionStatus.FINISHED
                }
                "execution_error" -> {
                    _executionStatus.value = ExecutionStatus.ERROR
                }
                "status" -> {
                    // Could track queue remaining here if needed
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors for non-matching messages
        }
    }
    
    fun importWorkflow(name: String, json: String) {
        viewModelScope.launch {
            val workflow = WorkflowEntity(
                name = name,
                jsonContent = json,
                createdAt = System.currentTimeMillis()
            )
            repository.insert(workflow)
        }
    }
    
    fun syncHistory() {
        viewModelScope.launch {
            try {
                // Get history raw json
                val history = buildApiService().getHistory()
                
                // Iterate
                history.entrySet().forEach { (executionId, element) ->
                    if (element.isJsonObject) {
                        val item = element.asJsonObject
                        
                        if (item.has("prompt")) {
                            val promptElement = item.get("prompt")
                            
                            var workflowJson: String? = null
                            
                            if (promptElement.isJsonArray) {
                                val arr = promptElement.asJsonArray
                                if (arr.size() >= 3) {
                                    workflowJson = arr.get(2).toString() // The node graph
                                }
                            } else if (promptElement.isJsonObject) {
                                workflowJson = promptElement.toString()
                            }
                            
                            if (workflowJson != null) {
                                val name = extractNameFromHistoryItem(item, executionId)
                                val workflow = WorkflowEntity(
                                    name = name,
                                    jsonContent = workflowJson,
                                    createdAt = System.currentTimeMillis()
                                )
                                repository.insert(workflow)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun extractNameFromHistoryItem(item: com.google.gson.JsonObject, executionId: String): String {
        try {
            if (item.has("extra_data")) {
                val extraData = item.getAsJsonObject("extra_data")
                if (extraData.has("extra_pnginfo")) {
                    val pngInfo = extraData.getAsJsonObject("extra_pnginfo")
                    if (pngInfo.has("workflow")) {
                        val workflow = pngInfo.getAsJsonObject("workflow")
                        if (workflow.has("extra")) {
                            val extra = workflow.getAsJsonObject("extra")
                            if (extra.has("name")) {
                                return extra.get("name").asString
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore extraction errors
        }
        
        // Fallback to timestamped history
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
        return "History $date"
    }

    fun renameWorkflow(workflow: WorkflowEntity, newName: String) {
        viewModelScope.launch {
            repository.insert(workflow.copy(name = newName))
        }
    }

    fun deleteWorkflow(workflow: WorkflowEntity) {
        viewModelScope.launch {
            repository.deleteWorkflow(workflow)
        }
    }

    fun deleteMedia(mediaList: List<com.example.comfyui_remote.data.GeneratedMediaEntity>) {
        viewModelScope.launch {
            mediaRepository.delete(mediaList)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT disconnect automatically on ViewModel clear anymore 
        // because we want background persistence!
        // But if the User explicitly closes the app task, the Service *might* get killed or stay alive 
        // depending on START_STICKY. Standard behavior is to keep it unless user Force Stops.
        // However, if we want "Close App = Disconnect", we should verify. 
        // For "Background Persistence", we usually want it to stay until explicitly disconnected.
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: WorkflowRepository,
    private val mediaRepository: com.example.comfyui_remote.data.MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, mediaRepository, userPreferencesRepository, connectionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
