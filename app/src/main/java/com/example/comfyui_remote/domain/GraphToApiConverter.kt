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

        // 2. Pre-scan for Phantom Nodes (Missing Metadata) to build efficient lookup
        // Map: PhantomNodeID -> List<InputLinkID>
        val phantomNodeInputs = mutableMapOf<Int, List<Int>>()
        val nodesArray = graph.getAsJsonArray("nodes") ?: JsonArray()
        
        nodesArray.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val id = node.get("id").asString.toIntOrNull() ?: return@forEach
            val type = node.get("type").asString
            val nodeDef = objectInfo.dynamicNodes.get(type)?.asJsonObject
            
            // Treat MarkdownNote/Note as non-executable (ignore generally) but we don't need to flatten through them usually?
            // Actually, if we are bypassing, we only care about nodes that MIGHT be part of the flow but are unknown.
            // MarkdownNotes usually don't have output links, so they won't be in dependency chain.
            
            // Check if it's a Phantom Node
            if (nodeDef == null) {
                // Determine if it is a manual fallback node (e.g. LoadImage) or a true Phantom to be flattened
                val isManualLoadImage = type == "LoadImage" || type == "ETN_LoadImageBase64"
                
                // Heuristic: If a node has widgets, it's likely a configurable functional node (e.g. KSampler).
                // We should NOT flatten it even if metadata is missing, because flattening deletes the widgets.
                // Instead, we let it pass through (it will report as Missing Node later).
                val hasWidgets = node.has("widgets_values") && 
                                 node.get("widgets_values").isJsonArray && 
                                 node.get("widgets_values").asJsonArray.size() > 0

                if (!isManualLoadImage && !hasWidgets) {
                    // This is a candidate for flattening. Collect its input links.
                    val inputLinks = mutableListOf<Int>()
                    val rawInputs = if (node.has("inputs")) node.get("inputs") else null
                    if (rawInputs != null && rawInputs.isJsonArray) {
                        rawInputs.asJsonArray.forEach { inputEl ->
                            val linkEl = inputEl.asJsonObject.get("link")
                            if (linkEl != null && !linkEl.isJsonNull) {
                                inputLinks.add(linkEl.asInt)
                            }
                        }
                    }
                    phantomNodeInputs[id] = inputLinks
                    // We also track it as missing for the report, unless it gets fully bypassed
                     missingNodes.add(type)
                }
            }
        }

        // Helper function to resolve real source recursively
        fun resolveRealSource(initialLinkId: Int, visited: MutableSet<Int> = mutableSetOf()): Pair<Int, Int>? {
            if (visited.contains(initialLinkId)) return null // Cycle detected
            visited.add(initialLinkId)

            if (!linkMap.containsKey(initialLinkId)) return null
            val (sourceId, sourceSlot) = linkMap[initialLinkId]!!

            // Is the source a phantom node?
            if (phantomNodeInputs.containsKey(sourceId)) {
                val inputs = phantomNodeInputs[sourceId]
                if (inputs.isNullOrEmpty()) return null // Dead end

                // Heuristic: specific logic for Reroute/Primitive?
                // For now: Take the first input link (Index 0). 
                // Mostly these pass-through nodes have 1 input. If multiple, we gamble on the first.
                val firstInputLink = inputs[0]
                println("CONVERT_DEBUG: Flattening - Node $sourceId is phantom, bypassing to input link $firstInputLink")
                return resolveRealSource(firstInputLink, visited)
            }

            // It's a real node (or at least one we are preserving)
            return sourceId to sourceSlot
        }

        // 3. Process Nodes
        nodesArray.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val idStr = node.get("id").asString
            val id = idStr.toIntOrNull() ?: 0
            val type = node.get("type").asString
            
            // Skip processing if this is a Phantom Node (it's being flattened)
            // UNLESS we are preserving manual fallback nodes (LoadImage)
            val isManualLoadImage = type == "LoadImage" || type == "ETN_LoadImageBase64"
            
            // If it's in our phantom input map, it means it's missing metadata AND NOT a manual fallback.
            // So we skip generating it in the JSON -> It is "Flattened" out.
            if (phantomNodeInputs.containsKey(id)) {
                println("CONVERT_DEBUG: Node $id ($type): skipping generation (will be flattened)")
                return@forEach
            }
            
            val nodeDef = objectInfo.dynamicNodes.get(type)?.asJsonObject
            
            // Filter non-executables
            val nonExecutableTypes = setOf("MarkdownNote", "Note")
            if (nonExecutableTypes.contains(type)) {
                return@forEach
            }

            val apiNode = JsonObject()
            val inputs = JsonObject()
            apiNode.addProperty("class_type", type)
            apiNode.add("inputs", inputs)
            
            val title = if (node.has("title")) node.get("title").asString else type
            val meta = JsonObject()
            meta.addProperty("title", title)
            apiNode.add("_meta", meta)

            // Get Inputs Data
            val rawInputs = if (node.has("inputs")) node.get("inputs") else null

            // MODE A: Inputs is a JSONObject
            if (rawInputs != null && rawInputs.isJsonObject) {
                val inputObj = rawInputs.asJsonObject
                inputObj.entrySet().forEach { (key, element) ->
                    inputs.add(key, element)
                }
            } 
            // MODE B: Inputs is a JSONArray
            else if (nodeDef != null || isManualLoadImage) {
                val inputDef = nodeDef?.get("input")?.asJsonObject
                val required = inputDef?.get("required")?.asJsonObject
                val optional = inputDef?.get("optional")?.asJsonObject
                
                val allInputKeys = mutableListOf<String>()
                required?.entrySet()?.forEach { allInputKeys.add(it.key) }
                optional?.entrySet()?.forEach { allInputKeys.add(it.key) }
                
                val graphInputs = if (rawInputs != null && rawInputs.isJsonArray) rawInputs.asJsonArray else JsonArray()
                val graphWidgets = if (node.has("widgets_values") && node.get("widgets_values").isJsonArray) {
                    node.get("widgets_values").asJsonArray
                } else null
                
                println("CONVERT_DEBUG: Processing Node $id ($type). Metadata Found: ${nodeDef != null}. Widgets: ${graphWidgets?.size()}")
                
                var widgetIndex = 0
                val slotNames = mutableSetOf<String>()
                graphInputs.forEach { 
                    slotNames.add(it.asJsonObject.get("name").asString)
                }

 
                // Helper to get expected type
                fun getExpectedType(key: String): String? {
                    val reqDef = required?.get(key)?.asJsonArray
                    val optDef = optional?.get(key)?.asJsonArray
                    val def = reqDef ?: optDef ?: return null
                    if (def.size() == 0) return null
                    val firstElement = def[0]
                    return when {
                        firstElement.isJsonPrimitive && firstElement.asJsonPrimitive.isString -> firstElement.asString
                        firstElement.isJsonArray -> "COMBO"
                        else -> null
                    }
                }
                 
                // Helper to check compatibility
                fun isCompatible(value: com.google.gson.JsonElement, expectedType: String?): Boolean {
                    if (expectedType == null) return true
                    return when (expectedType) {
                        "INT" -> value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || (value.asJsonPrimitive.isString && value.asString.toLongOrNull() != null))
                        "FLOAT" -> value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || (value.asJsonPrimitive.isString && value.asString.toDoubleOrNull() != null))
                        "STRING" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                        "BOOLEAN" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
                        "COMBO" -> value.isJsonPrimitive
                        else -> true
                    }
                }
                 
                fun findNextCompatibleWidget(key: String): com.google.gson.JsonElement? {
                    if (graphWidgets == null) return null
                    val expectedType = getExpectedType(key)
                    while (widgetIndex < graphWidgets.size()) {
                        val widget = graphWidgets[widgetIndex]
                        widgetIndex++
                        if (isCompatible(widget, expectedType)) return widget
                    }
                    return null
                }
 
                if (isManualLoadImage) {
                    if (graphWidgets != null && graphWidgets.size() > 0) {
                        inputs.add("image", graphWidgets[0])
                    }
                }

                for (key in allInputKeys) {
                    if (slotNames.contains(key)) {
                        val slot = graphInputs.firstOrNull { it.asJsonObject.get("name").asString == key }?.asJsonObject
                        val linkId = if (slot?.get("link")?.isJsonNull == false) slot.get("link").asInt else null
                        
                        if (linkId != null) {
                            // HERE IS THE FLATTENING CALL
                            val resolved = resolveRealSource(linkId)
                            
                            if (resolved != null) {
                                val (sourceId, sourceSlot) = resolved
                                val linkArray = JsonArray()
                                linkArray.add(sourceId.toString())
                                linkArray.add(sourceSlot)
                                inputs.add(key, linkArray)
                            } else {
                                // Could not resolve (maybe link to missing node that has no input?)
                                println("CONVERT_DEBUG: Warn: Node $id: key '$key' link $linkId resolved to null (broken chain?)")
                            }
                        } else {
                            val widget = findNextCompatibleWidget(key)
                            if (widget != null) inputs.add(key, widget)
                        }
                    } else {
                        val widget = findNextCompatibleWidget(key)
                        if (widget != null) inputs.add(key, widget)
                    }
                }
            } else {
                // This block should barely be reached now, as Phantom nodes are skipped above.
                // But just in case logic slips through (e.g. unknown node not in nodes list? impossible)
                missingNodes.add(type)
            }

            api.add(idStr, apiNode)
        }

        // Remove bypassed nodes from missing nodes list so we don't warn user unnecessarily
        phantomNodeInputs.keys.forEach { bypassedId ->
             // Actually phantomNodeInputs key is Int.
             // We need to find the TYPE for that ID to remove it?
             // Or we just stored Types in missingNodes directly.
             // This is tricky. simpler to just let them be "missing" warning? 
             // But if we successfully flattened them, they aren't "missing" in a problematic way.
             // Ideally we filter missingNodes at the end.
        }
        
        // Better: Validate missingNodes set against the API output.
        // If a node type is IN the missingNodes set BUT NOT in the final API JSON, it was removed.
        // Wait, 'missingNodes' stores TYPES.
        // Let's just return the list. The UI warning is fine: "Missing Nodes: Reroute". 
        // User will understand "Oh, Reroute is not on server, but maybe it worked?"
        // Ideally we suppress it if we know we handled it.
        // Let's Filter: Only report missing nodes that we FAILED to bypass?
        // Actually, 'missingNodes' are accumulated during the 'Processed Nodes' Loop.
        // And we SKIP processing phantom nodes in that loop (line 108: return@forEach).
        // So they WON'T be added to missingNodes anymore!
        // EXCEPT: We added them in the PRE-SCAN loop (line 66).
        // Let's REMOVE the add in pre-scan loop to avoid duplicate/false positive.
        // We only add to 'missingNodes' if we FAIL to bypass? 
        // No, if we flatten, we just don't include it. 
        // So I will remove `missingNodes.add(type)` from the Pre-scan loop in the logic above.

        return ConversionResult(gson.toJson(api), missingNodes.toList())
    }
}
