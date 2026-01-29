package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Gson
import java.util.Locale

class WorkflowNormalizationService(private val workflowParser: WorkflowParser) {

    private val gson = Gson()

    fun normalize(
        name: String, 
        rawJson: String, 
        source: WorkflowSource,
        objectInfo: JsonObject? = null,
        existingMissingNodes: List<String> = emptyList()
    ): NormalizedWorkflow {
        var processedJson = try {
            JsonParser.parseString(rawJson).asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }

        var missingNodes = existingMissingNodes.toMutableList()

        // 1. Convert Workspace Format to API Format if needed
        if (processedJson.has("nodes") && processedJson.has("links") && objectInfo != null) {
            try {
                val conversionResult = GraphToApiConverter.convert(
                    rawJson, 
                    ComfyObjectInfo(objectInfo)
                )
                processedJson = JsonParser.parseString(conversionResult.json).asJsonObject
                missingNodes.addAll(conversionResult.missingNodes)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to original if conversion fails
            }
        }

        // 2. Strip UI-specific metadata and clean node structure
        val cleanedJson = stripUiMetadata(processedJson)
        val cleanedJsonString = gson.toJson(cleanedJson)

        // 3. Extract Base Models
        val baseModels = extractModels(cleanedJsonString)

        return NormalizedWorkflow(
            name = name,
            jsonContent = cleanedJsonString,
            source = source,
            baseModels = baseModels,
            missingNodes = missingNodes
        )
    }

    private fun stripUiMetadata(original: JsonObject): JsonObject {
        val cleaned = JsonObject()
        original.entrySet().forEach { (nodeId, element) ->
            if (element.isJsonObject) {
                val node = element.asJsonObject
                val cleanedNode = JsonObject()

                // Preserve only essential keys for API execution
                if (node.has("class_type")) {
                    cleanedNode.add("class_type", node.get("class_type"))
                }
                
                if (node.has("inputs")) {
                    cleanedNode.add("inputs", node.get("inputs"))
                }

                // Preserve and clean _meta (titles are useful for our UI)
                if (node.has("_meta")) {
                    val meta = node.getAsJsonObject("_meta")
                    val cleanedMeta = JsonObject()
                    if (meta.has("title")) {
                        cleanedMeta.add("title", meta.get("title"))
                    }
                    if (cleanedMeta.size() > 0) {
                        cleanedNode.add("_meta", cleanedMeta)
                    }
                }

                cleaned.add(nodeId, cleanedNode)
            } else {
                // If it's some top-level non-node key in an API format (unusual but possible)
                // we drop it unless it's clearly relevant.
            }
        }
        return cleaned
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
