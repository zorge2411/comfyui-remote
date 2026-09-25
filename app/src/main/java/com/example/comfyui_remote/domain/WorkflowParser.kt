package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class WorkflowParser {

    private companion object {
        const val DYNAMIC_COMBO = "COMFY_DYNAMICCOMBO_V3"
    }

    fun parse(jsonContent: String, metadata: JsonObject? = null): List<InputField> {
        val inputs = mutableListOf<InputField>()
        try {
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject

            jsonObject.entrySet().forEach { (nodeId, element) ->
                if (element.isJsonObject) {
                    val node = element.asJsonObject
                    val classType = node.get("class_type")?.asString
                    val inputsObj = node.get("inputs")?.asJsonObject
                    val meta = node.get("_meta")?.asJsonObject
                    val title = meta?.get("title")?.asString ?: classType ?: "Unknown Node"

                    if (classType != null && inputsObj != null) {
                        // Iterate through all keys in inputsObj
                        inputsObj.entrySet().forEach { (fieldName, fieldValue) ->
                            // Skip if it's a connection (JsonArray like ["4", 0])
                            if (!fieldValue.isJsonPrimitive) return@forEach
                            
                            val primitive = fieldValue.asJsonPrimitive
                            
                            // Guess or determine Type
                            val spec = specFor(metadata, classType, fieldName, inputsObj)
                            // A dynamic combo's value decides which "key.sub" inputs exist; changing it here
                            // would send the old option's inputs, so it is not offered for editing.
                            if (spec?.let { specKind(it) } == DYNAMIC_COMBO) return@forEach
                            val options = spec?.let { comboOptions(it) }
                            
                            val inputField = when {
                                (classType == "LoadImage" && fieldName == "image") -> {
                                    // Must be checked before the generic combo-options branch below:
                                    // real ComfyUI servers declare LoadImage's "image" input as a combo
                                    // list of already-uploaded server filenames, which would otherwise
                                    // always win and render a dropdown instead of the gallery/camera
                                    // picker — silently disabling img2img image selection entirely.
                                    InputField.ImageInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asString, // Likely the default filename
                                        localUri = null,
                                        nodeTitle = title
                                    )
                                }
                                options != null -> {
                                    InputField.SelectionInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asString,
                                        options = options,
                                        nodeTitle = title
                                    )
                                }
                                fieldName.contains("seed", ignoreCase = true) -> {
                                    InputField.SeedInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = try { primitive.asLong } catch(e: Exception) { 0L },
                                        nodeTitle = title
                                    )
                                }
                                fieldName == "ckpt_name" || fieldName == "model" || fieldName.endsWith("_name") -> {
                                    // Heuristic: If it ends in _name, it's likely a loader (vae_name, lora_name).
                                    // If we have options from metadata, it will be handled by the 'options != null' block above FIRST.
                                    // If we reach here, it means we have no metadata options for this loader. 
                                    // We fallback to ModelInput (which might just show a text field if we don't have list).
                                    // Or better: defaulting to StringInput might be safer if we don't have a list?
                                    // But ModelInput is intended for this. Let's use it.
                                    InputField.ModelInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asString,
                                        nodeTitle = title
                                    )
                                }
                                primitive.isNumber -> {
                                    // Check if it's float or int
                                    val number = primitive.asNumber
                                    if (number.toDouble() % 1.0 != 0.0 || fieldName == "denoise" || fieldName == "cfg") {
                                        // It has decimals OR it is a known float field (even if value is 1.0)
                                        // Note: 1.0 might appear as 1 in JSON if printed simply.
                                        // But primitive.asNumber handles it. 
                                        // Let's rely on Double conversion logic or known fields.
                                        InputField.FloatInput(
                                            nodeId = nodeId,
                                            fieldName = fieldName,
                                            value = number.toFloat(),
                                            nodeTitle = title
                                        )
                                    } else {
                                        InputField.IntInput(
                                            nodeId = nodeId,
                                            fieldName = fieldName,
                                            value = primitive.asInt,
                                            nodeTitle = title
                                        )
                                    }
                                }
                                primitive.isString -> {
                                    InputField.StringInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asString,
                                        nodeTitle = title
                                    )
                                }
                                else -> null
                            }
                            
                            inputField?.let { inputs.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val positiveNodeId = findPositivePromptNodeId(jsonContent)
        if (positiveNodeId != null) {
            val (positiveFields, rest) = inputs.partition { it.nodeId == positiveNodeId }
            if (positiveFields.isNotEmpty()) {
                return positiveFields + rest
            }
        }
        return inputs
    }

    /**
     * Finds the node ID producing the positive prompt by graph topology: locates a
     * sampler-shaped node (one exposing both `positive` and `negative` link inputs,
     * the standard CONDITIONING-pair convention) and traces its `positive` link back
     * to the source node. Returns null if no sampler-shaped node is found, or if
     * multiple sampler-shaped nodes disagree on the positive source (ambiguous) —
     * callers must leave ordering unchanged in either case.
     */
    private fun findPositivePromptNodeId(jsonContent: String): String? {
        val positiveSourceIds = mutableSetOf<String>()
        try {
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
            jsonObject.entrySet().forEach { (_, element) ->
                if (!element.isJsonObject) return@forEach
                val node = element.asJsonObject
                val inputsObj = node.get("inputs")?.asJsonObject ?: return@forEach
                val positive = inputsObj.get("positive")
                val negative = inputsObj.get("negative")
                if (positive != null && positive.isJsonArray && negative != null && negative.isJsonArray) {
                    val link = positive.asJsonArray
                    if (link.size() >= 1) {
                        positiveSourceIds.add(link[0].asString)
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return if (positiveSourceIds.size == 1) positiveSourceIds.first() else null
    }

    /**
     * The /object_info spec of an API input. Dotted keys ("format.codec") are inputs of a selected
     * COMFY_DYNAMICCOMBO_V3 option: walk each prefix, picking the option by the node's value for it.
     */
    private fun specFor(metadata: JsonObject?, classType: String, fieldName: String, nodeInputs: JsonObject): JsonArray? {
        if (metadata == null) return null
        return try {
            var group = metadata.getAsJsonObject(classType)?.getAsJsonObject("input") ?: return null
            val parts = fieldName.split(".")
            var spec: JsonArray? = null
            parts.forEachIndexed { i, part ->
                spec = (group.getAsJsonObject("required")?.get(part) ?: group.getAsJsonObject("optional")?.get(part))
                    ?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
                if (i < parts.lastIndex) {
                    if (specKind(spec!!) != DYNAMIC_COMBO) return null
                    val selected = nodeInputs.get(parts.take(i + 1).joinToString("."))
                        ?.takeIf { it.isJsonPrimitive }?.asString ?: return null
                    group = spec!!.get(1).asJsonObject.getAsJsonArray("options")
                        .map { it.asJsonObject }
                        .firstOrNull { it.get("key")?.asString == selected }
                        ?.getAsJsonObject("inputs") ?: return null
                }
            }
            spec
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun specKind(spec: JsonArray): String? {
        val first = spec.takeIf { it.size() > 0 }?.get(0) ?: return null
        return if (first.isJsonArray) "COMBO" else first.takeIf { it.isJsonPrimitive }?.asString
    }

    /** Options of a legacy [[...], {...}] or V3 ["COMBO", {"options": [...]}] combo; null for other inputs. */
    private fun comboOptions(spec: JsonArray): List<String>? {
        val first = spec.takeIf { it.size() > 0 }?.get(0) ?: return null
        val list = when {
            first.isJsonArray -> first.asJsonArray
            first.isJsonPrimitive && first.asString == "COMBO" ->
                spec.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArray("options")
            else -> null
        } ?: return null
        return list.filter { it.isJsonPrimitive }.map { it.asString }
    }

    fun parseAllNodes(jsonContent: String): List<NodeInfo> {
        val nodes = mutableListOf<NodeInfo>()
        try {
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
            jsonObject.entrySet().forEach { (nodeId, element) ->
                if (element.isJsonObject) {
                    val node = element.asJsonObject
                    val classType = node.get("class_type")?.asString ?: "Unknown"
                    val meta = node.get("_meta")?.asJsonObject
                    val title = meta?.get("title")?.asString ?: classType
                    nodes.add(NodeInfo(nodeId, title, classType))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return nodes
    }
}

data class NodeInfo(
    val id: String,
    val title: String,
    val classType: String
)
