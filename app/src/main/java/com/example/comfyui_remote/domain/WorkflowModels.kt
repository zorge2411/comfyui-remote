package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A model file a workflow loads: its models folder when known, and its name without path or extension. */
data class ModelRef(val folder: String?, val baseName: String)

/** The models a workflow uses, for the workflow cards and search (Phase 103). */
object WorkflowModels {

    private val MODEL_FILE = Regex("""\.(safetensors|sft|gguf|ckpt|pt|pth|bin)$""", RegexOption.IGNORE_CASE)

    /** Model files named by the API prompt's string inputs, in prompt order, without duplicates. */
    fun of(promptJson: String): List<ModelRef> {
        val prompt = try {
            JsonParser.parseString(promptJson).asJsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        val found = LinkedHashMap<String, ModelRef>()
        for ((_, element) in prompt.entrySet()) {
            val node = element as? JsonObject ?: continue
            val classType = node.get("class_type")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val inputs = node.get("inputs") as? JsonObject ?: continue
            for ((key, value) in inputs.entrySet()) {
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) continue
                val file = value.asString
                if (!MODEL_FILE.containsMatchIn(file)) continue
                val base = file.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                found.putIfAbsent(base, ModelRef(ModelSources.folderFor(classType, key), base))
            }
        }
        return found.values.toList()
    }
}
