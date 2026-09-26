package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/** A model file a workflow needs, with where to download it and which server models folder it belongs in. */
data class ModelSource(val name: String, val url: String, val directory: String)

/**
 * Model download links saved in graph workflows (Phase 97). The ComfyUI frontend stores them as
 * `properties.models: [{name, url, directory}]` on nodes, on nodes inside subgraph definitions, and in a
 * top-level `models` list; the converted API prompt loses them, so they are read from the graph at import.
 */
object ModelSources {

    private val gson = Gson()

    fun extract(graphJson: String): List<ModelSource> {
        val graph = try {
            JsonParser.parseString(graphJson).asJsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        val found = LinkedHashMap<String, ModelSource>()
        fun addAll(models: JsonElement?) {
            if (models == null || !models.isJsonArray) return
            for (entry in models.asJsonArray) {
                val m = entry as? JsonObject ?: continue
                val name = m.string("name") ?: continue
                val url = m.string("url") ?: continue
                val directory = m.string("directory") ?: continue
                found.putIfAbsent("$directory/$name", ModelSource(name, url, directory))
            }
        }
        fun addNodes(nodes: JsonElement?) {
            if (nodes == null || !nodes.isJsonArray) return
            for (node in nodes.asJsonArray) {
                val properties = (node as? JsonObject)?.get("properties") as? JsonObject ?: continue
                addAll(properties.get("models"))
            }
        }
        addNodes(graph.get("nodes"))
        ((graph.get("definitions") as? JsonObject)?.get("subgraphs") as? JsonArray)?.forEach { sg ->
            addNodes((sg as? JsonObject)?.get("nodes"))
        }
        addAll(graph.get("models"))
        return found.values.toList()
    }

    /** Keeps the sources the API prompt actually uses, so models listed on muted or removed nodes aren't offered. */
    fun usedBy(sources: List<ModelSource>, promptJson: String): List<ModelSource> {
        val values = HashSet<String>()
        try {
            JsonParser.parseString(promptJson).asJsonObject.entrySet().forEach { (_, node) ->
                val inputs = (node as? JsonObject)?.get("inputs") as? JsonObject ?: return@forEach
                inputs.entrySet().forEach { (_, v) ->
                    if (v.isJsonPrimitive && v.asJsonPrimitive.isString) values.add(baseName(v.asString))
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return sources.filter { it.name in values }
    }

    /**
     * Sources not on the server. [available] maps a models folder to the server's file list for it; a folder
     * without a list is unknown and its sources are not reported. Server lists can include subfolders.
     */
    fun missing(sources: List<ModelSource>, available: Map<String, List<String>>): List<ModelSource> =
        sources.filter { source ->
            val files = available[source.directory] ?: return@filter false
            files.none { it == source.name || baseName(it) == source.name }
        }

    fun toJson(sources: List<ModelSource>): String = gson.toJson(sources)

    fun fromJson(text: String?): List<ModelSource> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<ModelSource>>(text, object : TypeToken<List<ModelSource>>() {}.type)
                ?.filter { it.name.isNotBlank() && it.url.isNotBlank() && it.directory.isNotBlank() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

    private fun JsonObject.string(key: String): String? {
        val v = get(key) ?: return null
        if (!v.isJsonPrimitive || !v.asJsonPrimitive.isString) return null
        return v.asString.takeIf { it.isNotBlank() }
    }
}
