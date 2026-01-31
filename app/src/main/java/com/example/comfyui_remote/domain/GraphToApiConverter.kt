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
        var graph = JsonParser.parseString(graphJson).asJsonObject
        
        // 0. Parse Definitions and Expand Subgraphs
        val definitions = parseSubgraphDefinitions(graph)
        if (definitions.isNotEmpty()) {
            println("CONVERT_DEBUG: Found ${definitions.size} subgraph definitions. Expanding...")
            graph = expandGraph(graph, definitions)
        }

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
        // First, map which nodes have outgoing links
        val nodesWithOutputs = mutableSetOf<Int>()
        linksArray.forEach { element ->
             if (element.isJsonArray) {
                val arr = element.asJsonArray
                if (arr.size() >= 2) {
                    nodesWithOutputs.add(arr[1].asInt)
                }
            }
        }

        // Map: PhantomNodeID -> List<InputLinkID>
        val phantomNodeInputs = mutableMapOf<Int, List<Int>>()
        val nodesArray = graph.getAsJsonArray("nodes") ?: JsonArray()
        
        nodesArray.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val idStr = node.get("id").asString
            val id = idStr.toIntOrNull() ?: return@forEach
            val type = node.get("type").asString
            val nodeDef = objectInfo.dynamicNodes.get(type)?.asJsonObject
            
            // Check if it's a Phantom Node
            if (nodeDef == null) {
                val isManualLoadImage = type == "LoadImage" || type == "ETN_LoadImageBase64"
                
                val hasWidgets = node.has("widgets_values") && 
                                 node.get("widgets_values").isJsonArray && 
                                 node.get("widgets_values").asJsonArray.size() > 0
                
                val hasKeyedInputs = node.has("inputs") && node.get("inputs").isJsonObject

                val isUuidType = type.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))

                val isSubgraph = node.has("properties") && 
                                 node.get("properties").asJsonObject.has("proxyWidgets")

                val hasOutputs = nodesWithOutputs.contains(id)

                // Candidate for skipping/flattening if missing metadata and:
                // 1. Is UUID (frontend-only)
                // 2. OR has NO content (no widgets, no keyed inputs)
                val isContentless = !hasWidgets && !hasKeyedInputs
                
                if ((isUuidType || isContentless) && !isManualLoadImage && !isSubgraph) {
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

                    // A: If it has inputs, we can potentially flatten/bypass it
                    if (inputLinks.isNotEmpty()) {
                        println("CONVERT_DEBUG: Node $id ($type): Flattening candidate (found ${inputLinks.size} inputs)")
                        phantomNodeInputs[id] = inputLinks
                    } 
                    // B: If it has NO inputs AND NO outputs, it's a dead-end shell -> skip it
                    else if (!hasOutputs) {
                        println("CONVERT_DEBUG: Node $id ($type): Dead-end shell detection. Skipping generation.")
                        phantomNodeInputs[id] = emptyList() // Mark for skipping in processing loop
                        missingNodes.add(type)
                    }
                    // C: If it has outputs but no inputs, it might be a producer -> Keep it!
                    else {
                        println("CONVERT_DEBUG: Node $id ($type): Missing metadata but has outputs and no inputs. Treating as Producer. Keeping.")
                        missingNodes.add(type)
                    }
                } else {
                    // Not flattened, but missing metadata
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
                println("CONVERT_DEBUG: FALLTHROUGH Node $id ($type). Metadata Missing? Mapping available data.")
                
                val graphWidgets = if (node.has("widgets_values") && node.get("widgets_values").isJsonArray) {
                    node.get("widgets_values").asJsonArray
                } else null

                // 1. Handle Linked Inputs (even without metadata)
                if (rawInputs != null && rawInputs.isJsonArray) {
                    rawInputs.asJsonArray.forEach { inputEl ->
                        val slot = inputEl.asJsonObject
                        val key = slot.get("name").asString
                        val linkId = if (slot.has("link") && !slot.get("link").isJsonNull) slot.get("link").asInt else null
                        
                        if (linkId != null) {
                            val resolved = resolveRealSource(linkId)
                            if (resolved != null) {
                                val (sourceId, sourceSlot) = resolved
                                val linkArray = JsonArray()
                                linkArray.add(sourceId.toString())
                                linkArray.add(sourceSlot)
                                inputs.add(key, linkArray)
                            }
                        }
                    }
                }

                // 2. Heuristic Widget Mapping
                if (graphWidgets != null) {
                    for (i in 0 until graphWidgets.size()) {
                        val value = graphWidgets[i]
                        val valueStr = if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else ""
                        
                        when {
                            // Image Heuristic
                            (valueStr.endsWith(".png") || valueStr.endsWith(".jpg") || valueStr.endsWith(".webp")) && !inputs.has("image") -> {
                                inputs.add("image", value)
                            }
                            // Generic value (often first widget)
                            i == 0 && !inputs.has("value") -> {
                                inputs.add("value", value)
                            }
                            else -> {
                                inputs.add("widget_$i", value)
                            }
                        }
                    }
                }
                
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

        return ConversionResult(gson.toJson(api), missingNodes.toList().also { 
            println("CONVERT_DEBUG: Final Missing Nodes List: $it") 
        })
    }

    internal fun parseSubgraphDefinitions(graph: JsonObject): Map<String, SubgraphDefinition> {
        val definitions = graph.getAsJsonObject("definitions") ?: return emptyMap()
        val subgraphs = definitions.getAsJsonArray("subgraphs") ?: return emptyMap()
        
        val result = subgraphs.mapNotNull { element ->
            val subgraph = element.asJsonObject
            val id = subgraph.get("id").asString
            val name = if (subgraph.has("name")) subgraph.get("name").asString else "Subgraph-$id"
            
            // Parse inputs
            val inputs = subgraph.getAsJsonArray("inputs")?.map { inputEl ->
                val inputObj = inputEl.asJsonObject
                val linkIds = inputObj.getAsJsonArray("linkIds")?.map { it.asInt } ?: emptyList()
                SubgraphInput(
                    id = inputObj.get("id").asString,
                    name = inputObj.get("name").asString,
                    type = inputObj.get("type").asString,
                    linkIds = linkIds
                )
            } ?: emptyList()
            
            // Parse outputs  
            val outputs = subgraph.getAsJsonArray("outputs")?.map { outputEl ->
                val outputObj = outputEl.asJsonObject
                val linkIds = outputObj.getAsJsonArray("linkIds")?.map { it.asInt } ?: emptyList()
                SubgraphOutput(
                    id = outputObj.get("id").asString,
                    name = outputObj.get("name").asString,
                    type = outputObj.get("type").asString,
                    linkIds = linkIds
                )
            } ?: emptyList()
            
            // Extract internal nodes and links
            val nodes = subgraph.getAsJsonArray("nodes")
            val links = subgraph.getAsJsonArray("links")
            
            if (nodes != null && links != null) {
                // Convert object links to array links standard
                // Object: {id, origin_id, origin_slot, target_id, target_slot, type}
                // Array: [id, origin_id, origin_slot, target_id, target_slot, type]
                val normalizedLinks = links.map { linkEl ->
                    val obj = linkEl.asJsonObject
                    val arr = com.google.gson.JsonArray()
                    arr.add(obj.get("id").asInt)
                    arr.add(obj.get("origin_id").asInt)
                    arr.add(obj.get("origin_slot").asInt)
                    arr.add(obj.get("target_id").asInt)
                    arr.add(obj.get("target_slot").asInt)
                    arr.add(if (obj.has("type")) obj.get("type") else com.google.gson.JsonPrimitive("*"))
                    arr
                }
                
                id to SubgraphDefinition(id, name, inputs, outputs, nodes.toList().map { it.asJsonObject }, normalizedLinks)
            } else {
                null
            }
        }.toMap()

        println("SUBGRAPH_DEBUG: Parsed ${result.size} subgraph definitions")
        return result
    }

    internal fun expandGraph(graph: JsonObject, definitions: Map<String, SubgraphDefinition>): JsonObject {
        var currentGraph = graph
        var expansionCount = 0
        val maxIterations = 10 // Safety limit for deeply nested subgraphs
        
        while (expansionCount < maxIterations) {
            val result = expandGraphOnce(currentGraph, definitions)
            if (result.second == 0) {
                // No more subgraphs to expand
                break
            }
            currentGraph = result.first
            expansionCount++
            println("SUBGRAPH_DEBUG: Expansion iteration $expansionCount completed. Expanded ${result.second} subgraphs.")
        }
        
        if (expansionCount >= maxIterations) {
            println("SUBGRAPH_DEBUG: WARNING - Reached max expansion iterations ($maxIterations). Possible circular reference?")
        }
        
        return currentGraph
    }

    // Returns: (ExpandedGraph, NumberOfSubgraphsExpanded)
    private fun expandGraphOnce(graph: JsonObject, definitions: Map<String, SubgraphDefinition>): Pair<JsonObject, Int> {
        val originalNodes = graph.getAsJsonArray("nodes") ?: JsonArray()
        val originalLinks = graph.getAsJsonArray("links") ?: JsonArray()
        
        val newNodes = JsonArray()
        val newLinks = JsonArray()
        
        var maxId = 0
        originalNodes.forEach { 
            val id = it.asJsonObject.get("id").asInt
            if (id > maxId) maxId = id
        }
        // Also scan links for max ID
        originalLinks.forEach {
            val linkArr = it.asJsonArray
            if (linkArr.size() > 0) {
                val linkId = linkArr[0].asInt
                if (linkId > maxId) maxId = linkId
            }
        }
        
        var subgraphsExpanded = 0
        
        // Maps to handle link rewiring
        val wrapperInputRedirects = mutableMapOf<Int, Map<Int, List<Pair<Int, Int>>>>()
        val wrapperOutputRedirects = mutableMapOf<Int, Map<Int, Pair<Int, Int>>>()
        
        // Pass-wide maps for updating node inputs
        val globalLinkIdRemapper = mutableMapOf<Int, Int>()
        // Map: NodeID -> SlotIndex -> NewLinkID (For boundary connections)
        val specificInputUpdates = mutableMapOf<Int, MutableMap<Int, Int>>()
        
        originalNodes.forEach { nodeEl ->
            val node = nodeEl.asJsonObject
            val type = node.get("type").asString
            val id = node.get("id").asInt
            
            // Check if it's a wrapper
            val def = definitions[type]
            val isSubgraph = def != null && node.has("properties") && node.get("properties").asJsonObject.has("proxyWidgets")
            
            if (isSubgraph) {
                subgraphsExpanded++
                // EXPAND
                val definition = definitions[type]!!
                
                // 1. Remap IDs
                val internalIds = mutableSetOf<Int>()
                definition.nodes.forEach { internalIds.add(it.get("id").asInt) }
                val idRemapper = createIdRemapper(setOf(maxId), internalIds)
                maxId += internalIds.size // Advance maxId safely
                
                val (remappedNodes, linkMap) = remapInternalLinks(definition.nodes, definition.links, idRemapper)
                
                // 2. Identify Boundary Nodes (Input -10, Output -20)
                // Note: -10 and -20 are remapped too! 
                // We need to know what they turned into.
                val remappedInputId = idRemapper[-10]
                val remappedOutputId = idRemapper[-20]
                
                // 3. Analyze Connections to build Redirect Maps
                val inputRedirects = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
                val outputRedirects = mutableMapOf<Int, Pair<Int, Int>>()
                
                // Map old internal link IDs to new link IDs
                val linkIdRemapper = mutableMapOf<Int, Int>()
                
                // Process Internal Links to find boundaries
                definition.links.forEach { link -> // These are RAW links (arrays) from definition
                    if (link.size() >= 5) { // [id, sourceId, sourceSlot, targetId, targetSlot, type]
                        val oldLinkId = link[0].asInt
                        val sourceId = link[1].asInt
                        val sourceSlot = link[2].asInt
                        val targetId = link[3].asInt
                        val targetSlot = link[4].asInt
                        
                        // Check if Link STARTS from InputNode (-10)
                        if (sourceId == -10) {
                            // This internal link connects InputNode Output(Slot=sourceSlot) -> InternalNode Input
                            // sourceSlot matches the Subgraph Input Index (usually)
                            // We need to redirect External connection to (remappedTargetId, targetSlot)
                            val remappedTargetId = idRemapper[targetId] ?: targetId
                            
                            if (!inputRedirects.containsKey(sourceSlot)) {
                                inputRedirects[sourceSlot] = mutableListOf()
                            }
                            inputRedirects[sourceSlot]?.add(remappedTargetId to targetSlot)
                            
                            // Mark this link as "removed" (set to -1) so nodes know to clear it
                            linkIdRemapper[oldLinkId] = -1
                            
                            // DO NOT add this link to newLinks (it's internal boundary)
                        } 
                        // Check if Link ENDS at OutputNode (-20)
                        else if (targetId == -20) {
                            // This internal link connects InternalNode Output -> OutputNode Input(Slot=targetSlot)
                            // targetSlot matches the Subgraph Output Index
                            // We need to redirect External connection from (remappedSourceId, sourceSlot)
                            val remappedSourceId = idRemapper[sourceId] ?: sourceId
                            
                            outputRedirects[targetSlot] = remappedSourceId to sourceSlot
                            
                            // Mark this link as "removed"
                            linkIdRemapper[oldLinkId] = -1
                            
                            // DO NOT add this link to newLinks
                        } 
                        else {
                            // Pure Internal Link - Add to newLinks with REMAPPED IDs
                            val remappedSourceId = idRemapper[sourceId] ?: sourceId
                            val remappedTargetId = idRemapper[targetId] ?: targetId
                            val newLinkId = maxId++ // Generate new Link ID
                            
                            // Track old -> new link ID mapping
                            linkIdRemapper[oldLinkId] = newLinkId
                            
                            val newLink = JsonArray()
                            newLink.add(newLinkId)
                            newLink.add(remappedSourceId)
                            newLink.add(sourceSlot)
                            newLink.add(remappedTargetId)
                            newLink.add(targetSlot)
                            newLink.add(if (link.size() > 5) link[5] else com.google.gson.JsonPrimitive("Wildcard"))
                            newLinks.add(newLink)
                            
                            // Specific Update: Target Node needs to know about this new Link ID
                            specificInputUpdates.getOrPut(remappedTargetId) { mutableMapOf() }[targetSlot] = newLinkId
                        }
                    }
                }
                
                wrapperInputRedirects[id] = inputRedirects
                wrapperOutputRedirects[id] = outputRedirects
                
                // 4. Add Pure Internal Nodes (excluding Input -10/Output -20)
                // AND update their inputs[].link values using linkIdRemapper
                remappedNodes.forEach { internalNode ->
                    val currentId = intId(internalNode)
                    if (currentId != remappedInputId && currentId != remappedOutputId) {
                        // Update this node's input links
                        val updatedNode = updateNodeInputLinks(internalNode, linkIdRemapper)
                        newNodes.add(updatedNode)
                    }
                }
                
                println("SUBGRAPH_DEBUG: Expanded wrapper $id ($type). Added ${remappedNodes.size - 2} nodes. Redirects: In=${inputRedirects.size}, Out=${outputRedirects.size}. LinkRemaps: ${linkIdRemapper.size}")
                
            } else {
                // Normal Node - Keep as is
                newNodes.add(node)
            }
        }
        
        // Process Original Links
        originalLinks.forEach { linkEl ->
            val link = linkEl.asJsonArray
            val oldLinkId = link[0].asInt
            val sourceId = link[1].asInt
            val sourceSlot = link[2].asInt
            val targetId = link[3].asInt
            val targetSlot = link[4].asInt
            val type = if (link.size() > 5) if (link[5].isJsonPrimitive) link[5].asString else "*" else "*"
            
            val isDestWrapper = wrapperInputRedirects.containsKey(targetId)
            val isSourceWrapper = wrapperOutputRedirects.containsKey(sourceId)
            
            if (isDestWrapper && isSourceWrapper) {
                // Wrapper -> Wrapper
                val sourceRedirect = wrapperOutputRedirects[sourceId]?.get(sourceSlot)
                val destRedirects = wrapperInputRedirects[targetId]?.get(targetSlot)
                
                if (sourceRedirect != null && destRedirects != null) {
                    val (realSourceId, realSourceSlot) = sourceRedirect
                    
                    destRedirects.forEach { (realDestId, realDestSlot) ->
                        val newLinkId = maxId++
                        val newLink = JsonArray()
                        newLink.add(newLinkId)
                        newLink.add(realSourceId)
                        newLink.add(realSourceSlot)
                        newLink.add(realDestId)
                        newLink.add(realDestSlot)
                        newLink.add(type)
                        newLinks.add(newLink)
                        
                        // Update Destination Internal Node
                        specificInputUpdates.getOrPut(realDestId) { mutableMapOf() }[realDestSlot] = newLinkId
                    }
                }
            }
            else if (isDestWrapper) {
                // External -> Wrapper
                val destRedirects = wrapperInputRedirects[targetId]?.get(targetSlot)
                
                if (destRedirects != null) {
                    destRedirects.forEach { (realDestId, realDestSlot) ->
                        val newLinkId = maxId++
                        val newLink = JsonArray()
                        newLink.add(newLinkId)
                        newLink.add(sourceId) // Original Source
                        newLink.add(sourceSlot)
                        newLink.add(realDestId)
                        newLink.add(realDestSlot)
                        newLink.add(type)
                        newLinks.add(newLink)
                        
                        // Update Destination Internal Node
                        specificInputUpdates.getOrPut(realDestId) { mutableMapOf() }[realDestSlot] = newLinkId
                    }
                }
            } 
            else if (isSourceWrapper) {
                // Wrapper -> External
                val sourceRedirect = wrapperOutputRedirects[sourceId]?.get(sourceSlot)
                
                if (sourceRedirect != null) {
                    val (realSourceId, realSourceSlot) = sourceRedirect
                    
                    val newLinkId = maxId++
                    val newLink = JsonArray()
                    newLink.add(newLinkId)
                    newLink.add(realSourceId)
                    newLink.add(realSourceSlot)
                    newLink.add(targetId) // Original Target
                    newLink.add(targetSlot)
                    newLink.add(type)
                    newLinks.add(newLink)
                    
                    // Update Destination External Node
                    globalLinkIdRemapper[oldLinkId] = newLinkId
                }
            } 
            else {
                // Normal -> Normal
                newLinks.add(link)
            }
        }
        
        // 3. Apply Updates to All Nodes
        val finalNodes = JsonArray()
        newNodes.forEach { nodeEl ->
            val node = nodeEl.asJsonObject
            val nodeId = intId(node)
            
            // Apply Global Remaps
            var updatedNode = updateNodeInputLinks(node, globalLinkIdRemapper)
            
            // Apply Specific Updates
            val specifics = specificInputUpdates[nodeId]
            if (specifics != null) {
                updatedNode = applySpecificInputUpdates(updatedNode, specifics)
            }
            
            finalNodes.add(updatedNode)
        }
        
        val newGraph = graph.deepCopy()
        newGraph.add("nodes", finalNodes)
        newGraph.add("links", newLinks)
        return newGraph to subgraphsExpanded
    }

    private fun intId(node: JsonObject): Int {
        return node.get("id").asInt
    }

    private fun applySpecificInputUpdates(node: JsonObject, updates: Map<Int, Int>): JsonObject {
        val updatedNode = node.deepCopy()
        if (!updatedNode.has("inputs")) return updatedNode
        
        val inputs = updatedNode.getAsJsonArray("inputs")
        val newInputs = JsonArray()
        
        // Map slot index to Input Object
        // Inputs in JSON are array of objects. We don't have explicit slots in properties.
        // BUT internal links use slot index.
        // Usually input order matches slot index.
        
        inputs.forEachIndexed { index, inputEl ->
            if (inputEl.isJsonObject) {
                val input = inputEl.asJsonObject.deepCopy()
                // Check if we have an update for this slot index
                val newLinkId = updates[index]
                if (newLinkId != null) {
                    input.addProperty("link", newLinkId)
                }
                newInputs.add(input)
            } else {
                newInputs.add(inputEl)
            }
        }
        
        updatedNode.add("inputs", newInputs)
        return updatedNode
    }

    private fun updateNodeInputLinks(node: JsonObject, linkIdRemapper: Map<Int, Int>): JsonObject {
        val updatedNode = node.deepCopy()
        
        if (!updatedNode.has("inputs") || !updatedNode.get("inputs").isJsonArray) {
            return updatedNode
        }
        
        val inputs = updatedNode.getAsJsonArray("inputs")
        val newInputs = JsonArray()
        
        inputs.forEach { inputEl ->
            if (inputEl.isJsonObject) {
                val input = inputEl.asJsonObject.deepCopy()
                if (input.has("link") && !input.get("link").isJsonNull) {
                    val oldLinkId = input.get("link").asInt
                    val newLinkId = linkIdRemapper[oldLinkId]
                    
                    if (newLinkId != null) {
                        if (newLinkId == -1) {
                            // Link was removed (boundary link) - set to null
                            input.add("link", com.google.gson.JsonNull.INSTANCE)
                        } else {
                            // Update to new link ID
                            input.addProperty("link", newLinkId)
                        }
                    }
                    // If not in remapper, keep original (might be external link)
                }
                newInputs.add(input)
            } else {
                newInputs.add(inputEl)
            }
        }
        
        updatedNode.add("inputs", newInputs)
        return updatedNode
    }

    internal fun createIdRemapper(existingNodeIds: Set<Int>, subgraphInternalIds: Set<Int>): Map<Int, Int> {
        val maxExistingId = existingNodeIds.maxOrNull() ?: 0
        var nextId = maxExistingId + 1
        
        return subgraphInternalIds.associateWith { oldId ->
            nextId++.also {
                println("SUBGRAPH_DEBUG: Remapping internal node $oldId -> $it")
            }
        }
    }

    internal fun remapInternalLinks(
        nodes: List<JsonObject>, 
        links: List<JsonArray>,
        idRemapper: Map<Int, Int>
    ): Pair<List<JsonObject>, Map<Int, Pair<Int, Int>>> {
        // Update node IDs in nodes array
        val remappedNodes = nodes.map { node ->
            val oldId = node.get("id").asInt
            val newId = idRemapper[oldId] ?: oldId
            val newNode = node.deepCopy().asJsonObject
            newNode.addProperty("id", newId)
            newNode
        }
        
        // Update link sourceNodeId and create linkId -> (sourceNodeId, sourceSlot) map
        val linkMap = mutableMapOf<Int, Pair<Int, Int>>()
        links.forEach { link ->
            // Link format: [id, sourceNodeId, sourceSlot, targetNodeId, targetSlot, type]
            // We only need Source info for the map: LinkID -> (SourceNodeID, SourceSlot)
            
            if (link.size() >= 3) {
                val linkId = link[0].asInt
                val sourceNodeId = link[1].asInt
                val sourceSlot = link[2].asInt
                
                val remappedSourceId = idRemapper[sourceNodeId] ?: sourceNodeId
                linkMap[linkId] = remappedSourceId to sourceSlot
            }
        }
        
        return remappedNodes to linkMap
    }
}
