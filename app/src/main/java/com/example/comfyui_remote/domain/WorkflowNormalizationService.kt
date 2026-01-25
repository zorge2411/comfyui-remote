package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

class WorkflowNormalizationService(private val workflowParser: WorkflowParser) {

    fun normalize(name: String, rawJson: String, source: WorkflowSource): NormalizedWorkflow {
        val jsonObject = try {
            JsonParser.parseString(rawJson).asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }

        // 1. Strip UI-specific weight/metadata from Workspace format if present
        // ComfyUI Web workspaces often have keys like "extra_data", "nodes", "links", "groups", "config"
        // But the API format (which we use for execution) is a flat map of nodes.
        // We want to preserve the "API" part but maybe strip other bulk.
        
        val normalizedJson = if (jsonObject.has("nodes") && jsonObject.has("links")) {
            // This is "Workspace" format. We might want to keep it as is if we eventually support a graph UI,
            // but for Dynamic Form, we usually prefer the API format.
            // However, the current app expects API format.
            // For now, we store whatever we get, but we'll extract models from it.
            rawJson
        } else {
            rawJson
        }

        // 2. Extract Base Models
        val baseModels = extractModels(rawJson)

        return NormalizedWorkflow(
            name = name,
            jsonContent = normalizedJson,
            source = source,
            baseModels = baseModels
        )
    }

    private fun extractModels(json: String): List<String> {
        val models = mutableSetOf<String>()
        try {
            val jsonObject = JsonParser.parseString(json).asJsonObject
            jsonObject.entrySet().forEach { (_, element) ->
                if (element.isJsonObject) {
                    val node = element.asJsonObject
                    val inputs = node.getAsJsonObject("inputs") ?: return@forEach
                    inputs.entrySet().forEach { (field, value) ->
                        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                            val strValue = value.asString
                            if (field.lowercase(Locale.ROOT).contains("ckpt_name") || 
                                field.lowercase(Locale.ROOT) == "model" ||
                                strValue.endsWith(".safetensors") || 
                                strValue.endsWith(".ckpt")) {
                                models.add(strValue)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fail silently, just return empty list
        }
        return models.toList()
    }
}
