package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GraphToApiConverter {

    private val gson = Gson()

    data class ConversionResult(val json: String, val missingNodes: List<String>)

    fun convert(graphJson: String, objectInfo: ComfyObjectInfo): ConversionResult {
        val graph = JsonParser.parseString(graphJson).asJsonObject
        val api = JsonObject()
        val missingNodes = mutableSetOf<String>()

        // 1. Index Links: ID -> [SourceNodeID, SourceSlotIndex]
        // Graph Link structure: [id, source_id, source_slot, target_id, target_slot, type]
        val linkMap = mutableMapOf<Int, Pair<Int, Int>>()
        val linksArray = graph.getAsJsonArray("links") ?: JsonArray()
        linksArray.forEach { element ->
            if (element.isJsonArray) {
                val arr = element.asJsonArray
                if (arr.size() >= 3) {
                    val id = arr[0].asInt
                    val sourceNodeId = arr[1].asInt
                    val sourceSlotIndex = arr[2].asInt
                    linkMap[id] = sourceNodeId to sourceSlotIndex
                }
            }
        }

        // 2. Process Nodes
        val nodesArray = graph.getAsJsonArray("nodes") ?: JsonArray()
        nodesArray.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val id = node.get("id").asString // API uses string keys (usually ints)
            val type = node.get("type").asString
            
            val apiNode = JsonObject()
            val inputs = JsonObject()
            
            apiNode.addProperty("class_type", type)
            apiNode.add("inputs", inputs)
            
            // Add _meta with title for UI display
            val title = if (node.has("title")) node.get("title").asString else type
            val meta = JsonObject()
            meta.addProperty("title", title)
            apiNode.add("_meta", meta)
            
            // --- Helper to get Node Definition ---
            val nodeDef = objectInfo.dynamicNodes.get(type)?.asJsonObject
            if (nodeDef == null) {
                missingNodes.add(type)
            }
            
            // Phase 60: Filter out non-executable or missing nodes
            val nonExecutableTypes = setOf("MarkdownNote", "Note")
            if (nonExecutableTypes.contains(type)) {
                android.util.Log.d("CONVERT_DEBUG", "Node $id ($type): skipping non-executable node type")
                return@forEach
            }

            // Get Inputs Data (Dual Mode Support)
            val rawInputs = if (node.has("inputs")) node.get("inputs") else null

            // MODE A: Inputs is a JSONObject (API-style or Hybrid)
            // We can process this WITHOUT node definition because keys are explicit.
            if (rawInputs != null && rawInputs.isJsonObject) {
                val inputObj = rawInputs.asJsonObject
                android.util.Log.d("CONVERT_DEBUG", "Node $id: MODE A (keyed inputs), keys: ${inputObj.keySet()}")
                inputObj.entrySet().forEach { (key, element) ->
                    inputs.add(key, element)
                }
            } 
            // MODE B: Inputs is a JSONArray (Standard Graph)
            // We REQUIRE node definition to map 'widgets_values' arrays to named inputs.
            else if (nodeDef != null) {
                // Determine Input Mapping
                val inputDef = nodeDef.get("input")?.asJsonObject
                val required = inputDef?.get("required")?.asJsonObject
                val optional = inputDef?.get("optional")?.asJsonObject
                
                val allInputKeys = mutableListOf<String>()
                required?.entrySet()?.forEach { allInputKeys.add(it.key) }
                optional?.entrySet()?.forEach { allInputKeys.add(it.key) }
                
                val graphInputs = if (rawInputs != null && rawInputs.isJsonArray) rawInputs.asJsonArray else JsonArray()
                
                val graphWidgets = if (node.has("widgets_values") && node.get("widgets_values").isJsonArray) {
                    node.get("widgets_values").asJsonArray
                } else {
                     null
                }
                
                android.util.Log.d("CONVERT_DEBUG", "Node $id: MODE B, allInputKeys: $allInputKeys")
                android.util.Log.d("CONVERT_DEBUG", "Node $id: graphInputs slots: ${graphInputs.size()}, widgets_values: ${graphWidgets?.size() ?: "null"}")
                if (graphWidgets != null) {
                    android.util.Log.d("CONVERT_DEBUG", "Node $id: widgets_values content: $graphWidgets")
                }
                
                var widgetIndex = 0
                val slotNames = mutableSetOf<String>()
                graphInputs.forEach { 
                    slotNames.add(it.asJsonObject.get("name").asString)
                }
 
                // Helper function to get expected type for an input key
                fun getExpectedType(key: String): String? {
                    val reqDef = required?.get(key)?.asJsonArray
                    val optDef = optional?.get(key)?.asJsonArray
                    val def = reqDef ?: optDef ?: return null
                    if (def.size() == 0) return null
                    val firstElement = def[0]
                    return when {
                        firstElement.isJsonPrimitive && firstElement.asJsonPrimitive.isString -> firstElement.asString
                        firstElement.isJsonArray -> "COMBO" // It's a list of valid values
                        else -> null
                    }
                }
                 
                // Helper function to check if a widget value is compatible with expected type
                fun isCompatible(value: com.google.gson.JsonElement, expectedType: String?): Boolean {
                    if (expectedType == null) return true // No type info, assume compatible
                    return when (expectedType) {
                        "INT" -> value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || 
                                 (value.asJsonPrimitive.isString && value.asString.toLongOrNull() != null))
                        "FLOAT" -> value.isJsonPrimitive && (value.asJsonPrimitive.isNumber ||
                                   (value.asJsonPrimitive.isString && value.asString.toDoubleOrNull() != null))
                        "STRING" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                        "BOOLEAN" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
                        "COMBO" -> value.isJsonPrimitive // Combo accepts string or int index
                        else -> true // Unknown type, assume compatible
                    }
                }
                 
                // Helper function to find next compatible widget value
                fun findNextCompatibleWidget(key: String): com.google.gson.JsonElement? {
                    if (graphWidgets == null) return null
                    val expectedType = getExpectedType(key)
                    
                    // Try current index first
                    while (widgetIndex < graphWidgets.size()) {
                        val widget = graphWidgets[widgetIndex]
                        widgetIndex++
                        
                        if (isCompatible(widget, expectedType)) {
                            android.util.Log.d("CONVERT_DEBUG", "Node $id: key '$key' (type=$expectedType) -> widget[${widgetIndex-1}] = $widget")
                            return widget
                        } else {
                            android.util.Log.d("CONVERT_DEBUG", "Node $id: skipping widget[${widgetIndex-1}] = $widget (incompatible with $expectedType)")
                        }
                    }
                    return null
                }
 
                for (key in allInputKeys) {
                    if (slotNames.contains(key)) {
                        val slot = graphInputs.firstOrNull { it.asJsonObject.get("name").asString == key }?.asJsonObject
                        val linkId = if (slot?.get("link")?.isJsonNull == false) slot.get("link").asInt else null
                        
                        if (linkId != null && linkMap.containsKey(linkId)) {
                            val (sourceId, sourceSlot) = linkMap[linkId]!!
                            val linkArray = JsonArray()
                            linkArray.add(sourceId.toString())
                            linkArray.add(sourceSlot)
                            inputs.add(key, linkArray)
                            android.util.Log.d("CONVERT_DEBUG", "Node $id: key '$key' -> link [$sourceId, $sourceSlot]")
                        } else {
                            // Slot exists but has no link - find compatible widget value
                            val widget = findNextCompatibleWidget(key)
                            if (widget != null) {
                                inputs.add(key, widget)
                            } else {
                                android.util.Log.w("CONVERT_DEBUG", "Node $id: key '$key' (unlinked slot) has no compatible widget value")
                            }
                        }
                    } else {
                        // Not a slot, must be a pure widget value
                        val widget = findNextCompatibleWidget(key)
                        if (widget != null) {
                            inputs.add(key, widget)
                        } else {
                            android.util.Log.w("CONVERT_DEBUG", "Node $id: key '$key' has no compatible widget value")
                        }
                    }
                }
            } else {
                // Fallback / Error case: Missing metadata and NO keyed inputs.
                // We skip this node entirely to avoid server error.
                android.util.Log.w("CONVERT_DEBUG", "Node $id ($type): missing metadata and no keyed inputs - skipping")
                return@forEach
            }

            api.add(id, apiNode)
        }

        return ConversionResult(gson.toJson(api), missingNodes.toList())
    }
}
