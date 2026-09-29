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
import kotlinx.coroutines.flow.flowOn
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
    private val localQueueRepository: com.example.comfyui_remote.data.LocalQueueRepository,
    private val savedGalleryListRepository: com.example.comfyui_remote.data.SavedGalleryListRepository
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
    
    // Phase 102: one connection gate for the screens
    val isConnected: StateFlow<Boolean> = connectionRepository.connectionState
        .map { it == WebSocketState.CONNECTED }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    val isConnecting: StateFlow<Boolean> = connectionRepository.connectionState
        .map { it == WebSocketState.CONNECTING || it == WebSocketState.RECONNECTING }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /** null until the saved settings are loaded; then whether a server host is saved. */
    private val _hasSavedServer = MutableStateFlow<Boolean?>(null)
    val hasSavedServer: StateFlow<Boolean?> = _hasSavedServer.asStateFlow()

    private val _saveFolderUri = MutableStateFlow<String?>(null)
    val saveFolderUri: StateFlow<String?> = _saveFolderUri.asStateFlow()

    // Phase 79: Gallery Sync Filtering with Saved Lists
    private val _gallerySyncFilter = MutableStateFlow(com.example.comfyui_remote.data.GallerySyncFilter.default())
    val gallerySyncFilter: StateFlow<com.example.comfyui_remote.data.GallerySyncFilter> = _gallerySyncFilter.asStateFlow()

    val savedGalleryLists = savedGalleryListRepository.allLists

    fun updateGallerySyncFilter(filter: com.example.comfyui_remote.data.GallerySyncFilter) {
        _gallerySyncFilter.value = filter
    }

    fun clearGallerySyncFilter() {
        _gallerySyncFilter.value = com.example.comfyui_remote.data.GallerySyncFilter.default()
    }

    /** Sets the gallery's filter (Phase 104): applied on the phone, so nothing is deleted or re-synced. */
    fun setGalleryFilter(filter: com.example.comfyui_remote.data.GallerySyncFilter) {
        _gallerySyncFilter.value = filter
    }

    /** Saves [filter] as a named list (saved lists are named filters since Phase 104). */
    suspend fun saveCurrentGalleryList(
        name: String,
        filter: com.example.comfyui_remote.data.GallerySyncFilter
    ): Result<com.example.comfyui_remote.data.SavedGalleryList> {
        return try {
            val count = com.example.comfyui_remote.domain.GalleryView.apply(mediaRepository.allMediaListings.first(), filter).size
            val savedList = com.example.comfyui_remote.data.SavedGalleryList.createLiveFilter(
                name = name,
                filter = filter,
                itemCount = count
            )
            savedGalleryListRepository.saveList(savedList)
            Result.success(savedList)
        } catch (e: Exception) {
            android.util.Log.e("GALLERY_FILTER", "Error saving gallery list", e)
            Result.failure(e)
        }
    }

    /** Applies a saved list's filter. Older Snapshot lists apply their filter too (D-03). */
    fun applySavedFilter(listId: String) {
        viewModelScope.launch {
            try {
                val savedList = savedGalleryListRepository.getListById(listId)
                if (savedList != null) _gallerySyncFilter.value = savedList.filter
            } catch (e: Exception) {
                android.util.Log.e("GALLERY_FILTER", "Error applying saved filter", e)
            }
        }
    }


    /**
     * Delete a saved gallery list.
     * @param listId The ID of the list to delete
     */
    fun deleteSavedGalleryList(listId: String) {
        viewModelScope.launch {
            try {
                savedGalleryListRepository.deleteList(listId)
                android.util.Log.d("GALLERY_FILTER", "Deleted saved list: $listId")
            } catch (e: Exception) {
                android.util.Log.e("GALLERY_FILTER", "Error deleting saved list", e)
            }
        }
    }

    /**
     * Rename a saved gallery list.
     * @param listId The ID of the list to rename
     * @param newName The new name for the list
     */
    fun renameSavedGalleryList(listId: String, newName: String) {
        viewModelScope.launch {
            try {
                savedGalleryListRepository.renameList(listId, newName)
                android.util.Log.d("GALLERY_FILTER", "Renamed saved list: $listId to $newName")
            } catch (e: Exception) {
                android.util.Log.e("GALLERY_FILTER", "Error renaming saved list", e)
            }
        }
    }

    /**
     * Duplicate a saved gallery list.
     * @param listId The ID of the list to duplicate
     */
    fun duplicateSavedGalleryList(listId: String) {
        viewModelScope.launch {
            try {
                savedGalleryListRepository.duplicateList(listId)
                android.util.Log.d("GALLERY_FILTER", "Duplicated saved list: $listId")
            } catch (e: Exception) {
                android.util.Log.e("GALLERY_FILTER", "Error duplicating saved list", e)
            }
        }
    }

    data class ExecutionProgress(
        val currentNodeId: String? = null,
        val currentNodeTitle: String? = null,
        val currentStep: Int = 0,
        val maxSteps: Int = 0,
        val progress: Float = 0f,
        val overallProgress: Float = 0f
    )

    private val _executionProgress = MutableStateFlow(ExecutionProgress())
    val executionProgress: StateFlow<ExecutionProgress> = _executionProgress.asStateFlow()

    /** The prompt this app's websocket reports progress for (Phase 102: matched to the server queue's running job). */
    private val _runningPromptId = MutableStateFlow<String?>(null)
    val runningPromptId: StateFlow<String?> = _runningPromptId.asStateFlow()

    val themeMode: StateFlow<Int> = userPreferencesRepository.themeMode.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val maxSyncItems: StateFlow<Int> = userPreferencesRepository.maxSyncItems.stateIn(viewModelScope, SharingStarted.Lazily, 100)

    /** Connect to the saved server on start (Phase 102). */
    val autoConnect: StateFlow<Boolean> = userPreferencesRepository.autoConnect.stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveAutoConnect(enabled) }
    }

    val serverProfiles: StateFlow<List<com.example.comfyui_remote.data.ServerProfile>> = userPreferencesRepository.serverProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemeMode(mode)
        }
    }

    fun updateMaxSyncItems(items: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveMaxSyncItems(items)
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
    
    fun updateIsSecure(secure: Boolean) {
        _isSecure.value = secure
    }

    fun saveConnection() {
        viewModelScope.launch {
            val p = _port.value.toIntOrNull() ?: 8188
            userPreferencesRepository.saveConnectionDetails(_host.value, p, _isSecure.value)
            _hasSavedServer.value = _host.value.isNotBlank()
            
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

    /** The gallery grid's items: stored media narrowed and ordered by the gallery filter (Phase 104). */
    val galleryMedia: StateFlow<List<com.example.comfyui_remote.data.GeneratedMediaListing>> =
        kotlinx.coroutines.flow.combine(allMedia, gallerySyncFilter) { media, filter ->
            com.example.comfyui_remote.domain.GalleryView.apply(media, filter)
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    /** How many items the user removed from the gallery (they stay hidden through syncs). */
    val removedMediaCount: StateFlow<Int> = mediaRepository.hiddenCount
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0)

    /** The last sync failure, for the gallery's error banner; null when the last sync worked. */
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun clearSyncError() {
        _syncError.value = null
    }

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
        
        // Phase 103: "Last used" sort; history previews (id 0) aren't stored workflows
        if (workflow.id != 0L) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.markUsed(workflow.id) }
        }

        // Fix: Update generated image view to show the last result if available
        if (workflow.lastImageName != null) {
            val base = "${if (_isSecure.value) "https" else "http"}://${_serverAddress.value}"
            _generatedImage.value = com.example.comfyui_remote.domain.MediaUrls.view(base, workflow.lastImageName)

            // Phase 60: Fetch ID for navigation. Phase 103: the stored row also gives the subfolder and type.
            viewModelScope.launch {
                val media = mediaRepository.getLatestByFilename(workflow.lastImageName)
                _generatedMediaId.value = media?.id
                if (media != null && _selectedWorkflow.value?.id == workflow.id) {
                    _generatedImage.value = com.example.comfyui_remote.domain.MediaUrls.view(
                        base, media.fileName, media.subfolder, media.serverType
                    )
                }
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

    /** The workflow's inputs with its remembered values applied (Phase 101). */
    fun parseWorkflowInputs(workflow: WorkflowEntity): List<com.example.comfyui_remote.domain.InputField> =
        com.example.comfyui_remote.domain.FormValues.overlay(
            parseWorkflowInputs(workflow.jsonContent),
            com.example.comfyui_remote.domain.FormValues.decode(workflow.savedInputs)
        )

    /** Positive and negative prompt source nodes, for the form layout (Phase 101). */
    fun promptNodeIds(json: String): Pair<String?, String?> =
        workflowParser.findPositivePromptNodeId(json) to workflowParser.findNegativePromptNodeId(json)

    private var saveFormJob: kotlinx.coroutines.Job? = null

    /** Remembers the form's values for this workflow, at most every 500 ms (each save rewrites the row). */
    fun saveFormValues(workflow: WorkflowEntity, inputs: List<com.example.comfyui_remote.domain.InputField>) {
        if (workflow.id == 0L) return // history previews aren't stored workflows
        saveFormJob?.cancel()
        saveFormJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            val current = repository.allWorkflows.first().firstOrNull { it.id == workflow.id } ?: return@launch
            repository.insert(current.copy(savedInputs = com.example.comfyui_remote.domain.FormValues.encode(inputs)))
        }
    }

    /** Forgets the remembered values and returns the workflow's own inputs. */
    suspend fun resetFormValues(workflow: WorkflowEntity): List<com.example.comfyui_remote.domain.InputField> {
        saveFormJob?.cancel()
        if (workflow.id != 0L) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val current = repository.allWorkflows.first().firstOrNull { it.id == workflow.id }
                if (current != null) repository.insert(current.copy(savedInputs = null))
            }
        }
        return parseWorkflowInputs(workflow.jsonContent)
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
        ensureNodeTitles(workflow)
        return cachedNodeTitles?.get(nodeId)
    }

    private fun ensureNodeTitles(workflow: WorkflowEntity) {
        if (workflow !== cachedWorkflowForTitles) {
            // This happens on the main thread (from handleMessage), but only ONCE per workflow selection.
            // Subsequent lookups are O(1).
            val nodes = parseAllNodes(workflow.jsonContent)
            cachedNodeTitles = nodes.associate { it.id to it.title }
            cachedWorkflowForTitles = workflow
        }
    }

    private val progressTracker = com.example.comfyui_remote.domain.ExecutionProgressTracker()

    private fun publishProgress() {
        val snap = progressTracker.snapshot()
        _executionProgress.value = _executionProgress.value.copy(
            currentStep = snap.currentStep,
            maxSteps = snap.maxSteps,
            progress = if (snap.maxSteps > 0) snap.currentStep.toFloat() / snap.maxSteps else 0f,
            overallProgress = snap.overall
        )
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
                    lastImageName = media.fileName,
                    // Phase 104: restored seeds start Fixed, so Generate reproduces the item's settings
                    savedInputs = com.example.comfyui_remote.domain.FormValues.encode(
                        parseWorkflowInputs(media.promptJson)
                            .filterIsInstance<com.example.comfyui_remote.domain.InputField.SeedInput>()
                            .map { it.copy(fixed = true) }
                    )
                )
                android.util.Log.d("HISTORY_DEBUG", "Temp workflow created: ${tempWorkflow.name}")
                _selectedWorkflow.value = tempWorkflow
                
                // Set the preview image for the DynamicFormScreen
                val protocol = if (_isSecure.value) "https" else "http"
                val url = com.example.comfyui_remote.domain.MediaUrls.view("$protocol://${_serverAddress.value}", media.fileName, media.subfolder, media.serverType)
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

    // The server accepted a prompt but skipped outputs that depend on failing nodes (Phase 92)
    private val _serverWarning = MutableStateFlow<String?>(null)
    val serverWarning: StateFlow<String?> = _serverWarning.asStateFlow()

    fun clearServerWarning() {
        _serverWarning.value = null
    }

    fun reportError(message: String) {
        _errorMessage.value = message
        _executionStatus.value = ExecutionStatus.ERROR
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

    /** The prompt Generate would send with [inputs], without uploads: what pre-flight and the list badge check. */
    private fun buildCheckPrompt(
        workflow: WorkflowEntity,
        inputs: List<com.example.comfyui_remote.domain.InputField>
    ): com.google.gson.JsonObject = com.google.gson.JsonParser.parseString(
        workflowExecutionService.buildPrompt(workflow.jsonContent, emptyMap(), inputs)
    ).asJsonObject

    /**
     * Checks the prompt that Generate / Add to Queue would send against the server's live /object_info
     * (Phase 91). Returns null when the server's node list can't be obtained; the caller then queues
     * without a check. Images picked for upload get their server names only at queue time, so list checks
     * on those LoadImage inputs are skipped.
     */
    suspend fun preflight(
        workflow: WorkflowEntity,
        inputs: List<com.example.comfyui_remote.domain.InputField>
    ): List<com.example.comfyui_remote.domain.PromptValidator.Issue>? {
        val metadata = _nodeMetadata.value ?: try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { buildApiService().getObjectInfo() }
                .also { _nodeMetadata.value = it }
        } catch (e: Exception) {
            android.util.Log.w("PREFLIGHT", "No /object_info, queueing without a check: ${e.message}")
            return null
        }
        val pendingUploads = _inputImages.value.filterValues { it != null }.keys
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                com.example.comfyui_remote.domain.PromptValidator.validate(buildCheckPrompt(workflow, inputs), metadata).filterNot {
                    it.kind == com.example.comfyui_remote.domain.PromptValidator.Kind.VALUE_NOT_IN_LIST &&
                        it.inputName == "image" && it.nodeId in pendingUploads
                }
            } catch (e: Exception) {
                android.util.Log.w("PREFLIGHT", "Pre-flight check failed, queueing without it", e)
                null
            }
        }
    }

    /** Node types of this workflow the connected server doesn't have, or null without live metadata. */
    fun missingNodeTypes(workflow: WorkflowEntity): List<String>? {
        val metadata = _nodeMetadata.value ?: return null
        return try {
            val prompt = com.google.gson.JsonParser.parseString(workflow.jsonContent).asJsonObject
            com.example.comfyui_remote.domain.PromptValidator.validate(prompt, metadata)
                .filter { it.kind == com.example.comfyui_remote.domain.PromptValidator.Kind.MISSING_NODE_TYPE }
                .map { it.classType }
                .distinct()
        } catch (e: Exception) {
            null
        }
    }

    fun executeWorkflow(workflow: WorkflowEntity, inputs: List<com.example.comfyui_remote.domain.InputField>, batchCount: Int = 1) {
        viewModelScope.launch {
            _executionStatus.value = ExecutionStatus.QUEUED
            _errorMessage.value = null
            _serverWarning.value = null

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
                    // Phase 101: random seeds get a new value each run; Fixed seeds are sent as typed
                    val (runInputs, usedSeeds) = com.example.comfyui_remote.domain.SeedPolicy.resolve(inputs) {
                        kotlin.random.Random.nextLong(1, Long.MAX_VALUE)
                    }
                    _lastUsedSeeds.value = usedSeeds

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
                    (getApplication<Application>() as ComfyApplication).serverQueueRepository
                        .rememberName(response.prompt_id, workflow.name)

                    // Accepted, but outputs depending on these nodes were skipped (Phase 92)
                    com.example.comfyui_remote.domain.ServerErrorReport.fromPartialAcceptance(
                        response.node_errors,
                        try { com.google.gson.JsonParser.parseString(updatedJson).asJsonObject } catch (ex: Exception) { null }
                    )?.let { _serverWarning.value = it.format() }
                    
                    android.util.Log.d("BatchGen", "Queued batch item ${iteration + 1}/$batchCount (Prompt ID: ${response.prompt_id})")
                }
                
                // Set status to EXECUTING (monitoring will handle updates)
                 _executionStatus.value = ExecutionStatus.EXECUTING

            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                android.util.Log.e("API_ERROR", "HTTP ${e.code()}: $errorBody")
                // Every failing node and reason, by title (Phase 92); titles come from the prompt we sent
                val sentPrompt = try {
                    com.google.gson.JsonParser.parseString(
                        workflowExecutionService.buildPrompt(workflow.jsonContent, emptyMap(), inputs)
                    ).asJsonObject
                } catch (ex: Exception) {
                    null
                }
                val message = com.example.comfyui_remote.domain.ServerErrorReport
                    .fromPromptError(e.code(), errorBody, sentPrompt).format()
                
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

    // ---- Phase 103: workflow list cards ----

    /** The models each stored workflow loads, by workflow id. */
    val workflowModels: StateFlow<Map<Long, List<com.example.comfyui_remote.domain.ModelRef>>> = run {
        val memo = HashMap<Long, Pair<String, List<com.example.comfyui_remote.domain.ModelRef>>>()
        allWorkflows.map { list ->
            list.associate { wf ->
                val cached = memo[wf.id]?.takeIf { it.first == wf.jsonContent }?.second
                wf.id to (cached ?: com.example.comfyui_remote.domain.WorkflowModels.of(wf.jsonContent)
                    .also { memo[wf.id] = wf.jsonContent to it })
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    private data class StatusKey(val json: String, val savedInputs: String?, val metadata: com.google.gson.JsonObject)

    /**
     * Whether each stored workflow will run on the connected server, checked like Generate's pre-flight
     * against the cached /object_info. Empty without metadata (offline): no badges. Unchanged workflows
     * aren't re-checked when the list re-emits (form values are saved every 500 ms).
     */
    val workflowStatus: StateFlow<Map<Long, com.example.comfyui_remote.domain.Compatibility>> = run {
        val memo = HashMap<Long, Pair<StatusKey, com.example.comfyui_remote.domain.Compatibility>>()
        kotlinx.coroutines.flow.combine(allWorkflows, _nodeMetadata) { list, metadata ->
            if (metadata == null) return@combine emptyMap()
            val result = LinkedHashMap<Long, com.example.comfyui_remote.domain.Compatibility>()
            for (wf in list) {
                val key = StatusKey(wf.jsonContent, wf.savedInputs, metadata)
                val cached = memo[wf.id]?.takeIf { it.first.json == key.json && it.first.savedInputs == key.savedInputs && it.first.metadata === metadata }
                val status = cached?.second ?: try {
                    com.example.comfyui_remote.domain.WorkflowCompatibility
                        .check(buildCheckPrompt(wf, parseWorkflowInputs(wf)), metadata)
                        .also { memo[wf.id] = key to it }
                } catch (e: Exception) {
                    android.util.Log.w("WORKFLOW_STATUS", "Check failed for ${wf.name}: ${e.message}")
                    null
                }
                if (status != null) result[wf.id] = status
            }
            result
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    /** Each stored workflow's last result, found through its lastImageName (media rows' workflowName isn't reliable). */
    val lastResults: StateFlow<Map<Long, com.example.comfyui_remote.data.GeneratedMediaListing>> =
        kotlinx.coroutines.flow.combine(allWorkflows, allMedia) { list, _ -> list }
            .map { list ->
                val byName = mediaRepository.latestListingsByFileNames(list.mapNotNull { it.lastImageName }.toSet())
                list.mapNotNull { wf -> wf.lastImageName?.let { byName[it] }?.let { wf.id to it } }.toMap()
            }
            .flowOn(kotlinx.coroutines.Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val workflowSort: StateFlow<com.example.comfyui_remote.domain.WorkflowSort> = userPreferencesRepository.workflowSort
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.comfyui_remote.domain.WorkflowSort.LAST_USED)

    fun setWorkflowSort(sort: com.example.comfyui_remote.domain.WorkflowSort) {
        viewModelScope.launch { userPreferencesRepository.saveWorkflowSort(sort) }
    }

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

    // Phase 97/99: model downloads through the server's comfyui_remote_helper extension; the queue itself lives
    // in the app-scoped ModelDownloadRepository, shared with the Queue screen and ExecutionService
    private val modelDownloadRepository =
        (getApplication<Application>() as ComfyApplication).modelDownloadRepository

    /** Completed once the saved host/port are loaded; server calls before that would go to an empty address. */
    private val settingsLoaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** null until checked after connecting; false when the server doesn't have the extension. */
    val helperAvailable: StateFlow<Boolean?> = modelDownloadRepository.helperAvailable
    val helperHasToken: StateFlow<Boolean> = modelDownloadRepository.hasToken
    val helperHasCivitaiToken: StateFlow<Boolean> = modelDownloadRepository.hasCivitaiToken
    val helperVersion: StateFlow<Int> = modelDownloadRepository.helperVersion

    /** Keyed by "directory/filename"; an active download wins over older finished ones for the same file. */
    val modelDownloads: StateFlow<Map<String, com.example.comfyui_remote.domain.ModelDownload>> =
        modelDownloadRepository.downloads
            .map { list -> list.reversed().associateBy { it.key } }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyMap())

    /** Bumped when a download finishes, so screens recompute their missing models. */
    private val _modelsVersion = MutableStateFlow(0)
    val modelsVersion: StateFlow<Int> = _modelsVersion.asStateFlow()

    private val modelListCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    init {
        viewModelScope.launch {
            modelDownloadRepository.finished.collect { download ->
                modelListCache.remove(download.directory)
                fetchNodeMetadata()
                _modelsVersion.value++
            }
        }
    }

    /** Checks for the server extension again unless it is known to be there (e.g. when a workflow screen opens). */
    fun refreshModelHelper() {
        if (helperAvailable.value == true) return
        if (connectionRepository.connectionState.value == WebSocketState.CONNECTED) modelDownloadRepository.refresh()
    }

    /**
     * Models this workflow needs that the server doesn't have: those its stored download links name that the
     * server's model lists lack, plus those the prompt names that /object_info doesn't offer, which may have
     * no link (url ""). [overrides] holds the form's current values, keyed "nodeId/fieldName".
     */
    suspend fun missingModels(
        workflow: WorkflowEntity,
        overrides: Map<String, String> = emptyMap()
    ): List<com.example.comfyui_remote.domain.ModelSource> {
        val stored = if (workflow.id == 0L) workflow
        else repository.allWorkflows.first().firstOrNull { it.id == workflow.id } ?: workflow
        val sources = com.example.comfyui_remote.domain.ModelSources.fromJson(stored.modelSources)
        settingsLoaded.await()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val available = HashMap<String, List<String>>()
            for (dir in sources.map { it.directory }.distinct()) {
                modelList(dir)?.let { available[dir] = it } // unknown folder: its models aren't reported
            }
            val missing = com.example.comfyui_remote.domain.ModelSources.missing(sources, available).toMutableList()
            val meta = _nodeMetadata.value ?: return@withContext missing
            val inPrompt = com.example.comfyui_remote.domain.ModelSources.missingInPrompt(workflow.jsonContent, meta, overrides)
            var folders: Map<String, List<String>>? = null
            for (model in inPrompt) {
                if (missing.any { it.name == model.name }) continue
                val link = sources.firstOrNull { it.name == model.name }
                // A linked model the server's folder list has is there: /object_info may just be older
                if (link != null && available[link.directory]?.any { it == model.name || it.substringAfterLast('/') == model.name } == true) continue
                val directory = link?.directory ?: model.directory ?: run {
                    if (folders == null) folders = allModelLists()
                    com.example.comfyui_remote.domain.ModelSources.guessFolder(model.options, folders!!)
                } ?: continue // folder unknown: the pre-flight check still reports it
                missing += com.example.comfyui_remote.domain.ModelSource(model.name, link?.url ?: "", directory)
            }
            missing
        }
    }

    private suspend fun modelList(dir: String): List<String>? = modelListCache[dir] ?: try {
        buildApiService().getModels(dir).also { modelListCache[dir] = it }
    } catch (e: Exception) {
        null
    }

    /** Every models folder's file list, for placing inputs whose folder can't be told from their name. */
    private suspend fun allModelLists(): Map<String, List<String>> {
        val names = try {
            buildApiService().getModelFolders()
        } catch (e: Exception) {
            return emptyMap()
        }
        return names.filter { it != "custom_nodes" && it != "configs" }
            .mapNotNull { dir -> modelList(dir)?.let { dir to it } }
            .toMap()
    }

    /** Saves a download link the user gave for a model with this workflow, so the model can be downloaded. */
    suspend fun saveModelLink(workflow: WorkflowEntity, source: com.example.comfyui_remote.domain.ModelSource) {
        if (workflow.id != 0L) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val current = repository.allWorkflows.first().firstOrNull { it.id == workflow.id } ?: return@withContext
                val sources = com.example.comfyui_remote.domain.ModelSources.withSource(
                    com.example.comfyui_remote.domain.ModelSources.fromJson(current.modelSources), source
                )
                repository.insert(current.copy(modelSources = com.example.comfyui_remote.domain.ModelSources.toJson(sources)))
            }
        }
        _modelsVersion.value++
    }

    /** (size in bytes or null, gated) from the server's HEAD request, or null when the probe failed. */
    suspend fun probeModel(source: com.example.comfyui_remote.domain.ModelSource): Pair<Long?, Boolean>? =
        modelDownloadRepository.probe(source)

    fun downloadModel(source: com.example.comfyui_remote.domain.ModelSource) = modelDownloadRepository.download(source)

    fun downloadAllModels(sources: List<com.example.comfyui_remote.domain.ModelSource>) =
        modelDownloadRepository.downloadAll(sources)

    fun cancelModelDownload(download: com.example.comfyui_remote.domain.ModelDownload) =
        modelDownloadRepository.cancel(download)

    /** Seeds sent by the latest run, keyed "nodeId/fieldName" (Phase 101), so the form can offer to pin one. */
    private val _lastUsedSeeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastUsedSeeds: StateFlow<Map<String, Long>> = _lastUsedSeeds.asStateFlow()

    private val _serverWorkflows = MutableStateFlow<List<ServerWorkflowFile>>(emptyList())
    val serverWorkflows: StateFlow<List<ServerWorkflowFile>> = _serverWorkflows.asStateFlow()




    // Template library of the connected server (/templates/index.json)
    private val _templateCategories = MutableStateFlow<List<com.example.comfyui_remote.domain.TemplateCategory>>(emptyList())
    val templateCategories: StateFlow<List<com.example.comfyui_remote.domain.TemplateCategory>> = _templateCategories.asStateFlow()

    private val _templatesLoading = MutableStateFlow(false)
    val templatesLoading: StateFlow<Boolean> = _templatesLoading.asStateFlow()

    private val _templatesError = MutableStateFlow<String?>(null)
    val templatesError: StateFlow<String?> = _templatesError.asStateFlow()

    private val _templateImportError = MutableStateFlow<String?>(null)
    val templateImportError: StateFlow<String?> = _templateImportError.asStateFlow()

    fun clearTemplateImportError() {
        _templateImportError.value = null
    }

    /** Base URL of the connected server, e.g. "http://192.168.1.5:8188". */
    val serverBaseUrl: String
        get() = "${if (_isSecure.value) "https" else "http"}://${_serverAddress.value}"

    fun fetchTemplates(force: Boolean = false) {
        if (_templatesLoading.value || (!force && _templateCategories.value.isNotEmpty())) return
        viewModelScope.launch {
            _templatesLoading.value = true
            _templatesError.value = null
            try {
                val categories = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val body = buildApiService().getTemplateFile(com.example.comfyui_remote.domain.WorkflowTemplateIndex.INDEX_FILE)
                    com.example.comfyui_remote.domain.WorkflowTemplateIndex.parse(body.string())
                }
                _templateCategories.value = categories
                if (categories.isEmpty()) _templatesError.value = "The server has no workflow templates."
            } catch (e: retrofit2.HttpException) {
                android.util.Log.e("TEMPLATES", "Template index HTTP ${e.code()}", e)
                _templatesError.value = if (e.code() == 404) {
                    "This server doesn't provide workflow templates. Update ComfyUI (and its comfyui-workflow-templates package)."
                } else {
                    "Couldn't load templates (HTTP ${e.code()})."
                }
            } catch (e: Exception) {
                android.util.Log.e("TEMPLATES", "Error loading template index", e)
                _templatesError.value = "Couldn't load templates: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _templatesLoading.value = false
            }
        }
    }

    /** Downloads a template's workflow from the server and imports it like any other workflow. */
    fun importTemplate(template: com.example.comfyui_remote.domain.WorkflowTemplate, onSuccess: (WorkflowEntity) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _importStatus.value = "Fetching template..."
            try {
                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    buildApiService().getTemplateFile("${template.name}.json").string()
                }
                importWorkflowInternal(template.title, json, com.example.comfyui_remote.domain.WorkflowSource.SERVER_TEMPLATE, onSuccess)
            } catch (e: Exception) {
                android.util.Log.e("TEMPLATES", "Error importing template ${template.name}", e)
                _templateImportError.value = "Couldn't import '${template.title}': ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _isSyncing.value = false
                _importStatus.value = ""
            }
        }
    }

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
        viewModelScope.launch { userPreferencesRepository.saveAutoConnect(true) }
        fetchAvailableModels()
        fetchNodeMetadata()
        fetchServerWorkflows()
    }
    
    fun disconnect() {
        connectionRepository.disconnect()
        // Phase 102: a deliberate disconnect stops auto-connect on the next start
        viewModelScope.launch { userPreferencesRepository.saveAutoConnect(false) }

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
                    _runningPromptId.value = obj.getAsJsonObject("data")?.get("prompt_id")
                        ?.takeIf { it.isJsonPrimitive }?.asString
                    _executionProgress.value = ExecutionProgress()
                    val wf = _selectedWorkflow.value
                    if (wf != null) ensureNodeTitles(wf)
                    progressTracker.start(if (wf != null) cachedNodeTitles?.size ?: 0 else 0)
                }
                "execution_cached" -> {
                    val data = obj.getAsJsonObject("data")
                    val nodes = data?.getAsJsonArray("nodes")
                    if (nodes != null) {
                        progressTracker.markCached(nodes.map { it.asString })
                        publishProgress()
                    }
                }
                "executing" -> {
                    val data = obj.getAsJsonObject("data")
                    // When node is null, the prompt execution is complete
                    if (data.has("node") && data.get("node").isJsonNull) {
                        progressTracker.finish()
                        _runningPromptId.value = null
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
                        progressTracker.onExecuting(nodeId)
                        _executionProgress.value = _executionProgress.value.copy(
                            currentNodeId = nodeId,
                            currentNodeTitle = title
                        )
                        publishProgress()
                    }
                }
                "progress" -> {
                    val data = obj.getAsJsonObject("data")
                    val value = data.get("value").asInt
                    val max = data.get("max").asInt
                    progressTracker.onStep(value, max)
                    publishProgress()
                }
                "executed" -> {
                    val data = obj.getAsJsonObject("data")
                    val promptId = if (data.has("prompt_id")) data.get("prompt_id").asString else ""
                    syncHistoryItem(promptId, data)
                    _executionStatus.value = ExecutionStatus.FINISHED
                    _executionProgress.value = ExecutionProgress()
                }
                "execution_error" -> {
                    // Which node failed and why (Phase 92)
                    val data = obj.getAsJsonObject("data")
                    if (data != null) {
                        val promptId = data.get("prompt_id")?.takeIf { it.isJsonPrimitive }?.asString
                        val sentPrompt = promptId?.let { _executionCache[it] }?.let {
                            try { com.google.gson.JsonParser.parseString(it).asJsonObject } catch (e: Exception) { null }
                        }
                        _errorMessage.value = com.example.comfyui_remote.domain.ServerErrorReport
                            .fromExecutionError(data, sentPrompt).format()
                    }
                    _executionStatus.value = ExecutionStatus.ERROR
                    _executionProgress.value = ExecutionProgress()
                    _runningPromptId.value = null
                }
                "execution_interrupted" -> _runningPromptId.value = null
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
                        listOf("images", "gifs", "videos").forEach { key ->
                            if (out.has(key)) {
                                val mediaArray = out.getAsJsonArray(key)
                                mediaArray.forEach { mediaElement ->
                                    val mediaObj = mediaElement.asJsonObject
                                    val filename = mediaObj.get("filename").asString

                                    
                                    val subfolder = if (mediaObj.has("subfolder")) mediaObj.get("subfolder").asString else null

                                    val protocol = if (_isSecure.value) "https" else "http"
                                    val url = com.example.comfyui_remote.domain.MediaUrls.view("$protocol://${_serverAddress.value}", filename, subfolder)
                                    _generatedImage.value = url

                                    val hostParts = _serverAddress.value.split(":")
                                    val host = hostParts.getOrNull(0) ?: ""
                                    val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8188

                                    val extension = filename.substringAfterLast('.', "").lowercase()
                                    val isVideo = key != "images" || extension in listOf("mp4", "gif", "webm", "mkv")
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
            // Phase 97: keep the graph's model download links for the models the prompt uses
            val modelSources = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.comfyui_remote.domain.ModelSources.usedBy(
                    com.example.comfyui_remote.domain.ModelSources.extract(json),
                    conversionResult.json
                )
            }
            android.util.Log.d("IMPORT_DEBUG", "Model download links kept: ${modelSources.size}")
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
                missingNodes = if (normalized.missingNodes.isNotEmpty()) normalized.missingNodes.joinToString(", ") else null,
                modelSources = if (modelSources.isNotEmpty()) com.example.comfyui_remote.domain.ModelSources.toJson(modelSources) else null
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

    fun syncHistory(startDate: Long? = null, endDate: Long? = null, maxItemsOverride: Int? = null) {
        viewModelScope.launch {
            android.util.Log.d("SYNC_DEBUG", "syncHistory() called with date range: $startDate - $endDate, override: $maxItemsOverride")
            _isSyncing.value = true
            _syncError.value = null
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
                    
                    // Priority: 1. Override, 2. Date filtering min-count, 3. User preference
                    val userMaxItems = userPreferencesRepository.maxSyncItems.first()
                    val maxItems = when {
                        maxItemsOverride != null -> maxItemsOverride
                        startDate != null || endDate != null -> kotlin.math.max(userMaxItems, 1000)
                        else -> userMaxItems
                    }
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
                                    // android.util.Log.d("SYNC_DEBUG", "Skipping existing item $executionId")
                                    // Skip the value (the JSON object) for this key
                                    r.skipValue()
                                    continue
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
                                                        listOf("images", "gifs", "videos").forEach { key ->
                                                            if (out.has(key)) {
                                                                val mediaArray = out.getAsJsonArray(key)
                                                                mediaArray.forEach { mediaElement ->
                                                                    val mediaObj = mediaElement.asJsonObject
                                                                    val filename = mediaObj.get("filename").asString
                                                                    
                                                                    val subfolder = if (mediaObj.has("subfolder")) mediaObj.get("subfolder").asString else null
                                                                    val serverType = if (mediaObj.has("type")) mediaObj.get("type").asString else "output"

                                                                    val extension = filename.substringAfterLast('.', "").lowercase()
                                                                    val isVideo = key != "images" || extension in listOf("mp4", "gif", "webm", "mkv")
                                                                    val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                                                                    
                                                                    imageCount++
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
                                                                android.util.Log.d("SYNC_DEBUG", "Item $executionId added $imageCount media items to newMediaItems")
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                android.util.Log.w("SYNC_DEBUG", "Item $executionId has outputs but workflowJson is NULL")
                                            }
                                        } else {
                                            android.util.Log.w("SYNC_DEBUG", "Item $executionId: workflowJson is NULL - skipping")
                                        }
                                    }
 else {
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
                    _syncError.value = "Couldn't load history from the server: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }

    fun clearAndRefreshHistory() {
        viewModelScope.launch {
            _isSyncing.value = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    android.util.Log.d("SYNC_DEBUG", "Starting full clear and refresh...")
                    // ComfyUI's history is empty after a server restart: then keep the gallery (Phase 104)
                    val serverHasHistory = try {
                        buildApiService().getHistory(maxItems = 1).use { body ->
                            com.example.comfyui_remote.domain.HistoryCheck.hasEntries(body.charStream())
                        }
                    } catch (e: Exception) {
                        _syncError.value = "Couldn't reach the server, so the gallery was kept: ${e.message ?: e.javaClass.simpleName}"
                        return@withContext
                    }
                    if (!serverHasHistory) {
                        _syncError.value = "The server's history is empty (ComfyUI clears it when it restarts), so the gallery was kept as it is."
                        return@withContext
                    }
                    _syncError.value = null
                    // Removed items keep their rows, so the sync below skips them (Phase 104)
                    mediaRepository.deleteVisible()
                    
                    // Reset UI states related to selection/preview if necessary
                    // _generatedImage.value = null // This might be jarring if currently viewing an image.
                    // But for consistency with "Clear", let's clear it.
                    _generatedImage.value = null
                    _generatedMediaId.value = null
                    
                    android.util.Log.d("SYNC_DEBUG", "Local data cleared. Triggering sync...")
                    // Trigger sync with high limit to ensure we get a fresh view
                    syncHistory(maxItemsOverride = 2000)
                } catch (e: Exception) {
                    android.util.Log.e("SYNC_DEBUG", "Error during clear and refresh: ${e.message}", e)
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

    /**
     * Removes items from the gallery (Phase 104). Their rows are kept hidden, so a sync or "Reload from
     * server" doesn't bring them back; the files on the server are untouched.
     */
    fun removeFromGallery(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { mediaRepository.hide(ids) }
    }

    /** Shows every removed item again. */
    fun restoreRemovedMedia() {
        viewModelScope.launch { mediaRepository.unhideAll() }
    }

    /** How an item was made, for the viewer's Info sheet; empty for uploads without a stored prompt. */
    suspend fun mediaInfo(id: Long): com.example.comfyui_remote.domain.MediaInfo =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.comfyui_remote.domain.MediaInfo.from(
                mediaRepository.getById(id)?.promptJson, workflowParser, _nodeMetadata.value
            )
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
            _hasSavedServer.value = savedHost.isNotBlank()

            // Phase 102: connect to the last server unless the user disconnected. The connection outlives
            // this view model (foreground service), so skip it when one is already up or starting.
            val state = connectionRepository.connectionState.value
            if (savedHost.isNotBlank() && userPreferencesRepository.autoConnect.first() &&
                (state == WebSocketState.DISCONNECTED || state == WebSocketState.ERROR)
            ) {
                connect()
            }
        }.invokeOnCompletion { settingsLoaded.complete(Unit) }

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
                    // The connection outlives this view model (foreground service), so a recreated one sees
                    // CONNECTED at once; wait for the saved server address before calling the server.
                    settingsLoaded.await()
                    syncHistory()
                    // Refresh node metadata/available models on every transition to CONNECTED,
                    // not just the initial explicit connect() call. Without this, a process
                    // restart (e.g. after a crash) that reconnects the WebSocket leaves these
                    // in-memory caches empty/stale — the UI looks connected, but combo-option
                    // dropdowns (checkpoint/model pickers) silently render with no items until
                    // the user manually disconnects and reconnects.
                    fetchNodeMetadata()
                    fetchAvailableModels()
                    // Phase 97: model folders may have changed while disconnected
                    // (ModelDownloadRepository checks the helper itself)
                    modelListCache.clear()
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
    private val localQueueRepository: com.example.comfyui_remote.data.LocalQueueRepository,
    private val savedGalleryListRepository: com.example.comfyui_remote.data.SavedGalleryListRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, mediaRepository, userPreferencesRepository, connectionRepository, localQueueRepository, savedGalleryListRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
