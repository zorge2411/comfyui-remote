package com.example.comfyui_remote.data

import com.example.comfyui_remote.domain.ServerJob
import com.example.comfyui_remote.domain.ServerQueue
import com.example.comfyui_remote.network.ComfyApiService
import com.example.comfyui_remote.network.WebSocketState
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * The ComfyUI server's own queue (Phase 102): what is running and pending, from any client.
 * Lives as long as the app, like [ModelDownloadRepository].
 */
class ServerQueueRepository(
    private val connection: ConnectionRepository,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _jobs = MutableStateFlow<List<ServerJob>>(emptyList())
    val jobs: StateFlow<List<ServerJob>> = _jobs.asStateFlow()

    /** A short message from the last failed refresh or action; cleared by the next success. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** prompt_id -> workflow name for prompts this app sent; the server's entry may not carry a name. */
    private val knownNames = object : LinkedHashMap<String, String>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_NAMES
    }

    private var refreshJob: Job? = null
    private var pollJob: Job? = null

    init {
        scope.launch {
            connection.connectionState.collect { state ->
                when (state) {
                    WebSocketState.CONNECTED -> refresh()
                    WebSocketState.DISCONNECTED -> {
                        _jobs.value = emptyList()
                        _lastError.value = null
                    }
                    else -> {}
                }
            }
        }
        scope.launch {
            connection.messages.collect { message ->
                if (QUEUE_EVENTS.any { message.contains("\"$it\"") } && typeOf(message) in QUEUE_EVENTS) {
                    refreshSoon()
                }
            }
        }
    }

    fun rememberName(promptId: String, name: String) {
        synchronized(knownNames) { knownNames[promptId] = name }
    }

    private fun api(): ComfyApiService? {
        val base = connection.baseUrl ?: return null
        return try {
            retrofit2.Retrofit.Builder()
                .baseUrl(base)
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(ComfyApiService::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    /** Websocket events come in bursts; one refresh 300 ms after the last. */
    private fun refreshSoon() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(300)
            refreshNow()
        }
    }

    private suspend fun refreshNow() {
        if (connection.connectionState.value != WebSocketState.CONNECTED) return
        val api = api() ?: return
        try {
            val names = synchronized(knownNames) { HashMap(knownNames) }
            _jobs.value = ServerQueue.parse(api.getQueue(), names)
            _lastError.value = null
        } catch (e: Exception) {
            android.util.Log.w("SERVER_QUEUE", "Refresh failed: ${e.message}")
            _lastError.value = "Couldn't load the server queue: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Refreshes every 3 s while the Queue screen is visible. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                refreshNow()
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Removes a pending job from the server's queue. */
    fun cancel(job: ServerJob) {
        val body = JsonObject().apply { add("delete", JsonArray().apply { add(job.promptId) }) }
        action("Cancel") { it.editQueue(body) }
    }

    /** Stops the running job; the server ignores it when that prompt is no longer running. */
    fun interrupt(job: ServerJob) {
        val body = JsonObject().apply { addProperty("prompt_id", job.promptId) }
        action("Stop") { it.interrupt(body) }
    }

    /** Removes every pending job; the running one continues. */
    fun clearPending() {
        val body = JsonObject().apply { addProperty("clear", true) }
        action("Clear") { it.editQueue(body) }
    }

    private fun action(name: String, call: suspend (ComfyApiService) -> Any?) {
        scope.launch {
            try {
                val api = api() ?: throw IllegalStateException("Not connected")
                call(api)
                refreshNow()
            } catch (e: Exception) {
                _lastError.value = "$name failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun typeOf(message: String): String? = try {
        JsonParser.parseString(message).asJsonObject.get("type")?.asString
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_NAMES = 200
        val QUEUE_EVENTS = setOf(
            "status", "execution_start", "executing", "execution_success", "execution_error", "execution_interrupted"
        )
    }
}
