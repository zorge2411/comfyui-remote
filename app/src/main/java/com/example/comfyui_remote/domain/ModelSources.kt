package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/** A model file a workflow needs, with where to download it and which server models folder it belongs in. */
data class ModelSource(val name: String, val url: String, val directory: String) {
    /** False for a model found missing in the prompt that the workflow carries no download link for. */
    val hasLink: Boolean get() = url.isNotBlank()
}

/** A model file the prompt names that the server doesn't offer; [directory] is null when the input's folder is unknown. */
data class PromptModel(val name: String, val directory: String?, val options: List<String>)

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

    private val MODEL_FILE = Regex("""\.(safetensors|sft|gguf|ckpt|pt|pth|bin)$""", RegexOption.IGNORE_CASE)

    /**
     * Model files the API prompt names that the server's /object_info lists don't offer (a workflow saved
     * elsewhere usually carries no download links, so this finds what the links can't). [overrides] holds
     * the form's current values, keyed "nodeId/fieldName".
     */
    fun missingInPrompt(promptJson: String, objectInfo: JsonObject, overrides: Map<String, String> = emptyMap()): List<PromptModel> {
        val prompt = try {
            JsonParser.parseString(promptJson).asJsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        val found = LinkedHashMap<String, PromptModel>()
        for ((id, el) in prompt.entrySet()) {
            val node = el as? JsonObject ?: continue
            val classType = node.string("class_type") ?: continue
            val input = (objectInfo.get(classType) as? JsonObject)?.get("input") as? JsonObject ?: continue
            val inputs = node.get("inputs") as? JsonObject ?: continue
            for ((key, v) in inputs.entrySet()) {
                val value = overrides["$id/$key"]
                    ?: v.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?: continue
                if (!MODEL_FILE.containsMatchIn(value)) continue
                val spec = ((input.get("required") as? JsonObject)?.get(key)
                    ?: (input.get("optional") as? JsonObject)?.get(key)) as? JsonArray ?: continue
                val options = GraphToApiConverter.comboOptions(spec) ?: continue
                val name = baseName(value)
                if (value in options || options.any { baseName(it) == name }) continue
                found.putIfAbsent(name, PromptModel(name, folderFor(classType, key), options))
            }
        }
        return found.values.toList()
    }

    /** The models folder a loader input reads, from its name; null when it can't be told. */
    fun folderFor(classType: String, input: String): String? {
        val cls = classType.lowercase()
        return when {
            cls.contains("upscalemodelloader") && input == "model_name" ->
                if (cls.contains("latent")) "latent_upscale_models" else "upscale_models"
            cls.contains("clipvision") -> "clip_vision"
            cls.contains("modelpatchloader") -> "model_patches"
            input.startsWith("ckpt_name") -> "checkpoints"
            input == "unet_name" -> "diffusion_models"
            input == "model_name" && (cls.contains("diffusionmodel") || cls.contains("unet")) -> "diffusion_models"
            input.startsWith("clip_name") || input == "text_encoder" -> "text_encoders"
            input.startsWith("vae_name") || input.endsWith("_vae") -> "vae"
            input.startsWith("lora") -> "loras"
            input == "control_net_name" -> "controlnet"
            input == "style_model_name" -> "style_models"
            input == "gligen_name" -> "gligen"
            input == "hypernetwork_name" -> "hypernetworks"
            input == "audio_encoder_name" -> "audio_encoders"
            else -> null
        }
    }

    /** The server folder whose files overlap the input's options most, for inputs [folderFor] can't place. */
    fun guessFolder(options: List<String>, folders: Map<String, List<String>>): String? {
        if (options.isEmpty()) return null
        val wanted = options.toHashSet()
        return folders.entries
            .map { (folder, files) -> folder to files.count { it in wanted } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * A link pasted by the user, as a direct download URL (a Hugging Face or GitHub page link becomes its
     * file link, a Civitai model page with ?modelVersionId= its download link), or null when the server's
     * helper wouldn't accept it (https on huggingface.co, github.com or civitai.com).
     */
    fun normalizeUrl(text: String): String? {
        val url = text.trim()
        val uri = try {
            java.net.URI(url)
        } catch (e: Exception) {
            return null
        }
        if (uri.scheme != "https") return null
        return when (uri.host?.lowercase()) {
            "huggingface.co" -> url.replaceFirst(Regex("""^(https://huggingface\.co/.+?)/blob/"""), "$1/resolve/")
            "github.com" -> url.replaceFirst(Regex("""^(https://github\.com/[^/]+/[^/]+)/blob/"""), "$1/raw/")
            "civitai.com" -> when {
                uri.path.orEmpty().matches(Regex("""/api/download/models/\d+/?""")) -> url
                uri.path.orEmpty().startsWith("/models/") ->
                    Regex("""(?:^|&)modelVersionId=(\d+)""").find(uri.rawQuery.orEmpty())
                        ?.let { "https://civitai.com/api/download/models/${it.groupValues[1]}" }
                else -> null
            }
            else -> null
        }
    }

    /** True for a Civitai link, which needs the server's CIVITAI_TOKEN rather than HF_TOKEN when refused. */
    fun isCivitai(url: String): Boolean =
        try {
            java.net.URI(url).host?.lowercase() == "civitai.com"
        } catch (e: Exception) {
            false
        }

    /** [sources] with [source] added, replacing any link for a model of the same name. */
    fun withSource(sources: List<ModelSource>, source: ModelSource): List<ModelSource> =
        sources.filter { it.name != source.name } + source

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
