package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GraphToApiConverter {

    private val gson = Gson()

    fun convert(graphJson: String, objectInfo: ComfyObjectInfo): String {
        val graph = JsonParser.parseString(graphJson).asJsonObject
        val api = JsonObject()

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
            // The object info has keys like "CheckpointLoaderSimple".
            val nodeDef = objectInfo.dynamicNodes.get(type)?.asJsonObject

            if (nodeDef != null) {
                // Determine Input Mapping
                // Graph "widgets_values" (array) maps to standard widget inputs
                // Graph "inputs" (array of objects) maps to link inputs
                
                // We need to iterate the DEFINITION to find the correct names for widgets.
                val inputDef = nodeDef.get("input")?.asJsonObject
                val required = inputDef?.get("required")?.asJsonObject
                val optional = inputDef?.get("optional")?.asJsonObject
                
                // Combine keys preserving order? Gson JsonObject entrySet usually preserves order.
                val allInputKeys = mutableListOf<String>()
                required?.entrySet()?.forEach { allInputKeys.add(it.key) }
                optional?.entrySet()?.forEach { allInputKeys.add(it.key) }
                
                // Get Graph Data
                val graphInputs = node.getAsJsonArray("inputs") // These are slots (potential links)
                val graphWidgets = node.getAsJsonArray("widgets_values") // These are values
                
                var widgetIndex = 0
                
                // Set of input names that are ACTUALLY slots in the graph
                // In graph JSON, 'inputs' is list of { "name": "ckpt_name", ... }
                val slotNames = mutableSetOf<String>()
                graphInputs?.forEach { 
                    slotNames.add(it.asJsonObject.get("name").asString)
                }

                // --- Map Values ---
                for (key in allInputKeys) {
                    // check if this key corresponds to a Slot (Link Input)
                    if (slotNames.contains(key)) {
                        // It is a slot. Check if it has a link.
                        val slot = graphInputs.firstOrNull { it.asJsonObject.get("name").asString == key }?.asJsonObject
                        val linkId = if (slot?.get("link")?.isJsonNull == false) slot.get("link").asInt else null
                        
                        if (linkId != null && linkMap.containsKey(linkId)) {
                            // It's a link: ["5", 0]
                            val (sourceId, sourceSlot) = linkMap[linkId]!!
                            val linkArray = JsonArray()
                            linkArray.add(sourceId.toString())
                            linkArray.add(sourceSlot)
                            inputs.add(key, linkArray)
                        } else {
                            // It's an empty slot? API format usually ignores unconnected optional inputs, 
                            // but for required... maybe it fails? 
                            // Or maybe there is a default value?
                            // Logic: If it's a SLOT but unconnected, we do nothing for now.
                        }
                    } else {
                        // It is NOT a slot. It must be a widget value.
                        if (graphWidgets != null && widgetIndex < graphWidgets.size()) {
                            inputs.add(key, graphWidgets[widgetIndex])
                            widgetIndex++
                        }
                    }
                }
            } else {
                // Fallback for unknown nodes? 
                // We could try to map all links we find... but we can't map widgets without definition.
                // For now, let's just log or ignore.
                // Or maybe assume graphInputs match? No, impossible to mapping widgets without order.
            }

            api.add(id, apiNode)
        }

        return gson.toJson(api)
    }
}
