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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

import com.example.comfyui_remote.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.example.comfyui_remote.network.ServerWorkflowFile

class MainViewModel(
    application: Application,
    private val repository: WorkflowRepository,
    private val mediaRepository: com.example.comfyui_remote.data.MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectionRepository: ConnectionRepository
) : AndroidViewModel(application) {

    private val imageRepository = com.example.comfyui_remote.data.ImageRepository()

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

    data class ExecutionProgress(
        val currentNodeId: String? = null,
        val currentNodeTitle: String? = null,
        val currentStep: Int = 0,
        val maxSteps: Int = 0,
        val progress: Float = 0f
    )

    private val _executionProgress = MutableStateFlow(ExecutionProgress())
    val executionProgress: StateFlow<ExecutionProgress> = _executionProgress.asStateFlow()

    val themeMode: StateFlow<Int> = userPreferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemeMode(mode)
        }
    }
    
    fun onNavigatedToWorkflows() {
        _shouldNavigateToWorkflows.value = false
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
    val allMedia = mediaRepository.allMediaListings

    fun getMediaById(id: Long): kotlinx.coroutines.flow.Flow<com.example.comfyui_remote.data.GeneratedMediaEntity?> {
        return mediaRepository.allMedia.map { list ->
            list.find { it.id == id }
        }
    }

    // Phase 3: Execution Logic
    private val workflowParser = com.example.comfyui_remote.domain.WorkflowParser()
    private val normalizationService = com.example.comfyui_remote.domain.WorkflowNormalizationService(workflowParser)
    private val workflowExecutor = com.example.comfyui_remote.domain.WorkflowExecutor()
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _executionStatus = MutableStateFlow(ExecutionStatus.IDLE)
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()

    private val _selectedWorkflow = MutableStateFlow<WorkflowEntity?>(null)
    val selectedWorkflow: StateFlow<WorkflowEntity?> = _selectedWorkflow.asStateFlow()

    fun selectWorkflow(workflow: WorkflowEntity) {
        _selectedWorkflow.value = workflow
        
        // Fix: Update generated image view to show the last result if available
        if (workflow.lastImageName != null) {
             val url = "http://${_serverAddress.value}/view?filename=${workflow.lastImageName}&type=output"
            _generatedImage.value = url
        } else {
            _generatedImage.value = null
        }
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
        return workflowParser.parse(json, _nodeMetadata.value)
    }

    fun parseAllNodes(json: String): List<com.example.comfyui_remote.domain.NodeInfo> {
        return workflowParser.parseAllNodes(json)
    }

    // History & Caching
    private val _executionCache = mutableMapOf<String, String>() // prompt_id -> json content

    fun loadHistory(listing: com.example.comfyui_remote.data.GeneratedMediaListing) {
        viewModelScope.launch {
            val media = mediaRepository.getById(listing.id)
            if (media?.promptJson != null) {
                // Create a temporary workflow entity
                val tempWorkflow = WorkflowEntity(
                    id = 0,
                    name = "History: ${java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(media.timestamp))}",
                    jsonContent = media.promptJson,
                    createdAt = media.timestamp,
                    lastImageName = media.fileName
                )
                _selectedWorkflow.value = tempWorkflow
                
                // Set the preview image for the DynamicFormScreen
                val url = "http://${_serverAddress.value}/view?filename=${media.fileName}&type=output"
                _generatedImage.value = url
                
                _navigateToForm.value = true
            }
        }
    }
    
    private val _navigateToForm = MutableStateFlow(false)
    val navigateToForm: StateFlow<Boolean> = _navigateToForm.asStateFlow()
    
    fun onNavigatedToForm() {
        _navigateToForm.value = false
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
                
                // CACHE INPUT for History
                _executionCache[response.prompt_id] = updatedJson
                
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

    private val _nodeMetadata = MutableStateFlow<com.google.gson.JsonObject?>(null)
    val nodeMetadata: StateFlow<com.google.gson.JsonObject?> = _nodeMetadata.asStateFlow()

    fun fetchAvailableModels() {
        viewModelScope.launch {
            try {
                val api = buildApiService()
                val models = api.getModels("checkpoints")
                _availableModels.value = models
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchNodeMetadata() {
        viewModelScope.launch {
            try {
                _nodeMetadata.value = buildApiService().getObjectInfo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _serverWorkflows = MutableStateFlow<List<ServerWorkflowFile>>(emptyList())
    val serverWorkflows: StateFlow<List<ServerWorkflowFile>> = _serverWorkflows.asStateFlow()

    fun fetchServerWorkflows() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val response = buildApiService().getUserData(dir = "workflows")
                
                _serverWorkflows.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }


    fun importServerWorkflow(serverFile: ServerWorkflowFile, onSuccess: (WorkflowEntity) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val fullPath = serverFile.fullpath ?: return@launch
                val api = buildApiService()
                val json = api.getFileContent("userdata/$fullPath")
                


                val name = serverFile.name?.removeSuffix(".json") ?: "Unnamed Server Workflow"
                importWorkflow(name, json.toString(), com.example.comfyui_remote.domain.WorkflowSource.SERVER_USERDATA, onSuccess)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
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
        fetchNodeMetadata()
        fetchServerWorkflows()
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
                    _executionProgress.value = ExecutionProgress()
                }
                "executing" -> {
                    val data = obj.getAsJsonObject("data")
                    // When node is null, the prompt execution is complete
                    if (data.has("node") && data.get("node").isJsonNull) {
                        _executionStatus.value = ExecutionStatus.FINISHED
                        _executionProgress.value = ExecutionProgress()
                        
                        val promptId = if (data.has("prompt_id")) data.get("prompt_id").asString else ""
                        if (promptId.isNotEmpty()) {
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(2000)
                                syncHistoryItem(promptId)
                            }
                        }
                    } else {
                        val nodeId = data.get("node").asString
                        // Optionally lookup node title from current workflow
                        val title = _selectedWorkflow.value?.let { wf ->
                             val nodes = parseAllNodes(wf.jsonContent)
                             nodes.find { it.id == nodeId }?.title
                        }
                        _executionProgress.value = _executionProgress.value.copy(
                            currentNodeId = nodeId,
                            currentNodeTitle = title,
                            currentStep = 0,
                            maxSteps = 0,
                            progress = 0f
                        )
                    }
                }
                "progress" -> {
                    val data = obj.getAsJsonObject("data")
                    val value = data.get("value").asInt
                    val max = data.get("max").asInt
                    _executionProgress.value = _executionProgress.value.copy(
                        currentStep = value,
                        maxSteps = max,
                        progress = if (max > 0) value.toFloat() / max.toFloat() else 0f
                    )
                }
                "executed" -> {
                    val data = obj.getAsJsonObject("data")
                    val promptId = if (data.has("prompt_id")) data.get("prompt_id").asString else ""
                    syncHistoryItem(promptId, data)
                    _executionStatus.value = ExecutionStatus.FINISHED
                    _executionProgress.value = ExecutionProgress()
                }
                "execution_error" -> {
                    _executionStatus.value = ExecutionStatus.ERROR
                    _executionProgress.value = ExecutionProgress()
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors for non-matching messages
        }
    }

    /**
     * Syncs a single history item from the server. 
     * If 'data' is null, it fetches it from the /history/{id} endpoint.
     */
    private fun syncHistoryItem(promptId: String, data: com.google.gson.JsonObject? = null) {
        viewModelScope.launch {
            try {
                val finalData = data ?: buildApiService().getHistory(promptId).getAsJsonObject(promptId)
                if (finalData == null || !finalData.has("outputs")) return@launch

                val outputs = finalData.getAsJsonObject("outputs")
                
                // Extract image info (same as before but more robust)
                // Extract image info (same as before but more robust)
                val promptJson = _executionCache.get(promptId)
                if (promptJson != null) {
                    _executionCache.remove(promptId)
                }

                outputs.entrySet().forEach { (_, nodeOutput) ->
                    if (nodeOutput.isJsonObject) {
                        val out = nodeOutput.asJsonObject
                        if (out.has("images")) {
                            val images = out.getAsJsonArray("images")
                            images.forEach { imgElement ->
                                val image = imgElement.asJsonObject
                                val filename = image.get("filename").asString
                                val subfolder = if (image.has("subfolder")) image.get("subfolder").asString else null
                                
                                val url = "http://${_serverAddress.value}/view?filename=$filename&type=output"
                                _generatedImage.value = url

                                val hostParts = _serverAddress.value.split(":")
                                val host = hostParts.getOrNull(0) ?: ""
                                val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8188
                                
                                val extension = filename.substringAfterLast('.', "").lowercase()
                                val isVideo = extension in listOf("mp4", "gif", "webm", "mkv")
                                val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                                
                                mediaRepository.insert(
                                    com.example.comfyui_remote.data.GeneratedMediaEntity(
                                        workflowName = _selectedWorkflow.value?.name ?: "Unknown",
                                        fileName = filename,
                                        subfolder = subfolder,
                                        serverHost = host,
                                        serverPort = port,
                                        mediaType = mediaType,
                                        promptJson = promptJson,
                                        promptId = promptId
                                    )
                                )
                                
                                _selectedWorkflow.value?.let { workflow ->
                                    if (workflow.id != 0L) {
                                        repository.insert(workflow.copy(lastImageName = filename))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    


    fun importWorkflow(
        name: String, 
        json: String, 
        source: com.example.comfyui_remote.domain.WorkflowSource = com.example.comfyui_remote.domain.WorkflowSource.LOCAL_IMPORT,
        onSuccess: (WorkflowEntity) -> Unit
    ) {
        viewModelScope.launch {

            
            var finalJson = json

            // Phase 30: Auto-convert Graph Format -> API Format
            try {
                val jsonObj = com.google.gson.JsonParser.parseString(json).asJsonObject
                if (jsonObj.has("nodes") && jsonObj.has("links")) {
                    var meta = _nodeMetadata.value
                    if (meta == null) {
                        try {
                            meta = buildApiService().getObjectInfo()
                            _nodeMetadata.value = meta
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    val m = meta
                    if (m != null) {
                        finalJson = com.example.comfyui_remote.domain.GraphToApiConverter.convert(
                            json, 
                            com.example.comfyui_remote.data.ComfyObjectInfo(m)
                        )

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Phase 50: Use Normalization Service
            // Phase 50: Use Normalization Service
            val normalized = normalizationService.normalize(name, finalJson, source)
            

            
            val baseModelsShort = normalized.baseModels.joinToString(", ")
            
            val tempWorkflow = WorkflowEntity(
                name = normalized.name,
                jsonContent = normalized.jsonContent,
                createdAt = System.currentTimeMillis(),
                baseModelName = normalized.baseModels.firstOrNull(), // Keep legacy field updated
                baseModels = baseModelsShort,
                source = normalized.source.name,
                formatVersion = normalized.formatVersion
            )
            val id = repository.insert(tempWorkflow)
            
            val workflow = tempWorkflow.copy(id = id)
            _selectedWorkflow.value = workflow
            onSuccess(workflow)
        }
    }
    
    fun syncHistory() {
        viewModelScope.launch {
            _isSyncing.value = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Optimization: Fetch all IDs first to avoid duplicate inserts
                val existingIds = mediaRepository.getAllPromptIds().toSet()
                
                // Get history raw json
                val history = buildApiService().getHistory()
                
                val newMediaItems = mutableListOf<com.example.comfyui_remote.data.GeneratedMediaEntity>()

                // Iterate
                history.entrySet().forEach { (executionId, element) ->
                    // Attempt to sync all history items to fill gaps from previous partial syncs
                    // OnConflictStrategy.IGNORE handles duplicates efficiently.

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
                                
                                val hostParts = _serverAddress.value.split(":")
                                val host = hostParts.getOrNull(0) ?: ""
                                val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8188

                                // Extract filename from outputs if possible
                                if (item.has("outputs")) {
                                    val outputs = item.getAsJsonObject("outputs")
                                    outputs.entrySet().forEach { (_, nodeOutput) ->
                                        if (nodeOutput.isJsonObject) {
                                            val out = nodeOutput.asJsonObject
                                            if (out.has("images")) {
                                                val images = out.getAsJsonArray("images")
                                                images.forEach { imgElement ->
                                                    val img = imgElement.asJsonObject
                                                    val filename = img.get("filename").asString
                                                    val subfolder = if (img.has("subfolder")) img.get("subfolder").asString else null
                                                    
                                                    val extension = filename.substringAfterLast('.', "").lowercase()
                                                    val isVideo = extension in listOf("mp4", "gif", "webm", "mkv")
                                                    val mediaType = if (isVideo) "VIDEO" else "IMAGE"

                                                    newMediaItems.add(
                                                        com.example.comfyui_remote.data.GeneratedMediaEntity(
                                                            workflowName = name,
                                                            fileName = filename,
                                                            subfolder = subfolder,
                                                            serverHost = host,
                                                            serverPort = port,
                                                            mediaType = mediaType,
                                                            promptJson = workflowJson,
                                                            promptId = executionId
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Loop handled above, no fallback insert needed
                            }
                        }
                    }
                }
                
                if (newMediaItems.isNotEmpty()) {
                    mediaRepository.insert(newMediaItems)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
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

    fun deleteMedia(mediaList: List<com.example.comfyui_remote.data.GeneratedMediaListing>) {
        viewModelScope.launch {
             // Room uses @Delete on Entity. We can construct dummy entities with just the ID for deletion if strict mode isn't on.
             // But safer is to use a specific delete query in DAO or map back.
             // Mapping back is impossible without full data.
             // So we must use ID-based deletion.
             // For now, let's map to entities with dummy data but correct ID.
             val entities = mediaList.map { 
                 com.example.comfyui_remote.data.GeneratedMediaEntity(
                     id = it.id,
                     workflowName = "", fileName = "", subfolder = "", serverHost = "", serverPort = 0
                 )
             }
             mediaRepository.delete(entities)
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

    fun onImageSelected(input: com.example.comfyui_remote.domain.InputField.ImageInput, uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                // Optimistic update (show local URI)
                // We need to find the current input list and update it.
                // But inputs are state in the UI (DynamicFormScreen), not here.
                // Wait, DynamicFormScreen holds inputs state.
                // The ViewModel doesn't hold inputs state. 
                // We need to expose a way to upload and return the result?
                // Or change the architecture so VM holds inputs?
                
                // For now, let's just upload and let the UI callback handle the update?
                // No, the UI delegates to VM. VM should do the work. 
                // But the UI holds the state 'var inputs by remember'.
                // So this function should probably be suspend and return the result, 
                // OR take a callback.
                
                // But to allow background upload, it should be in VM.
                // Let's make this function upload and we need to tell the UI the result.
                // But the UI state is local.
                
                // Refactor: Inputs should ideally be in VM, but for now, let's keep it simple.
                // We will upload and "return" via a flow? No, that's complex for one field.
                
                // Let's implement 'uploadImage' that returns the server filename.
                // The UI calls it inside a scope.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun uploadImage(uri: android.net.Uri, contentResolver: android.content.ContentResolver): com.example.comfyui_remote.network.ImageUploadResponse? {
        if (_host.value.isEmpty()) return null
        return try {
            val api = buildApiService()
            imageRepository.uploadImage(api, uri, contentResolver)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun uploadManualImage(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        if (_host.value.isEmpty()) return

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val uploadResponse = uploadImage(uri, contentResolver)
                if (uploadResponse != null) {
                    val host = _host.value
                    val port = _port.value.toIntOrNull() ?: 8188

                    mediaRepository.insert(
                        com.example.comfyui_remote.data.GeneratedMediaEntity(
                            workflowName = "Manual Upload",
                            fileName = uploadResponse.name,
                            subfolder = uploadResponse.subfolder,
                            serverHost = host,
                            serverPort = port,
                            mediaType = "IMAGE",
                            serverType = uploadResponse.type.ifEmpty { "input" }
                        )
                    )
                    // No toast needed here as the Gallery updates automatically via Flow
                } else {
                    // Consider exposing an error channel to show toasts from VM if desired,
                    // but for now, we'll rely on the repo's internal error handling.
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    init {
        // Load saved values
        viewModelScope.launch {
            val savedHost = userPreferencesRepository.savedHost.first()
            val savedPort = userPreferencesRepository.savedPort.first()
            _host.value = savedHost
            _port.value = savedPort.toString()
            _saveFolderUri.value = userPreferencesRepository.saveFolderUri.first()
            _saveFolderUri.value = userPreferencesRepository.saveFolderUri.first()
            updateServerAddressFull()

            // Backfill baseModelName
            try {
                // We use a small delay or check to ensure DB is ready, but flow collection is safer
                // This is a one-time check on startup
                val existing = repository.allWorkflows.first()
                existing.forEach { wf ->
                    if (wf.baseModelName == null) {
                        try {
                            val inputs = workflowParser.parse(wf.jsonContent, null)
                            val modelInput = inputs.find { it is com.example.comfyui_remote.domain.InputField.ModelInput }
                            val modelName = (modelInput as? com.example.comfyui_remote.domain.InputField.ModelInput)?.value
                            
                            if (modelName != null) {
                                repository.insert(wf.copy(baseModelName = modelName))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Observe global connection state from Repository
        viewModelScope.launch {
            connectionRepository.connectionState.collect { state ->
                // Auto-sync history on connection
                if (state == WebSocketState.CONNECTED) {
                    syncHistory()
                }
            }
        }
        
        // Observe messages from Repository
        viewModelScope.launch {
            connectionRepository.messages.collect { message ->
                handleMessage(message)
            }
        }
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
