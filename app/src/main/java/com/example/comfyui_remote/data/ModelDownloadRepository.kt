package com.example.comfyui_remote.data

import com.example.comfyui_remote.domain.ModelDownload
import com.example.comfyui_remote.domain.ModelDownloads
import com.example.comfyui_remote.domain.ModelSource
import com.example.comfyui_remote.network.ComfyApiService
import com.example.comfyui_remote.network.WebSocketState
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * The server's model download queue (comfyui_remote_helper, Phases 97 and 99), shared by the workflow screen,
 * the Queue screen and the foreground service. Lives as long as the app, like [ConnectionRepository].
 */
class ModelDownloadRepository(
    private val connection: ConnectionRepository,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listLock = Mutex()

    /** null until checked after connecting; false when the server doesn't have the extension. */
    private val _helperAvailable = MutableStateFlow<Boolean?>(null)
    val helperAvailable: StateFlow<Boolean?> = _helperAvailable.asStateFlow()

    /** The extension's version: 0 while unknown; 2 adds move, retry and clear. */
    private val _helperVersion = MutableStateFlow(0)
    val helperVersion: StateFlow<Int> = _helperVersion.asStateFlow()

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    /** Ordered as the server lists them: downloading, queued by position, then finished. */
    private val _downloads = MutableStateFlow<List<ModelDownload>>(emptyList())
    val downloads: StateFlow<List<ModelDownload>> = _downloads.asStateFlow()

    /** A job that just finished downloading, so screens can refresh the server's model lists. */
    private val _finished = MutableSharedFlow<ModelDownload>(extraBufferCapacity = 16)
    val finished: SharedFlow<ModelDownload> = _finished.asSharedFlow()

    /** Readable errors from queue actions (move, retry, clear, cancel). */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val supportsQueueControl: Boolean get() = _helperVersion.value >= 2

    init {
        scope.launch {
            connection.connectionState.collect { if (it == WebSocketState.CONNECTED) refreshNow() }
        }
        scope.launch {
            connection.messages.collect { handleMessage(it) }
        }
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

    private suspend fun refreshNow() {
        val api = api() ?: return
        try {
            val info = api.getHelperInfo()
            _hasToken.value = info.get("hf_token")?.asBoolean == true
            _helperVersion.value = info.get("version")?.asString?.toIntOrNull() ?: 1
            _helperAvailable.value = true
            replace(ModelDownloads.parseList(api.getModelDownloads()))
        } catch (e: retrofit2.HttpException) {
            // 404: the server doesn't have the extension; other codes say nothing about it
            if (e.code() == 404) {
                _helperAvailable.value = false
                _helperVersion.value = 0
            }
            android.util.Log.w("MODEL_DOWNLOAD", "Helper check: HTTP ${e.code()}")
        } catch (e: Exception) {
            // Network or address problem: keep what we knew
            android.util.Log.w("MODEL_DOWNLOAD", "Helper check failed: ${e.message}")
        }
    }

    private suspend fun handleMessage(json: String) {
        if (!json.contains("remote_helper.")) return
        try {
            val obj = JsonParser.parseString(json).asJsonObject
            when (obj.get("type")?.asString) {
                "remote_helper.queue" -> replace(ModelDownloads.parseList(obj.get("data")))
                "remote_helper.download" -> update(ModelDownloads.parse(obj.getAsJsonObject("data")))
            }
        } catch (e: Exception) {
            android.util.Log.w("MODEL_DOWNLOAD", "Bad helper message: ${e.message}")
        }
    }

    private suspend fun replace(list: List<ModelDownload>) = listLock.withLock {
        // Keep rows the server doesn't know: requests in flight, and requests it refused (they carry the error)
        val pending = _downloads.value.filter { p -> p.id == null && list.none { it.key == p.key } }
        announceFinished(_downloads.value, list)
        _downloads.value = list + pending
    }

    private suspend fun update(job: ModelDownload) = listLock.withLock {
        val next = ModelDownloads.upsert(_downloads.value, job)
        announceFinished(_downloads.value, next)
        _downloads.value = next
    }

    private fun announceFinished(before: List<ModelDownload>, after: List<ModelDownload>) {
        val wasDone = before.filter { it.status == "done" }.mapNotNull { it.id }.toSet()
        after.filter { it.status == "done" && it.id != null && it.id !in wasDone }.forEach { _finished.tryEmit(it) }
    }

    /** (size in bytes or null, gated) from the server's HEAD request, or null when the probe failed. */
    suspend fun probe(source: ModelSource): Pair<Long?, Boolean>? = try {
        val body = JsonObject().apply { addProperty("url", source.url) }
        val r = api()?.probeModel(body) ?: throw IllegalStateException("Not connected")
        Pair(r.get("size")?.takeIf { !it.isJsonNull }?.asLong, r.get("gated")?.asBoolean == true)
    } catch (e: Exception) {
        null
    }

    fun download(source: ModelSource) {
        scope.launch { downloadNow(source) }
    }

    /** Queues the sources in order, one request at a time, skipping files that already have an active download. */
    fun downloadAll(sources: List<ModelSource>) {
        scope.launch {
            for (source in sources) {
                if (_downloads.value.any { it.key == "${source.directory}/${source.name}" && it.active }) continue
                downloadNow(source)
            }
        }
    }

    private suspend fun downloadNow(source: ModelSource) {
        val pending = ModelDownload(null, source.name, source.directory, null, 0, "queued", null)
        update(pending)
        try {
            val body = JsonObject().apply {
                addProperty("url", source.url)
                addProperty("directory", source.directory)
                addProperty("filename", source.name)
            }
            val api = api() ?: throw IllegalStateException("Not connected")
            val response = api.startModelDownload(body)
            val job = response.body()
            if (response.isSuccessful && job != null) {
                // A websocket event may already have moved the job on; keep the newer state
                val current = _downloads.value.firstOrNull { it.key == pending.key && it.active }
                if (current == null || current.id == null) update(ModelDownloads.parse(job))
            } else {
                update(pending.copy(status = "error", error = errorOf(response)))
            }
        } catch (e: Exception) {
            update(pending.copy(status = "error", error = e.message ?: e.javaClass.simpleName))
        }
    }

    fun cancel(download: ModelDownload) {
        val id = download.id ?: return
        action("Cancel") { it.cancelModelDownload(id) }
    }

    fun move(download: ModelDownload, position: Int) {
        val id = download.id ?: return
        if (!supportsQueueControl) return
        val body = JsonObject().apply { addProperty("position", position) }
        action("Move") { api -> api.moveModelDownload(id, body).also { check(it) } }
    }

    fun retry(download: ModelDownload) {
        val id = download.id ?: return
        if (!supportsQueueControl) return
        action("Retry") { api -> api.retryModelDownload(id).also { check(it) } }
    }

    fun clearFinished() {
        if (!supportsQueueControl) return
        action("Clear") { api -> api.clearModelDownloads().also { check(it) } }
    }

    /** Runs a queue action; the server's `remote_helper.queue` event brings the new list, refresh covers v1. */
    private fun action(name: String, call: suspend (ComfyApiService) -> Any?) {
        scope.launch {
            try {
                val api = api() ?: throw IllegalStateException("Not connected")
                call(api)
                if (!supportsQueueControl) replace(ModelDownloads.parseList(api.getModelDownloads()))
            } catch (e: Exception) {
                _errors.tryEmit("$name failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun check(response: retrofit2.Response<*>) {
        if (!response.isSuccessful) throw IllegalStateException(errorOf(response))
    }

    private fun errorOf(response: retrofit2.Response<*>): String = try {
        JsonParser.parseString(response.errorBody()?.string()).asJsonObject.get("error").asString
    } catch (e: Exception) {
        "HTTP ${response.code()}"
    }
}
