package com.example.comfyui_remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.comfyui_remote.data.LocalQueueItem
import com.example.comfyui_remote.data.LocalQueueRepository
import com.example.comfyui_remote.data.QueueStatus
import com.example.comfyui_remote.domain.WorkflowExecutionService
import com.example.comfyui_remote.domain.InputField
import com.example.comfyui_remote.network.ComfyApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.lang.reflect.Type

class QueueViewModel(
    application: Application,
    private val localQueueRepository: LocalQueueRepository,
    private val connectionRepository: com.example.comfyui_remote.data.ConnectionRepository,
    private val userPreferencesRepository: com.example.comfyui_remote.data.UserPreferencesRepository,
    private val workflowExecutionService: WorkflowExecutionService
) : AndroidViewModel(application) {

    // Queue Items Flow
    val queueItems = localQueueRepository.allQueueItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Execution State
    private val _isQueueRunning = MutableStateFlow(false)
    val isQueueRunning: StateFlow<Boolean> = _isQueueRunning.asStateFlow()

    private val _currentExecutingItemId = MutableStateFlow<Long?>(null)
    val currentExecutingItemId: StateFlow<Long?> = _currentExecutingItemId.asStateFlow()

    private var executionJob: Job? = null

    private val okHttpClient: OkHttpClient
        get() = (getApplication<Application>() as ComfyApplication).okHttpClient

    // Custom Gson for InputField Polymorphism
    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(InputField::class.java, InputFieldDeserializer())
            .create()
    }

    private suspend fun buildApiService(): ComfyApiService? {
        val host = userPreferencesRepository.savedHost.firstOrNull() ?: return null
        val port = userPreferencesRepository.savedPort.firstOrNull() ?: 8188
        val secure = userPreferencesRepository.isSecure.firstOrNull() ?: false
        val protocol = if (secure) "https" else "http"
        
        return try {
             retrofit2.Retrofit.Builder()
                .baseUrl("$protocol://$host:$port/")
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(ComfyApiService::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun startQueue() {
        if (_isQueueRunning.value) return
        _isQueueRunning.value = true
        
        executionJob = viewModelScope.launch {
            while (isActive && _isQueueRunning.value) {
                val pendingItems = localQueueRepository.getPendingItems()
                if (pendingItems.isEmpty()) {
                    _isQueueRunning.value = false
                    break
                }

                val item = pendingItems.first()
                processQueueItem(item)
                
                delay(1000)
            }
        }
    }

    private suspend fun processQueueItem(item: LocalQueueItem) {
        _currentExecutingItemId.value = item.id
        localQueueRepository.updateStatus(item.id, QueueStatus.EXECUTING)

        try {
            val api = buildApiService()
            if (api == null) {
                localQueueRepository.updateStatus(item.id, QueueStatus.FAILED)
                _isQueueRunning.value = false
                return
            }

            // Parse Inputs using Custom Gson
            val inputs = try {
                 val type = object : TypeToken<List<InputField>>() {}.type
                 gson.fromJson<List<InputField>>(item.inputValuesJson, type)
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Failed to parse inputs: ${e.message}")
            }

            // Handle Image Uploads
            val imagesToUpload = inputs
                .filterIsInstance<InputField.ImageInput>()
                .filter { !it.localUri.isNullOrBlank() && it.value.isNullOrBlank() }
                .associate { it.nodeId to (it.localUri ?: "") }

            val newlyUploaded = if (imagesToUpload.isNotEmpty()) {
                workflowExecutionService.uploadImages(api, imagesToUpload, getApplication<Application>().contentResolver)
            } else {
                emptyMap()
            }

            // Update inputs with newly uploaded filenames
            val finalInputs = inputs.map { input ->
                if (input is InputField.ImageInput && newlyUploaded.containsKey(input.nodeId)) {
                    input.copy(value = newlyUploaded[input.nodeId])
                } else {
                    input
                }
            }

            // Loop Batch Count
            repeat(item.batchCount) {
                 val runInputs = finalInputs.map { field ->
                    if (field is InputField.SeedInput) {
                        field.copy(value = kotlin.random.Random.nextLong(1, Long.MAX_VALUE))
                    } else {
                        field
                    }
                }

                workflowExecutionService.prepareAndQueue(
                    api = api,
                    clientId = connectionRepository.clientId ?: "",
                    workflowJson = item.workflowJson,
                    uploadedFilenames = newlyUploaded,
                    inputs = runInputs
                )
            }
            
            localQueueRepository.updateStatus(item.id, QueueStatus.COMPLETED)

        } catch (e: Exception) {
            e.printStackTrace()
            localQueueRepository.updateStatus(item.id, QueueStatus.FAILED)
        } finally {
            _currentExecutingItemId.value = null
        }
    }

    fun stopQueue() {
        _isQueueRunning.value = false
        executionJob?.cancel()
        executionJob = null
    }

    fun deleteItem(item: LocalQueueItem) {
        viewModelScope.launch {
            localQueueRepository.delete(item)
        }
    }
    
    fun clearCompleted() {
        viewModelScope.launch {
            localQueueRepository.clearCompleted()
        }
    }
    
    fun addToQueue(workflowId: Long, workflowName: String, workflowJson: String, inputs: List<InputField>, batchCount: Int) {
        viewModelScope.launch {
            val inputsJson = gson.toJson(inputs)
            localQueueRepository.addToQueue(workflowId, workflowName, workflowJson, inputsJson, batchCount)
        }
    }
}

class InputFieldDeserializer : JsonDeserializer<InputField> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): InputField {
        val jsonObject = json.asJsonObject
        val label = jsonObject.get("label")?.asString ?: throw JsonParseException("Missing label")

        return when (label) {
            "Text" -> context.deserialize(jsonObject, InputField.StringInput::class.java)
            "Number" -> context.deserialize(jsonObject, InputField.IntInput::class.java)
            "Seed" -> context.deserialize(jsonObject, InputField.SeedInput::class.java)
            "Number (Float)" -> context.deserialize(jsonObject, InputField.FloatInput::class.java)
            "Model" -> context.deserialize(jsonObject, InputField.ModelInput::class.java)
            "Selection" -> context.deserialize(jsonObject, InputField.SelectionInput::class.java)
            "Image" -> context.deserialize(jsonObject, InputField.ImageInput::class.java)
            else -> throw JsonParseException("Unknown InputField label: $label")
        }
    }
}

class QueueViewModelFactory(
    private val application: Application,
    private val localQueueRepository: LocalQueueRepository,
    private val connectionRepository: com.example.comfyui_remote.data.ConnectionRepository,
    private val userPreferencesRepository: com.example.comfyui_remote.data.UserPreferencesRepository,
    private val workflowExecutionService: WorkflowExecutionService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QueueViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QueueViewModel(application, localQueueRepository, connectionRepository, userPreferencesRepository, workflowExecutionService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
