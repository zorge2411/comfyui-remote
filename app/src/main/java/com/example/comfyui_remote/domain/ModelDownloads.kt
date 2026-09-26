package com.example.comfyui_remote.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** A model download on the server's comfyui_remote_helper queue (Phases 97, 99). */
data class ModelDownload(
    val id: String?,
    val filename: String,
    val directory: String,
    val total: Long?,
    val done: Long,
    val status: String, // queued, downloading, done, error, cancelled
    val error: String?,
    val position: Int? = null // 1-based place in the queue while queued (extension v2)
) {
    val key get() = "$directory/$filename"
    val active get() = status == "queued" || status == "downloading"
    val finished get() = status == "done" || status == "error" || status == "cancelled"
    val progress: Float? get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
}

object ModelDownloads {

    data class Summary(val title: String, val text: String, val percent: Int?)

    fun parse(o: JsonObject): ModelDownload {
        fun str(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asString
        return ModelDownload(
            id = str("id"),
            filename = str("filename") ?: "",
            directory = str("directory") ?: "",
            total = o.get("total")?.takeIf { !it.isJsonNull }?.asLong,
            done = o.get("done")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            status = str("status") ?: "error",
            error = str("error"),
            position = o.get("position")?.takeIf { !it.isJsonNull }?.asInt
        )
    }

    /** Accepts the list route's array or the `remote_helper.queue` event's `{"jobs": [...]}`. */
    fun parseList(element: JsonElement?): List<ModelDownload> {
        val array: JsonArray = when {
            element == null || element.isJsonNull -> return emptyList()
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> element.asJsonObject.getAsJsonArray("jobs") ?: return emptyList()
            else -> return emptyList()
        }
        return array.filter { it.isJsonObject }.map { parse(it.asJsonObject) }
    }

    /** Replaces the job with the same id (or the not-yet-confirmed row for the same file), or appends it. */
    fun upsert(list: List<ModelDownload>, job: ModelDownload): List<ModelDownload> {
        val index = list.indexOfFirst { (job.id != null && it.id == job.id) || (it.id == null && it.key == job.key) }
        return if (index >= 0) list.toMutableList().also { it[index] = job } else list + job
    }

    /**
     * Notification text while downloads are active, or null when none are. [finishedInBatch] counts the jobs
     * that finished since the queue was last idle, so the count reads "2 of 5" across the whole batch.
     */
    fun summary(list: List<ModelDownload>, finishedInBatch: Int): Summary? {
        val active = list.filter { it.active }
        if (active.isEmpty()) return null
        val current = active.firstOrNull { it.status == "downloading" } ?: active.first()
        val percent = current.progress?.let { (it * 100).toInt() }
        val total = finishedInBatch + active.size
        val index = finishedInBatch + 1
        return Summary(
            title = "Downloading $index of $total",
            text = current.filename + (percent?.let { ", $it%" } ?: ""),
            percent = percent
        )
    }

    /** "4 models downloaded, 1 failed, 1 cancelled", for the jobs of one batch. */
    fun finishedSummary(batch: List<ModelDownload>): String {
        val done = batch.count { it.status == "done" }
        val failed = batch.count { it.status == "error" }
        val cancelled = batch.count { it.status == "cancelled" }
        return listOfNotNull(
            "$done model${if (done == 1) "" else "s"} downloaded",
            failed.takeIf { it > 0 }?.let { "$it failed" },
            cancelled.takeIf { it > 0 }?.let { "$it cancelled" }
        ).joinToString(", ")
    }
}
