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
import retrofit2.HttpException

import com.example.comfyui_remote.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.example.comfyui_remote.network.ServerWorkflowFile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainViewModel(
    application: Application,
    private val repository: WorkflowRepository,
    private val mediaRepository: com.example.comfyui_remote.data.MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectionRepository: ConnectionRepository,
    private val localQueueRepository: com.example.comfyui_remote.data.LocalQueueRepository
) : AndroidViewModel(application) {

    private val imageRepository = com.example.comfyui_remote.data.ImageRepository()

    // Split state for UI
    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()
    
    private val _port = MutableStateFlow("8188")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _isSecure = MutableStateFlow(false)
    val isSecure: StateFlow<Boolean> = _isSecure.asStateFlow()

    private val _serverAddress = MutableStateFlow("")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()
    
    // One-shot event logic for navigation
    private val _shouldNavigateToWorkflows = MutableStateFlow(false)
    val shouldNavigateToWorkflows: StateFlow<Boolean> = _shouldNavigateToWorkflows.asStateFlow()
    
    private val _saveFolderUri = MutableStateFlow<String?>(null)
    val saveFolderUri: StateFlow<String?> = _saveFolderUri.asStateFlow()

    // Date range state for history filtering
    private val _historyStartDate = MutableStateFlow<Long?>(null)
    val historyStartDate: StateFlow<Long?> = _historyStartDate.asStateFlow()

    private val _historyEndDate = MutableStateFlow<Long?>(null)
    val historyEndDate: StateFlow<Long?> = _historyEndDate.asStateFlow()

    fun setHistoryDateRange(startDate: Long?, endDate: Long?) {
        _historyStartDate.value = startDate
        _historyEndDate.value = endDate
    }

    fun clearHistoryDateRange() {
        _historyStartDate.value = null
        _historyEndDate.value = null
    }

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

    val serverProfiles: StateFlow<List<com.example.comfyui_remote.data.ServerProfile>> = userPreferencesRepository.serverProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    
    fun updateIsSecure(secure: Boolean) {
        _isSecure.value = secure
    }

    fun saveConnection() {
        viewModelScope.launch {
            val p = _port.value.toIntOrNull() ?: 8188
            userPreferencesRepository.saveConnectionDetails(_host.value, p, _isSecure.value)
            
            // Phase 59: Save to profiles list
            userPreferencesRepository.saveServerProfile(
                com.example.comfyui_remote.data.ServerProfile(
                    host = _host.value,
                    port = p,
                    isSecure = _isSecure.value
                )
            )
        }
    }

    fun selectServerProfile(profile: com.example.comfyui_remote.data.ServerProfile) {
        _host.value = profile.host
        _port.value = profile.port.toString()
        _isSecure.value = profile.isSecure
        updateServerAddressFull()
        
        // Auto-connect? In plan it says selection auto-fills. 
        // User probably expects to hit connect, but for better UX we could auto-connect.
        // The plan says "Selecting a profile auto-fills host, port, and isSecure toggle".
        // I'll stick to auto-fill for now.
    }

    fun deleteServerProfile(profile: com.example.comfyui_remote.data.ServerProfile) {
        viewModelScope.launch {
            userPreferencesRepository.deleteServerProfile(profile)
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
    private val workflowExecutionService = com.example.comfyui_remote.domain.WorkflowExecutionService(imageRepository, workflowExecutor)
    
    // ... (existing helper flows)


    fun addToQueue(workflow: WorkflowEntity, inputs: List<com.example.comfyui_remote.domain.InputField>, batchCount: Int) {
        viewModelScope.launch {
            try {
                // We need to serialize inputs to JSON
                // Using standard Gson. InputField has 'label' property which should be serialized
                // allowing polymorphic deserialization in QueueViewModel.
                val gson = getApplication<ComfyApplication>().gson
                val inputsJson = gson.toJson(inputs)
             
                localQueueRepository.addToQueue(
                    workflowId = workflow.id,
                    workflowName = workflow.name,
                    workflowJson = workflow.jsonContent,
                    inputValuesJson = inputsJson,
                    batchCount = batchCount
                )
                
                // Optional: Notify success via a one-shot event or Snackbar state if needed
                // For now, no crash is the priority.
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to add to queue: ${e.message}"
                _executionStatus.value = ExecutionStatus.ERROR
            }
        }
    }
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _executionStatus = MutableStateFlow(ExecutionStatus.IDLE)
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()

    private val _selectedWorkflow = MutableStateFlow<WorkflowEntity?>(null)
    val selectedWorkflow: StateFlow<WorkflowEntity?> = _selectedWorkflow.asStateFlow()

    fun selectWorkflow(workflow: WorkflowEntity) {
        _selectedWorkflow.value = workflow
        _inputImages.value = emptyMap() // Reset inputs
        
        // Fix: Update generated image view to show the last result if available
        if (workflow.lastImageName != null) {
             val protocol = if (_isSecure.value) "https" else "http"
             val url = "$protocol://${_serverAddress.value}/view?filename=${workflow.lastImageName}&type=output"
            _generatedImage.value = url
            
            // Phase 60: Fetch ID for navigation
            viewModelScope.launch {
                val media = mediaRepository.getLatestByFilename(workflow.lastImageName)
                _generatedMediaId.value = media?.id
            }
        } else {
            _generatedImage.value = null
            _generatedMediaId.value = null
        }
        
        // Reset execution state when switching workflows
        clearErrorMessage()
    }

    fun updateServerAddress(address: String) {
        _serverAddress.value = address
    }
    
    // Create API Service dynamically
    // Use Application's shared OkHttpClient
    private val okHttpClient: OkHttpClient
        get() = (getApplication<Application>() as ComfyApplication).okHttpClient

    private fun buildApiService(): com.example.comfyui_remote.network.ComfyApiService {
         // Create a temporary retrofit instance for the call
         val protocol = if (_isSecure.value) "https" else "http"
         return retrofit2.Retrofit.Builder()
            .baseUrl("$protocol://${_serverAddress.value}/")
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

    // Cache for node titles to avoid re-parsing JSON during execution
    private var cachedNodeTitles: Map<String, String>? = null
    private var cachedWorkflowForTitles: WorkflowEntity? = null

    private fun resolveNodeTitle(workflow: WorkflowEntity, nodeId: String): String? {
        if (workflow !== cachedWorkflowForTitles) {
            // This happens on the main thread (from handleMessage), but only ONCE per workflow selection.
            // Subsequent lookups are O(1).
            val nodes = parseAllNodes(workflow.jsonContent)
            cachedNodeTitles = nodes.associate { it.id to it.title }
            cachedWorkflowForTitles = workflow
        }
        return cachedNodeTitles?.get(nodeId)
    }

    fun loadHistory(listing: com.example.comfyui_remote.data.GeneratedMediaListing) {
        android.util.Log.d("HISTORY_DEBUG", "==================== loadHistory() CALLED ====================")
        android.util.Log.d("HISTORY_DEBUG", "Listing ID: ${listing.id}")
        android.util.Log.d("HISTORY_DEBUG", "Listing workflowName: ${listing.workflowName}")
        
        viewModelScope.launch {
            android.util.Log.d("HISTORY_DEBUG", "Fetching media from repository with ID: ${listing.id}")
            val media = mediaRepository.getById(listing.id)
            
            android.util.Log.d("HISTORY_DEBUG", "Media retrieved: ${media != null}")
            if (media != null) {
                android.util.Log.d("HISTORY_DEBUG", "Media ID: ${media.id}")
                android.util.Log.d("HISTORY_DEBUG", "Media fileName: ${media.fileName}")
                android.util.Log.d("HISTORY_DEBUG", "Media timestamp: ${media.timestamp}")
                android.util.Log.d("HISTORY_DEBUG", "Media promptJson is null: ${media.promptJson == null}")
                android.util.Log.d("HISTORY_DEBUG", "Media promptJson length: ${media.promptJson?.length ?: 0}")
                if (media.promptJson != null) {
                    android.util.Log.d("HISTORY_DEBUG", "Media promptJson preview: ${media.promptJson.take(200)}")
                }
            } else {
                android.util.Log.e("HISTORY_DEBUG", "ERROR: Media is NULL for ID ${listing.id}")
            }
            
            if (media?.promptJson != null) {
                android.util.Log.d("HISTORY_DEBUG", "Creating temporary workflow...")
                // Create a temporary workflow entity
                val tempWorkflow = WorkflowEntity(
                    id = 0,
                    name = "History: ${DATE_FORMATTER_SHORT.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(media.timestamp))}",
                    jsonContent = media.promptJson,
                    createdAt = media.timestamp,
                    lastImageName = media.fileName
                )
                android.util.Log.d("HISTORY_DEBUG", "Temp workflow created: ${tempWorkflow.name}")
                _selectedWorkflow.value = tempWorkflow
                
                // Set the preview image for the DynamicFormScreen
                val protocol = if (_isSecure.value) "https" else "http"
                val url = "$protocol://${_serverAddress.value}/view?filename=${media.fileName}&type=output"
                android.util.Log.d("HISTORY_DEBUG", "Setting preview image URL: $url")
                _generatedImage.value = url
                _generatedMediaId.value = media.id
                
                android.util.Log.d("HISTORY_DEBUG", "Setting navigate to form = true")
                _navigateToForm.value = true
                android.util.Log.d("HISTORY_DEBUG", "loadHistory() completed successfully")
            } else {
                android.util.Log.e("HISTORY_DEBUG", "FAILED: promptJson is NULL - cannot load workflow")
                android.util.Log.e("HISTORY_DEBUG", "This history item has no associated workflow data")
            }
        }
    }
    
    private val _navigateToForm = MutableStateFlow(false)
    val navigateToForm: StateFlow<Boolean> = _navigateToForm.asStateFlow()
    
    fun onNavigatedToForm() {
        _navigateToForm.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
        if (_executionStatus.value == ExecutionStatus.ERROR) {
            _executionStatus.value = ExecutionStatus.IDLE
            _executionProgress.value = ExecutionProgress()
        }
    }

    private val _inputImages = MutableStateFlow<Map<String, String?>>(emptyMap())
    val inputImages: StateFlow<Map<String, String?>> = _inputImages.asStateFlow()

    fun setInputImage(nodeId: String, uriString: String?) {
        val current = _inputImages.value.toMutableMap()
        if (uriString == null) {
            current.remove(nodeId)
        } else {
            current[nodeId] = uriString
        }
        _inputImages.value = current
    }

    fun executeWorkflow(workflow: WorkflowEntity, inputs: List<com.example.comfyui_remote.domain.InputField>, batchCount: Int = 1) {
        viewModelScope.launch {
            _executionStatus.value = ExecutionStatus.QUEUED
            _errorMessage.value = null

            // Warning for missing nodes
            if (!workflow.missingNodes.isNullOrBlank()) {
                android.util.Log.w("EXECUTE_DEBUG", "Workflow has missing nodes but attempting execution anyway: ${workflow.missingNodes}")
            }

            try {
                _executionStatus.value = ExecutionStatus.EXECUTING
                val api = buildApiService()
                val resolver = getApplication<Application>().contentResolver

                // Phase 64: Handle Image Uploads (ONCE for the batch)
                val inputsToUpload = _inputImages.value
                val uploadedFilenames = if (inputsToUpload.isNotEmpty()) {
                     workflowExecutionService.uploadImages(api, inputsToUpload, resolver)
                } else {
                    emptyMap()
                }

                // Loop for Batch Generation
                repeat(batchCount) { iteration ->
                    // Randomize Seed for each run in the batch
                    val runInputs = inputs.map { field ->
                        if (field is com.example.comfyui_remote.domain.InputField.SeedInput) {
                            field.copy(value = kotlin.random.Random.nextLong(1, Long.MAX_VALUE))
                        } else {
                            field
                        }
                    }

                    // Patch & Queue via Service
                    val (updatedJson, response) = workflowExecutionService.prepareAndQueue(
                        api = api,
                        clientId = connectionRepository.clientId ?: "",
                        workflowJson = workflow.jsonContent,
                        uploadedFilenames = uploadedFilenames,
                        inputs = runInputs
                    )

                    // CACHE INPUT for History
                    _executionCache[response.prompt_id] = updatedJson
                    
                    android.util.Log.d("BatchGen", "Queued batch item ${iteration + 1}/$batchCount (Prompt ID: ${response.prompt_id})")
                }
                
                // Set status to EXECUTING (monitoring will handle updates)
                 _executionStatus.value = ExecutionStatus.EXECUTING

            } catch (e: retrofit2.HttpException) {
                // ... (existing error handling)
                val errorBody = e.response()?.errorBody()?.string()
                android.util.Log.e("API_ERROR", "HTTP ${e.code()}: $errorBody")
                
                val message = try {
                    val obj = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
                    if (obj.has("node_errors")) {
                        val nodeErrors = obj.getAsJsonObject("node_errors")
                        val firstEntry = nodeErrors.entrySet().firstOrNull()
                        if (firstEntry != null) {
                            val nodeId = firstEntry.key
                            val nodeError = firstEntry.value.asJsonObject
                            val classType = nodeError.get("class_type").asString
                            val errors = nodeError.getAsJsonArray("errors")
                            val firstError = errors.get(0).asJsonObject
                            val errMsg = firstError.get("message").asString
                            val details = if (firstError.has("details")) firstError.get("details").asString else ""
                            
                            val detailsStr = if (details.isNotBlank()) "\nDetails: $details" else ""
                            "Validation Error on Node $nodeId ($classType):\n$errMsg$detailsStr"
                        } else {
                            "Validation failed: ${obj.get("error").asJsonObject.get("message").asString}"
                        }
                    } else if (obj.has("error")) {
                        val err = obj.get("error")
                        if (err.isJsonObject) err.asJsonObject.get("message").asString else err.asString
                    } else {
                        "HTTP ${e.code()}: ${e.message()}"
                    }
                } catch (ex: Exception) {
                    "HTTP ${e.code()}: ${e.message()}"
                }
                
                _errorMessage.value = message
                _executionStatus.value = ExecutionStatus.ERROR
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.message ?: "Unknown error"
                _executionStatus.value = ExecutionStatus.ERROR
            }
        }
    }


    private val _generatedImage = MutableStateFlow<String?>(null)
    val generatedImage: StateFlow<String?> = _generatedImage.asStateFlow()

    private val _generatedMediaId = MutableStateFlow<Long?>(null)
    val generatedMediaId: StateFlow<Long?> = _generatedMediaId.asStateFlow()

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




    fun importServerWorkflow(serverFile: ServerWorkflowFile, onSuccess: (WorkflowEntity) -> Unit) {
        viewModelScope.launch {
            android.util.Log.d("WORKFLOW_IMPORT", "Importing server workflow: ${serverFile.name}")
            _isSyncing.value = true
            _importStatus.value = "Fetching workflow..."
            try {
                val fullPath = serverFile.fullpath ?: run {
                    android.util.Log.e("WORKFLOW_ERROR", "Cannot import workflow: serverFile.fullpath is null for ${serverFile.name}")
                    return@launch
                }
                android.util.Log.d("WORKFLOW_IMPORT", "Full path: $fullPath")
                
                val api = buildApiService()
                
                // URL-encode the path (slashes become %2F) as required by ComfyUI API
                val encodedPath = java.net.URLEncoder.encode(fullPath, "UTF-8").replace("+", "%20")
                android.util.Log.d("WORKFLOW_IMPORT", "Encoded path: $encodedPath")
                android.util.Log.d("WORKFLOW_IMPORT", "API endpoint: api/userdata/$encodedPath")
                
                val json = api.getFileContent("api/userdata/$encodedPath")
                android.util.Log.d("WORKFLOW_IMPORT", "Received workflow JSON (${json.toString().length} bytes)")
                
                val name = serverFile.name?.removeSuffix(".json") ?: "Unnamed Server Workflow"
                android.util.Log.d("WORKFLOW_IMPORT", "Import successful, calling importWorkflowInternal with name: $name")
                importWorkflowInternal(name, json.toString(), com.example.comfyui_remote.domain.WorkflowSource.SERVER_USERDATA, onSuccess)
            } catch (e: Exception) {
                android.util.Log.e("WORKFLOW_ERROR", "Error importing workflow ${serverFile.name}: ${e.javaClass.simpleName} - ${e.message}", e)
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
                _importStatus.value = ""
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
        connectionRepository.connect(_host.value, p, _isSecure.value)
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
                             resolveNodeTitle(wf, nodeId)
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
            android.util.Log.d("SYNC_DEBUG", "syncHistoryItem() called for promptId: $promptId")
            try {
                val finalData = data ?: buildApiService().getHistory(promptId).getAsJsonObject(promptId)
                if (finalData == null || !finalData.has("outputs")) {
                    android.util.Log.w("SYNC_DEBUG", "No outputs found for promptId: $promptId")
                    return@launch
                }

                val outputs = finalData.getAsJsonObject("outputs")

            // Extract image info (same as before but more robust)
            // Try to get from cache first (for real-time executions)
            var promptJson = _executionCache.get(promptId)
            if (promptJson != null) {
                android.util.Log.d("SYNC_DEBUG", "Found promptJson in execution cache for $promptId, length: ${promptJson.length}")
                _executionCache.remove(promptId)
            } else {
                android.util.Log.w("SYNC_DEBUG", "promptJson NOT in cache for $promptId, extracting from server response")
                // Fallback: Extract from server response (same as syncHistory)
                if (finalData.has("prompt")) {
                    val promptElement = finalData.get("prompt")
                    if (promptElement.isJsonArray) {
                        val arr = promptElement.asJsonArray
                        if (arr.size() >= 3) {
                            promptJson = arr.get(2).toString()
                            android.util.Log.d("SYNC_DEBUG", "Extracted promptJson from server response, length: ${promptJson?.length}")
                        } else {
                            android.util.Log.w("SYNC_DEBUG", "Prompt array too small (size ${arr.size()}), cannot extract workflowJson")
                        }
                    } else if (promptElement.isJsonObject) {
                        promptJson = promptElement.toString()
                        android.util.Log.d("SYNC_DEBUG", "Extracted promptJson from JsonObject, length: ${promptJson?.length}")
                    } else {
                        android.util.Log.w("SYNC_DEBUG", "Prompt element is neither array nor object")
                    }
                } else {
                    android.util.Log.w("SYNC_DEBUG", "No 'prompt' field in server response for $promptId")
                }
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

                                val protocol = if (_isSecure.value) "https" else "http"
                                val url = "$protocol://${_serverAddress.value}/view?filename=$filename&type=output"
                                _generatedImage.value = url

                                val hostParts = _serverAddress.value.split(":")
                                val host = hostParts.getOrNull(0) ?: ""
                                val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8188

                                val extension = filename.substringAfterLast('.', "").lowercase()
                                val isVideo = extension in listOf("mp4", "gif", "webm", "mkv")
                                val mediaType = if (isVideo) "VIDEO" else "IMAGE"

                                android.util.Log.d("SYNC_DEBUG", "Inserting media item: $filename (promptId: $promptId)")

                                val mediaEntity = com.example.comfyui_remote.data.GeneratedMediaEntity(
                                    workflowName = _selectedWorkflow.value?.name ?: "Unknown",
                                    fileName = filename,
                                    subfolder = subfolder,
                                    serverHost = host,
                                    serverPort = port,
                                    mediaType = mediaType,
                                    promptJson = promptJson,
                                    promptId = promptId
                                )

                                val insertedId = mediaRepository.insert(mediaEntity)

                                if (insertedId != -1L) {
                                    android.util.Log.d("SYNC_DEBUG", "Inserted media item with ID: $insertedId")
                                    _generatedMediaId.value = insertedId
                                } else {
                                    android.util.Log.d("SYNC_DEBUG", "Media item already exists, fetching ID")
                                    // Already exists, fetch ID
                                    val existing = mediaRepository.getLatestByFilename(filename)
                                    _generatedMediaId.value = existing?.id
                                }

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
                android.util.Log.e("SYNC_DEBUG", "Error syncing history item $promptId: ${e.message}", e)
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
            importWorkflowInternal(name, json, source, onSuccess)
        }
    }

    private suspend fun importWorkflowInternal(
        name: String, 
        json: String, 
        source: com.example.comfyui_remote.domain.WorkflowSource,
        onSuccess: (WorkflowEntity) -> Unit
    ) {
        _isSyncing.value = true
        _importStatus.value = "Importing workflow..."
        try {
            var finalJson = json
            android.util.Log.d("IMPORT_DEBUG", "Starting import for: $name, source: $source")
            android.util.Log.d("IMPORT_DEBUG", "Input JSON size: ${json.length} bytes")

            // Phase 30: Auto-convert Graph Format -> API Format
            // Run heavy JSON parsing on IO thread
            // Phase 30: Auto-convert Graph Format -> API Format
            // Run heavy JSON parsing on IO thread
            val conversionResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val jsonObj = com.google.gson.JsonParser.parseString(json).asJsonObject
                    val isFrontendFormat = jsonObj.has("nodes") && jsonObj.has("links")
                    android.util.Log.d("IMPORT_DEBUG", "Is frontend format: $isFrontendFormat")
                    
                    if (isFrontendFormat) {
                        var meta = _nodeMetadata.value
                        android.util.Log.d("IMPORT_DEBUG", "NodeMetadata available: ${meta != null}, size: ${meta?.size() ?: 0}")
                        
                        if (meta == null) {
                            try {
                                _importStatus.value = "Fetching metadata..."
                                android.util.Log.d("IMPORT_DEBUG", "Fetching metadata (raw)...")
                                // Use raw endpoint and parse manually to avoid blocking OkHttp thread
                                val responseBody = buildApiService().getObjectInfoRaw()
                                
                                _importStatus.value = "Parsing metadata..."
                                android.util.Log.d("IMPORT_DEBUG", "Parsing metadata JSON...")
                                val jsonString = responseBody.string()
                                meta = com.google.gson.JsonParser.parseString(jsonString).asJsonObject
                                _nodeMetadata.value = meta
                                android.util.Log.d("IMPORT_DEBUG", "Metadata parsed, size: ${meta.size()}")
                            } catch (e: Exception) {
                                android.util.Log.e("WORKFLOW_ERROR", "Metadata fetch failed for workflow '$name': ${e.javaClass.simpleName} - ${e.message}", e)
                                e.printStackTrace()
                            }
                        }
                        
                        val m = meta
                        if (m != null) {
                            _importStatus.value = "Converting format..."
                            android.util.Log.d("IMPORT_DEBUG", "Converting frontend to API format...")
                            val result = com.example.comfyui_remote.domain.GraphToApiConverter.convert(
                                json, 
                                com.example.comfyui_remote.data.ComfyObjectInfo(m)
                            )
                            android.util.Log.d("IMPORT_DEBUG", "Conversion complete. Result JSON size: ${result.json.length} bytes")
                            android.util.Log.d("IMPORT_DEBUG", "Missing nodes after conversion: ${result.missingNodes.size}")
                            if (result.missingNodes.isNotEmpty()) {
                                android.util.Log.d("IMPORT_DEBUG", "Missing nodes: ${result.missingNodes.joinToString(", ")}")
                            }
                            result
                        } else {
                            android.util.Log.e("IMPORT_DEBUG", "No metadata available - conversion skipped!")
                            com.example.comfyui_remote.domain.GraphToApiConverter.ConversionResult(json, emptyList())
                        }
                    } else {
                        com.example.comfyui_remote.domain.GraphToApiConverter.ConversionResult(json, emptyList())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WORKFLOW_ERROR", "Conversion error for workflow '$name': ${e.javaClass.simpleName} - ${e.message}", e)
                    e.printStackTrace()
                    com.example.comfyui_remote.domain.GraphToApiConverter.ConversionResult(json, emptyList())
                }
            }
            
            // Phase 50: Use Normalization Service (also on IO thread)
            _importStatus.value = "Normalizing..."
            val normalized = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                normalizationService.normalize(
                    name = name, 
                    rawJson = conversionResult.json, 
                    source = source,
                    existingMissingNodes = conversionResult.missingNodes
                )
            }
            android.util.Log.d("IMPORT_DEBUG", "Normalization complete")
            android.util.Log.d("IMPORT_DEBUG", "Normalized JSON size: ${normalized.jsonContent.length} bytes")
            android.util.Log.d("IMPORT_DEBUG", "Base models detected: ${normalized.baseModels.joinToString(", ")}")
            if (normalized.missingNodes.isNotEmpty()) {
                android.util.Log.d("IMPORT_DEBUG", "Missing nodes: ${normalized.missingNodes.joinToString(", ")}")
            }

            
            val baseModelsShort = normalized.baseModels.joinToString(", ")
            
            val tempWorkflow = WorkflowEntity(
                name = normalized.name,
                jsonContent = normalized.jsonContent,
                createdAt = System.currentTimeMillis(),
                baseModelName = normalized.baseModels.firstOrNull(), // Keep legacy field updated
                baseModels = baseModelsShort,
                source = normalized.source.name,
                formatVersion = normalized.formatVersion,
                missingNodes = if (normalized.missingNodes.isNotEmpty()) normalized.missingNodes.joinToString(", ") else null
            )
            
            // Database insert on IO thread
            _importStatus.value = "Saving..."
            android.util.Log.d("IMPORT_DEBUG", "Inserting workflow into database...")
            android.util.Log.d("IMPORT_DEBUG", "Workflow entity: name=\"${tempWorkflow.name}\", baseModels=\"${tempWorkflow.baseModels}\", missingNodes=\"${tempWorkflow.missingNodes}\"")
            val id = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.insert(tempWorkflow)
            }
            android.util.Log.d("IMPORT_DEBUG", "Database insert result: ID=$id")
            
            val workflow = tempWorkflow.copy(id = id)
            _selectedWorkflow.value = workflow
            android.util.Log.d("IMPORT_DEBUG", "Import complete. Workflow ID: $id")
            onSuccess(workflow)
        } catch (e: Exception) {
            android.util.Log.e("WORKFLOW_ERROR", "Error in importWorkflowInternal for workflow '$name': ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
            _importStatus.value = ""
        }
    }
    
    fun fetchServerWorkflows() {
        viewModelScope.launch {
            android.util.Log.d("WORKFLOW_FETCH", "Starting fetchServerWorkflows()")
            _isSyncing.value = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val response = buildApiService().getUserData(dir = "workflows")
                    android.util.Log.d("WORKFLOW_FETCH", "Received ${response.size} workflows from server")
                    
                    // Log each workflow file with details
                    response.forEachIndexed { index, workflow ->
                        android.util.Log.d("WORKFLOW_FETCH", "Workflow[$index]: name=\"${workflow.name}\", path=\"${workflow.path}\", type=\"${workflow.type}\", created=${workflow.created}")
                    }
                    
                    _serverWorkflows.value = response
                    android.util.Log.d("WORKFLOW_FETCH", "Fetch complete. Updated _serverWorkflows with ${response.size} items")
                } catch (e: Exception) {
                    android.util.Log.e("WORKFLOW_ERROR", "Error in fetchServerWorkflows: ${e.javaClass.simpleName} - ${e.message}", e)
                    e.printStackTrace()
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

    fun syncHistory(startDate: Long? = null, endDate: Long? = null) {
        viewModelScope.launch {
            android.util.Log.d("SYNC_DEBUG", "syncHistory() called with date range: $startDate - $endDate")
            _isSyncing.value = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                var parsedCount = 0
                var skippedCount = 0
                var filteredCount = 0
                try {
                    android.util.Log.d("SYNC_DEBUG", "Fetching existing prompt IDs...")
                    val existingIds = mediaRepository.getAllPromptIds().toSet()
                    android.util.Log.d("SYNC_DEBUG", "Found ${existingIds.size} existing prompt IDs")

                    val downloadStart = System.currentTimeMillis()
                    val newMediaItems = mutableListOf<com.example.comfyui_remote.data.GeneratedMediaEntity>()
                    val gson = getApplication<ComfyApplication>().gson
                    android.util.Log.d("SYNC_DEBUG", "Fetching history from server...")
                    
                    // Use larger max_items when date filtering is active
                    val maxItems = if (startDate != null || endDate != null) 1000 else 100
                    val responseBody = buildApiService().getHistory(maxItems = maxItems)
                    
                    responseBody.use { body ->
                        val reader = com.google.gson.stream.JsonReader(body.charStream())
                        reader.use { r ->
                            r.beginObject()
                            while (r.hasNext()) {
                                val executionId = r.nextName()
                                android.util.Log.d("SYNC_DEBUG", "Processing execution ID: $executionId")

                                if (existingIds.contains(executionId)) {
                                    skippedCount++
                                    android.util.Log.d("SYNC_DEBUG", "Hit existing item $executionId, stopping stream read")
                                    android.util.Log.w("SYNC_DEBUG", "**EARLY EXIT** - This stops reading ALL remaining history items!")
                                    // OPTIMIZATION: Stop reading stream as soon as we hit an existing item
                                    // We return from the .use block which closes both reader and body
                                    return@use
                                }

                                val element = gson.fromJson<com.google.gson.JsonObject>(r, com.google.gson.JsonObject::class.java)
                                parsedCount++

                                if (parsedCount % 10 == 0) {
                                    android.util.Log.d("SYNC_DEBUG", "Parsed $parsedCount items so far")
                                    kotlinx.coroutines.yield()
                                }

                                if (element != null && element.isJsonObject) {
                                    val item = element.asJsonObject
                                    
                                    // Extract timestamp from history item
                                    val itemTimestamp = extractTimestampFromHistoryItem(item)
                                    
                                    // Filter by date range if specified
                                    if (startDate != null && itemTimestamp < startDate) {
                                        filteredCount++
                                        continue
                                    }
                                    if (endDate != null && itemTimestamp > endDate) {
                                        filteredCount++
                                        continue
                                    }

                                    if (item.has("prompt")) {
                                        android.util.Log.d("SYNC_DEBUG", "Item $executionId has 'prompt' field")
                                        val promptElement = item.get("prompt")
                                        var workflowJson: String? = null

                                        if (promptElement.isJsonArray) {
                                            val arr = promptElement.asJsonArray
                                            android.util.Log.d("SYNC_DEBUG", "Prompt is JsonArray, size: ${arr.size()}")
                                            if (arr.size() >= 3) {
                                                workflowJson = arr.get(2).toString()
                                                android.util.Log.d("SYNC_DEBUG", "Extracted workflowJson from array[2], length: ${workflowJson?.length}")
                                            } else {
                                                android.util.Log.w("SYNC_DEBUG", "Prompt array size < 3, cannot extract workflowJson")
                                            }
                                        } else if (promptElement.isJsonObject) {
                                            workflowJson = promptElement.toString()
                                            android.util.Log.d("SYNC_DEBUG", "Extracted workflowJson from JsonObject, length: ${workflowJson?.length}")
                                        } else {
                                            android.util.Log.w("SYNC_DEBUG", "Prompt element is neither array nor object")
                                        }

                                        if (workflowJson != null) {
                                            android.util.Log.d("SYNC_DEBUG", "workflowJson is NOT null for $executionId")
                                            val name = extractNameFromHistoryItem(item, executionId)
                                            val hostParts = _serverAddress.value.split(":")
                                            val host = hostParts.getOrNull(0) ?: ""
                                            val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8188

                                            if (item.has("outputs")) {
                                                android.util.Log.d("SYNC_DEBUG", "Item $executionId has outputs")
                                                val outputs = item.getAsJsonObject("outputs")
                                                var imageCount = 0
                                                outputs.entrySet().forEach { (_, nodeOutput) ->
                                                    if (nodeOutput.isJsonObject) {
                                                        val out = nodeOutput.asJsonObject
                                                        if (out.has("images")) {
                                                            val images = out.getAsJsonArray("images")
                                                            images.forEach { imgElement ->
                                                                imageCount++
                                                                val img = imgElement.asJsonObject
                                                                val filename = img.get("filename").asString
                                                                val subfolder = if (img.has("subfolder")) img.get("subfolder").asString else null
                                                                val serverType = if (img.has("type")) img.get("type").asString else "output"

                                                                val extension = filename.substringAfterLast('.', "").lowercase()
                                                                val isVideo = extension in listOf("mp4", "gif", "webm", "mkv")
                                                                val mediaType = if (isVideo) "VIDEO" else "IMAGE"

                                                                android.util.Log.d("SYNC_DEBUG", "Adding media item: $filename with promptJson length: ${workflowJson!!.length}")
                                                                newMediaItems.add(
                                                                    com.example.comfyui_remote.data.GeneratedMediaEntity(
                                                                        workflowName = name,
                                                                        fileName = filename,
                                                                        subfolder = subfolder,
                                                                        serverHost = host,
                                                                        serverPort = port,
                                                                        mediaType = mediaType,
                                                                        promptJson = workflowJson,
                                                                        promptId = executionId,
                                                                        serverType = serverType,
                                                                        timestamp = itemTimestamp
                                                                    )
                                                                )
                                                            }
                                                            android.util.Log.d("SYNC_DEBUG", "Item $executionId added $imageCount images to newMediaItems")
                                                        }
                                                    }
                                                }
                                            } else {
                                                android.util.Log.w("SYNC_DEBUG", "Item $executionId has outputs but workflowJson is NULL")
                                            }
                                        } else {
                                            android.util.Log.w("SYNC_DEBUG", "Item $executionId: workflowJson is NULL - skipping")
                                        }
                                    } else {
                                        android.util.Log.w("SYNC_DEBUG", "Item $executionId has no 'prompt' field")
                                    }
                                }
                            }
                            r.endObject()
                        }
                    }

                    if (newMediaItems.isNotEmpty()) {
                        android.util.Log.d("SYNC_DEBUG", "Inserting ${newMediaItems.size} new media items into database...")
                        mediaRepository.insert(newMediaItems)
                        android.util.Log.d("SYNC_DEBUG", "Insert complete")
                    }

                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.d("SyncHistory", "Sync complete in ${duration}ms. Skipped: $skippedCount, Parsed: $parsedCount, Filtered: $filteredCount, Inserted: ${newMediaItems.size}")

                } catch (e: Exception) {
                    android.util.Log.e("SYNC_DEBUG", "Sync error: ${e.message}", e)
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
        val date = DATE_FORMATTER_LONG.format(LocalDateTime.now())
        return "History $date"
    }

    private fun extractTimestampFromHistoryItem(item: com.google.gson.JsonObject): Long {
        // Try to extract timestamp from various possible locations
        return try {
            when {
                item.has("prompt") && item.get("prompt").isJsonArray -> {
                    try {
                        val arr = item.get("prompt").asJsonArray
                        if (arr.size() >= 2 && arr.get(1).isJsonPrimitive) {
                            val element = arr.get(1)
                            // Try to parse as long, but catch if it's a string (like UUID)
                            try {
                                element.asLong
                            } catch (e: NumberFormatException) {
                                android.util.Log.w("SYNC_DEBUG", "Prompt array[1] is not a valid timestamp: ${element.asString}")
                                // Fallback to current time
                                System.currentTimeMillis()
                            }
                        } else {
                            android.util.Log.w("SYNC_DEBUG", "Prompt array size < 2 or element[1] not primitive")
                            System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SYNC_DEBUG", "Error extracting timestamp from prompt array: ${e.message}")
                        System.currentTimeMillis()
                    }
                }
                item.has("status") && item.getAsJsonObject("status").has("completed") -> {
                    try {
                        item.getAsJsonObject("status").get("completed").asLong
                    } catch (e: Exception) {
                        android.util.Log.w("SYNC_DEBUG", "Error extracting timestamp from status.completed: ${e.message}")
                        System.currentTimeMillis()
                    }
                }
                else -> {
                    android.util.Log.d("SYNC_DEBUG", "No timestamp found in history item, using current time")
                    System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SYNC_DEBUG", "Unexpected error in extractTimestampFromHistoryItem: ${e.message}")
            System.currentTimeMillis()
        }
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
            val savedIsSecure = userPreferencesRepository.isSecure.first()
            _host.value = savedHost
            _port.value = savedPort.toString()
            _isSecure.value = savedIsSecure
            _saveFolderUri.value = userPreferencesRepository.saveFolderUri.first()

            updateServerAddressFull()
        }

        // Backfill baseModelName
        // Bolt: Moved to IO dispatcher to avoid blocking main thread with JSON parsing during startup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
        // Bolt: Move message handling (JSON parsing) to IO thread to prevent UI jank
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            connectionRepository.messages.collect { message ->
                handleMessage(message)
            }
        }
    }

    companion object {
        private val DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        private val DATE_FORMATTER_LONG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: WorkflowRepository,
    private val mediaRepository: com.example.comfyui_remote.data.MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectionRepository: ConnectionRepository,
    private val localQueueRepository: com.example.comfyui_remote.data.LocalQueueRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, mediaRepository, userPreferencesRepository, connectionRepository, localQueueRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
