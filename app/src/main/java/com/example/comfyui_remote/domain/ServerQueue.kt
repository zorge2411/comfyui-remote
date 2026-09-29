package com.example.comfyui_remote.domain

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** One job in the ComfyUI server's own queue (Phase 102). */
data class ServerJob(
    val number: Long,
    val promptId: String,
    val workflowName: String?,
    val running: Boolean
)

/**
 * Reads a `GET /queue` response: `{"queue_running": [...], "queue_pending": [...]}`, where each entry is
 * `[number, prompt_id, prompt, extra_data, outputs_to_execute]`.
 */
object ServerQueue {

    /** Running jobs first, then pending jobs in queue order. Malformed entries are skipped. */
    fun parse(json: JsonObject, knownNames: Map<String, String> = emptyMap()): List<ServerJob> {
        val running = entries(json, "queue_running").mapNotNull { job(it, knownNames, running = true) }
        val pending = entries(json, "queue_pending").mapNotNull { job(it, knownNames, running = false) }
        return running.sortedBy { it.number } + pending.sortedBy { it.number }
    }

    private fun entries(json: JsonObject, key: String): List<JsonArray> {
        val list = json.get(key)
        if (list == null || !list.isJsonArray) return emptyList()
        return list.asJsonArray.filter { it.isJsonArray }.map { it.asJsonArray }
    }

    private fun job(entry: JsonArray, knownNames: Map<String, String>, running: Boolean): ServerJob? = try {
        val idElement = entry.get(1)
        if (!idElement.isJsonPrimitive || !idElement.asJsonPrimitive.isString) {
            null
        } else {
            val promptId = idElement.asString
            val number = entry.get(0).takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
            ServerJob(number, promptId, nameFrom(entry) ?: knownNames[promptId], running)
        }
    } catch (e: Exception) {
        null
    }

    /** `extra_data.extra_pnginfo.workflow.extra.name`, as the history sync reads it. */
    private fun nameFrom(entry: JsonArray): String? = try {
        if (entry.size() < 4 || !entry.get(3).isJsonObject) {
            null
        } else {
            entry.get(3).asJsonObject
                .getAsJsonObject("extra_pnginfo")
                ?.getAsJsonObject("workflow")
                ?.getAsJsonObject("extra")
                ?.get("name")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }
}
