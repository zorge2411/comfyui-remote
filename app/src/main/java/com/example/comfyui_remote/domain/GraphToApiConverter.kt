package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

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

        // 1. Index Links: ID -> [SourceNodeID, SourceSlotIndex], and ID -> link type (the target input's type)
        val linkMap = mutableMapOf<Int, Pair<Int, Int>>()
        val linkTypeMap = mutableMapOf<Int, String>()
        val linksArray = graph.getAsJsonArray("links") ?: JsonArray()
        linksArray.forEach { element ->
            if (element.isJsonArray) {
                val arr = element.asJsonArray
                if (arr.size() >= 3) {
                    val id = arr[0].asInt
                    val sourceNodeId = arr[1].asInt
                    val sourceSlotIndex = arr[2].asInt
                    linkMap[id] = sourceNodeId to sourceSlotIndex
                    if (arr.size() >= 6 && arr[5].isJsonPrimitive) linkTypeMap[id] = arr[5].asString
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

        // Map: PhantomNodeID -> its graph input slots (empty for a dead-end shell)
        val phantomNodeInputs = mutableMapOf<Int, JsonArray>()
        // Frontend-only nodes (VIRTUAL_TYPES the server doesn't define): never sent, never reported missing
        val virtualNodes = mutableMapOf<Int, JsonObject>()
        val nodesArray = graph.getAsJsonArray("nodes") ?: JsonArray()

        nodesArray.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val idStr = node.get("id").asString
            val id = idStr.toIntOrNull() ?: return@forEach
            val type = node.get("type").asString
            val nodeDef = objectInfo.dynamicNodes.get(type)?.asJsonObject

            if (nodeDef == null && type in VIRTUAL_TYPES) {
                virtualNodes[id] = node
                return@forEach
            }

            // Check if it's a Phantom Node
            if (nodeDef == null) {
                val isManualLoadImage = type == "LoadImage" || type == "ETN_LoadImageBase64"
                
                val hasWidgets = node.has("widgets_values") && 
                                 node.get("widgets_values").isJsonArray && 
                                 node.get("widgets_values").asJsonArray.size() > 0
                
                val hasKeyedInputs = node.has("inputs") && node.get("inputs").isJsonObject

                val isUuidType = type.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))

                val isSubgraph = definitions.containsKey(type)

                val hasOutputs = nodesWithOutputs.contains(id)

                // Candidate for skipping/flattening if missing metadata and:
                // 1. Is UUID (frontend-only)
                // 2. OR has NO content (no widgets, no keyed inputs)
                val isContentless = !hasWidgets && !hasKeyedInputs
                
                if ((isUuidType || isContentless) && !isManualLoadImage && !isSubgraph) {
                    val inputSlots = node.get("inputs")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
                    val linkedCount = inputSlots.count { linkOfSlot(it) != null }

                    // A: If it has inputs, we can potentially flatten/bypass it
                    if (linkedCount > 0) {
                        println("CONVERT_DEBUG: Node $id ($type): Flattening candidate (found $linkedCount inputs)")
                        phantomNodeInputs[id] = inputSlots
                    }
                    // B: If it has NO inputs AND NO outputs, it's a dead-end shell -> skip it
                    else if (!hasOutputs) {
                        println("CONVERT_DEBUG: Node $id ($type): Dead-end shell detection. Skipping generation.")
                        phantomNodeInputs[id] = JsonArray() // Mark for skipping in processing loop
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

        // Node modes (LiteGraph): 2 = muted (never executes), 4 = bypassed (outputs are replaced by a
        // type-matching input). The editor excludes both from the API prompt; do the same.
        val mutedNodes = mutableSetOf<Int>()
        val bypassedNodes = mutableMapOf<Int, JsonObject>()
        nodesArray.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val nodeId = node.get("id").asString.toIntOrNull() ?: return@forEach
            when (if (node.has("mode") && node.get("mode").isJsonPrimitive) node.get("mode").asInt else 0) {
                2 -> mutedNodes.add(nodeId)
                4 -> bypassedNodes[nodeId] = node
            }
        }

        fun inputsOf(node: JsonObject): JsonArray =
            node.get("inputs")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

        fun inputLinkAt(inputsArr: JsonArray, index: Int): Int? =
            if (index in 0 until inputsArr.size()) linkOfSlot(inputsArr[index]) else null

        // Which input replaces output [slot] of a bypassed node, or -1 (frontend ExecutableNodeDTO._getBypassSlotIndex).
        // [targetType] is the type of the input at the end of the chain, not of this node's output.
        fun bypassSlotIndex(node: JsonObject, slot: Int, targetType: String?): Int {
            val inputsArr = inputsOf(node)
            if (targetType.isNullOrEmpty() || targetType == "*") return if (inputsArr.size() > slot) slot else 0
            val outputsArr = node.get("outputs")?.takeIf { it.isJsonArray }?.asJsonArray
            val outType = if (outputsArr != null && slot < outputsArr.size()) slotType(outputsArr[slot]) else null
            fun fits(input: JsonElement) = slotType(input).let { isValidConnection(it, outType) && isValidConnection(it, targetType) }

            if (slot < inputsArr.size() && fits(inputsArr[slot])) return slot
            val exact = inputsArr.indexOfFirst { slotType(it) == targetType }
            if (exact != -1) return exact
            return inputsArr.indexOfFirst { fits(it) }
        }

        // Resolves a link to the real node output that feeds it, following the frontend's
        // ExecutableNodeDTO.resolveOutput: muted sources drop, bypassed and frontend-only nodes pass through.
        fun resolveRealSource(initialLinkId: Int, targetType: String?, visited: MutableSet<Int> = mutableSetOf()): Pair<Int, Int>? {
            if (visited.contains(initialLinkId)) return null // Cycle detected
            visited.add(initialLinkId)

            if (!linkMap.containsKey(initialLinkId)) return null
            val (sourceId, sourceSlot) = linkMap[initialLinkId]!!

            if (mutedNodes.contains(sourceId)) {
                println("CONVERT_DEBUG: Link $initialLinkId comes from muted node $sourceId; dropping")
                return null
            }
            bypassedNodes[sourceId]?.let { bypassed ->
                val index = bypassSlotIndex(bypassed, sourceSlot, targetType)
                val replacement = inputLinkAt(inputsOf(bypassed), index)
                println("CONVERT_DEBUG: Node $sourceId is bypassed; output $sourceSlot ($targetType) -> input $index, link $replacement")
                return if (replacement != null) resolveRealSource(replacement, targetType, visited) else null
            }

            // Frontend-only nodes pass output [slot] through from the input at the same index (getInputLink)
            virtualNodes[sourceId]?.let { virtual ->
                val next = inputLinkAt(inputsOf(virtual), sourceSlot)
                println("CONVERT_DEBUG: Node $sourceId is frontend-only; output $sourceSlot -> input link $next")
                return if (next != null) resolveRealSource(next, targetType, visited) else null
            }

            // Unknown pass-through node: the same-index input, else the first linked input of a compatible type
            phantomNodeInputs[sourceId]?.let { inputsArr ->
                val next = inputLinkAt(inputsArr, sourceSlot)
                    ?: inputsArr.firstOrNull { linkOfSlot(it) != null && isValidConnection(slotType(it), targetType) }
                        ?.let { linkOfSlot(it) }
                println("CONVERT_DEBUG: Flattening - Node $sourceId is phantom, bypassing to input link $next")
                return if (next != null) resolveRealSource(next, targetType, visited) else null
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
            
            if (mutedNodes.contains(id) || bypassedNodes.containsKey(id) || virtualNodes.containsKey(id)) {
                println("CONVERT_DEBUG: Node $id ($type): skipping generation (muted/bypassed/frontend-only)")
                return@forEach
            }

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

 
                fun specOf(key: String): JsonArray? = (required?.get(key) ?: optional?.get(key)) as? JsonArray

                fun kindOf(spec: JsonArray?): String? = specKind(spec)

                // Helper to check compatibility
                fun isCompatible(value: com.google.gson.JsonElement, expectedType: String?): Boolean {
                    if (expectedType == null) return true
                    return when (expectedType) {
                        "INT" -> value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || (value.asJsonPrimitive.isString && value.asString.toLongOrNull() != null))
                        "FLOAT" -> value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || (value.asJsonPrimitive.isString && value.asString.toDoubleOrNull() != null))
                        "STRING" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                        "BOOLEAN" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
                        "COMBO" -> value.isJsonPrimitive
                        DYNAMIC_COMBO -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                        else -> true
                    }
                }

                fun findNextCompatibleWidget(spec: JsonArray?): com.google.gson.JsonElement? {
                    if (graphWidgets == null) return null
                    val expectedType = kindOf(spec)
                    while (widgetIndex < graphWidgets.size()) {
                        val widget = graphWidgets[widgetIndex]
                        widgetIndex++
                        if (isCompatible(widget, expectedType)) return widget
                    }
                    return null
                }

                /**
                 * Extra widgets_values slots the frontend stores after this input's widget: a
                 * control_after_generate combo (1 for INT/FLOAT; 2 for COMBO, plus control_filter_list).
                 * INT inputs named seed/noise_seed get one unless the spec says otherwise (useIntWidget.ts).
                 */
                fun controlSlots(path: String, spec: JsonArray?): Int {
                    val config = spec?.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject
                    val flag = config?.get("control_after_generate")
                    val enabled = when {
                        flag == null || flag.isJsonNull -> null
                        flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean -> flag.asBoolean
                        flag.isJsonPrimitive && flag.asJsonPrimitive.isString -> flag.asString.isNotEmpty()
                        else -> true
                    }
                    return when (kindOf(spec)) {
                        "INT" -> if (enabled ?: (path.substringAfterLast('.') in SEED_NAMES)) 1 else 0
                        "FLOAT" -> if (enabled == true) 1 else 0
                        "COMBO" -> if (enabled == true) 2 else 0
                        else -> 0
                    }
                }

                // Only skip what really is a control value: older saves may lack the control widget.
                fun skipControlWidgets(count: Int) {
                    if (count == 0 || graphWidgets == null || widgetIndex >= graphWidgets.size()) return
                    val control = graphWidgets[widgetIndex]
                    if (!control.isJsonPrimitive || control.asString !in CONTROL_VALUES) return
                    widgetIndex++
                    if (count == 2 && widgetIndex < graphWidgets.size()) {
                        val filter = graphWidgets[widgetIndex]
                        if (filter.isJsonPrimitive && filter.asJsonPrimitive.isString) widgetIndex++
                    }
                }

                // Newer frontends also save widget values by name, which survives node definition changes
                val named = node.get("widgets_values_named")?.takeIf { it.isJsonObject }?.asJsonObject

                // Values promoted from an enclosing subgraph instance replace this node's own widget values
                val promoted = node.getAsJsonObject(PROMOTED_WIDGETS)

                if (isManualLoadImage) {
                    val image = promoted?.get("image")
                        ?: graphWidgets?.takeIf { it.size() > 0 }?.get(0)
                    if (image != null) inputs.add("image", image)
                }

                // Custom frontend extensions can store localized display labels (e.g. "By filename")
                // in widgets_values for combo inputs; /object_info only knows the real values.
                fun resolveComboValue(key: String, spec: JsonArray?, value: com.google.gson.JsonElement): com.google.gson.JsonElement {
                    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString || spec == null) return value
                    val options = comboOptions(spec) ?: return value
                    val label = value.asString
                    if (options.isEmpty() || options.contains(label)) return value

                    fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
                    val resolved: String? =
                        options.firstOrNull { it.equals(label, ignoreCase = true) }
                            ?: run {
                                val nl = norm(label)
                                val hits = options.filter { norm(it).isNotEmpty() && nl.contains(norm(it)) }
                                val longest = hits.maxOfOrNull { norm(it).length }
                                hits.filter { norm(it).length == longest }.singleOrNull()
                            }
                            ?: run {
                                val cfg = if (spec.size() > 1 && spec[1].isJsonObject) spec[1].asJsonObject else null
                                val d = cfg?.get("default")
                                if (d != null && d.isJsonPrimitive && d.asJsonPrimitive.isString && options.contains(d.asString)) d.asString else null
                            }
                    if (resolved == null) return value
                    println("CONVERT_DEBUG: Node $id: combo '$key' value '$label' resolved to '$resolved'")
                    return com.google.gson.JsonPrimitive(resolved)
                }

                // Returns false when the link doesn't reach a real node output
                fun addLink(key: String, linkId: Int): Boolean {
                    val resolved = resolveRealSource(linkId, linkTypeMap[linkId])
                    if (resolved != null) {
                        val linkArray = JsonArray()
                        linkArray.add(resolved.first.toString())
                        linkArray.add(resolved.second)
                        inputs.add(key, linkArray)
                        return true
                    }
                    println("CONVERT_DEBUG: Warn: Node $id: key '$key' link $linkId resolved to null (broken chain?)")
                    return false
                }

                // Promoted (Phase 93) > saved value > the frontend's default for a widget the node gained
                // after the workflow was saved. A named map lists every saved widget, so when it exists a
                // missing name means a newer widget, and the (possibly shifted) positional value is not used.
                fun widgetValueFor(path: String, spec: JsonArray?, saved: JsonElement?, isWidget: Boolean): JsonElement? {
                    val stored = if (named != null) named.get(path)?.takeIf { !it.isJsonNull } else saved
                    val widget = promoted?.get(path)
                        ?: stored
                        ?: (if (isWidget) frontendDefault(spec)?.also {
                            println("CONVERT_DEBUG: Node $id: no saved value for '$path', using default $it")
                        } else null)
                        ?: return null
                    return resolveComboValue(path, spec, widget)
                }

                fun graphSlot(name: String): JsonObject? =
                    graphInputs.firstOrNull { it.asJsonObject.get("name").asString == name }?.asJsonObject

                fun linkOf(slot: JsonObject?): Int? =
                    slot?.get("link")?.takeIf { !it.isJsonNull }?.asInt

                /**
                 * Emits one input. [path] is the API key: a top-level input name, or a dotted
                 * "parent.sub" name for inputs of a selected COMFY_DYNAMICCOMBO_V3 option.
                 */
                fun emitInput(path: String, spec: JsonArray?, isSubInput: Boolean) {
                    val kind = kindOf(spec)
                    if (kind == "COMFY_AUTOGROW_V3") {
                        // Graph JSON expands autogrow groups into dotted slots ("values.a"); copy linked
                        // ones verbatim and never let the group key consume a widget value.
                        graphInputs.forEach { el ->
                            val slot = el.asJsonObject
                            val name = slot.get("name").asString
                            val link = linkOf(slot)
                            if (name.startsWith("$path.") && link != null) addLink(name, link)
                        }
                        return
                    }
                    if (kind == IMAGE_COMPARE) {
                        // Display-only widget: never saved in widgets_values, sent as ["", ""] (useImageCompareWidget.ts)
                        inputs.add(path, JsonArray().apply { add(""); add("") })
                        return
                    }
                    val slot = graphSlot(path)
                    val linkId = linkOf(slot)
                    if (linkId != null) {
                        val linked = addLink(path, linkId)
                        // A widget converted to an input keeps its slot in widgets_values even when linked.
                        if (slot?.has("widget") == true) {
                            val saved = findNextCompatibleWidget(spec)
                            skipControlWidgets(controlSlots(path, spec))
                            // The frontend writes every widget value and only replaces it with a link that
                            // resolves (graphToPrompt), so a broken link still sends the widget's value.
                            if (!linked) widgetValueFor(path, spec, saved, hasWidget(spec))?.let { inputs.add(path, it) }
                        }
                        return
                    }
                    // In the frontend only widget-type inputs get a widget; sockets have no widgets_values slot.
                    val isWidget = hasWidget(spec)
                    if (!isWidget && (isSubInput || (slot != null && !slot.has("widget")) || isForcedInput(spec))) return

                    // Always consume the widgets_values slot so later widgets stay aligned
                    val saved = findNextCompatibleWidget(spec)
                    if (saved != null) skipControlWidgets(controlSlots(path, spec))
                    val value = widgetValueFor(path, spec, saved, isWidget) ?: return
                    inputs.add(path, value)

                    if (kind == DYNAMIC_COMBO) {
                        // The selected option's inputs follow the combo in widgets_values, depth-first
                        // (frontend dynamicWidgets.ts); the server reads them as "path.sub" (comfy_api _io.py).
                        val selected = value.takeIf { it.isJsonPrimitive }?.asString
                        val option = spec?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject
                            ?.getAsJsonArray("options")?.map { it.asJsonObject }
                            ?.firstOrNull { it.get("key")?.asString == selected }
                        if (option == null) {
                            println("CONVERT_DEBUG: Node $id: dynamic combo '$path' value '$selected' matches no option")
                            return
                        }
                        val optionInputs = option.getAsJsonObject("inputs")
                        for (section in listOf("required", "optional")) {
                            optionInputs?.getAsJsonObject(section)?.entrySet()?.forEach { (name, subSpec) ->
                                emitInput("$path.$name", subSpec as? JsonArray, isSubInput = true)
                            }
                        }
                    }
                }

                for (key in allInputKeys) {
                    emitInput(key, specOf(key), isSubInput = false)
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
                            val resolved = resolveRealSource(linkId, linkTypeMap[linkId])
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
        // Wrapper ID -> instance input slot -> subgraph input index (instances may list a subset, reordered)
        val wrapperSlotMaps = mutableMapOf<Int, Map<Int, Int>>()
        
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
            val isSubgraph = def != null
            
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
                val slotMap = mapInstanceInputs(node, definition)
                wrapperSlotMaps[id] = slotMap
                val promotedOverrides = promotedWidgetOverrides(node, definition, slotMap, idRemapper)
                wrapperOutputRedirects[id] = outputRedirects
                
                // 4. Add Pure Internal Nodes (excluding Input -10/Output -20)
                // AND update their inputs[].link values using linkIdRemapper
                remappedNodes.forEach { internalNode ->
                    val currentId = intId(internalNode)
                    if (currentId != remappedInputId && currentId != remappedOutputId) {
                        // Update this node's input links
                        val updatedNode = updateNodeInputLinks(internalNode, linkIdRemapper)
                        promotedOverrides[currentId]?.let { overrides ->
                            val merged = updatedNode.getAsJsonObject(PROMOTED_WIDGETS) ?: JsonObject()
                            overrides.entrySet().forEach { (name, value) -> merged.add(name, value) }
                            updatedNode.add(PROMOTED_WIDGETS, merged)
                        }
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
                val destRedirects = wrapperSlotMaps[targetId]?.get(targetSlot)
                    ?.let { wrapperInputRedirects[targetId]?.get(it) }
                
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
                val destRedirects = wrapperSlotMaps[targetId]?.get(targetSlot)
                    ?.let { wrapperInputRedirects[targetId]?.get(it) }
                
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

    /**
     * Node types that exist only in the ComfyUI frontend (isVirtualNode): Reroute, PrimitiveNode,
     * notes, and KJNodes SetNode/GetNode. Used only when /object_info doesn't define the type.
     */
    private val VIRTUAL_TYPES = setOf("Reroute", "PrimitiveNode", "Note", "MarkdownNote", "SetNode", "GetNode")

    private fun linkOfSlot(slot: JsonElement): Int? =
        slot.takeIf { it.isJsonObject }?.asJsonObject?.get("link")?.takeIf { !it.isJsonNull }?.asInt

    private fun slotType(slot: JsonElement): String? =
        slot.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { it.isJsonPrimitive }?.asString

    /** LiteGraph.isValidConnection: "" or "*" matches anything; otherwise any shared type of a comma list. */
    internal fun isValidConnection(a: String?, b: String?): Boolean {
        if (a.isNullOrEmpty() || a == "*" || b.isNullOrEmpty() || b == "*" || a == b) return true
        val left = a.lowercase().split(",").map { it.trim() }
        val right = b.lowercase().split(",").map { it.trim() }.toSet()
        return left.any { it in right }
    }

    private const val DYNAMIC_COMBO = "COMFY_DYNAMICCOMBO_V3"

    private const val IMAGE_COMPARE = "IMAGECOMPARE"
    private val SEED_NAMES = setOf("seed", "noise_seed")
    private val CONTROL_VALUES = setOf("fixed", "increment", "decrement", "randomize", "increment-wrap")

    /** Input kinds the frontend renders as widgets (and so store a widgets_values entry). */
    private val WIDGET_KINDS = setOf("INT", "FLOAT", "STRING", "BOOLEAN", "COMBO", DYNAMIC_COMBO)

    private fun specConfig(spec: JsonArray?): JsonObject? =
        spec?.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject

    /** forceInput (or the deprecated defaultInput) turns a widget type into a socket-only input. */
    private fun isForcedInput(spec: JsonArray?): Boolean {
        val config = specConfig(spec) ?: return false
        return listOf("forceInput", "defaultInput").any { key ->
            config.get(key)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
        }
    }

    /** The widget kind of an input spec: legacy lists are COMBO, and a config widgetType overrides the type. */
    private fun specKind(spec: JsonArray?): String? {
        val first = spec?.takeIf { it.size() > 0 }?.get(0) ?: return null
        specConfig(spec)?.get("widgetType")?.takeIf { it.isJsonPrimitive }?.let { return it.asString }
        return when {
            first.isJsonPrimitive && first.asJsonPrimitive.isString -> if (first.asString == "COMBO") "COMBO" else first.asString
            first.isJsonArray -> "COMBO"
            else -> null
        }
    }

    /** Whether the frontend renders this input as a widget (and so stores a widgets_values entry). */
    private fun hasWidget(spec: JsonArray?): Boolean = specKind(spec) in WIDGET_KINDS && !isForcedInput(spec)

    /** The value a newly created frontend widget starts with (use{Int,Float,String,Boolean,Combo}Widget.ts). */
    private fun frontendDefault(spec: JsonArray?): JsonElement? {
        val default = specConfig(spec)?.get("default")?.takeIf { !it.isJsonNull }
        return when (specKind(spec)) {
            "INT", "FLOAT" -> default ?: JsonPrimitive(0)
            "STRING" -> default ?: JsonPrimitive("")
            "BOOLEAN" -> default ?: JsonPrimitive(false)
            "COMBO", DYNAMIC_COMBO -> {
                val options = spec?.let { comboOptions(it) }.orEmpty()
                val chosen = default?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it in options } ?: options.firstOrNull()
                chosen?.let { JsonPrimitive(it) }
            }
            else -> null
        }
    }

    /** Valid values of a combo spec: legacy [[options], {...}], V3 ["COMBO", {options}], or dynamic option keys. */
    private fun comboOptions(spec: JsonArray): List<String>? {
        val first = spec.takeIf { it.size() > 0 }?.get(0) ?: return null
        val config = spec.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject
        fun strings(arr: JsonArray?) = arr?.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.map { it.asString }
        return when {
            first.isJsonArray -> strings(first.asJsonArray)
            first.asString == "COMBO" -> strings(config?.getAsJsonArray("options"))
            first.asString == DYNAMIC_COMBO ->
                config?.getAsJsonArray("options")?.mapNotNull { it.asJsonObject.get("key")?.asString }
            else -> null
        }
    }

    /** Graph-node property holding widget values promoted from an enclosing subgraph instance. Never sent to the API. */
    private const val PROMOTED_WIDGETS = "__promoted_widgets"

    /**
     * Widget values a subgraph instance supplies to its interior nodes, keyed by remapped interior node ID,
     * then input name. Mirrors the ComfyUI frontend (SubgraphNode._setWidget/_applyPromotedWidgetValues and
     * ExecutableNodeDTO.resolveInput): a subgraph input is a promoted widget when the first of its links that
     * reaches an interior input with a widget exists; the instance's widgets_values are assigned to those
     * inputs by position (proxyWidgetErrorQuarantine host values win), and a value only applies when no
     * external link feeds that input. Empty widgets_values keeps the interior values.
     */
    internal fun promotedWidgetOverrides(
        instance: JsonObject,
        definition: SubgraphDefinition,
        slotMap: Map<Int, Int>,
        idRemapper: Map<Int, Int>
    ): Map<Int, JsonObject> {
        val linksById = definition.links.associateBy { it[0].asInt }
        val nodesById = definition.nodes.associateBy { intId(it) }
        val instanceWidgets = instance.get("widgets_values")?.takeIf { it.isJsonArray }?.asJsonArray
        val inheritedValues = instance.getAsJsonObject(PROMOTED_WIDGETS) // this instance is itself promoted into
        val quarantine = mutableMapOf<String, com.google.gson.JsonElement>()
        instance.getAsJsonObject("properties")?.get("proxyWidgetErrorQuarantine")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { entryEl ->
                val entry = entryEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val original = entry.get("originalEntry")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
                if (original.size() >= 2 && original[0].asString == "-1" && entry.has("hostValue")) {
                    quarantine[original[1].asString] = entry.get("hostValue")
                }
            }
        val externallyLinked = mutableSetOf<Int>()
        instance.getAsJsonArray("inputs")?.forEachIndexed { slot, inputEl ->
            val link = inputEl.asJsonObject.get("link")
            if (link != null && !link.isJsonNull) slotMap[slot]?.let { externallyLinked += it }
        }

        val result = mutableMapOf<Int, JsonObject>()
        var valueIndex = 0
        definition.inputs.forEachIndexed { index, subgraphInput ->
            // Every interior input fed by this subgraph input: (node ID, input name, has a widget)
            val targets = subgraphInput.linkIds.mapNotNull { linkId ->
                val link = linksById[linkId] ?: return@mapNotNull null
                val target = nodesById[link[3].asInt] ?: return@mapNotNull null
                val slot = target.getAsJsonArray("inputs")?.let { ins ->
                    link[4].asInt.takeIf { it < ins.size() }?.let { ins[it].asJsonObject }
                } ?: return@mapNotNull null
                Triple(intId(target), slot.get("name").asString, slot.has("widget"))
            }
            if (targets.none { it.third }) return@forEachIndexed // not a promoted widget
            val positional = instanceWidgets?.let { if (valueIndex < it.size()) it[valueIndex] else null }
            valueIndex++
            val value = inheritedValues?.get(subgraphInput.name) ?: quarantine[subgraphInput.name] ?: positional
            if (value == null || value.isJsonNull || index in externallyLinked) return@forEachIndexed
            // The frontend resolves every interior input of this subgraph input to the promoted value
            targets.forEach { (targetId, inputName, _) ->
                val remapped = idRemapper[targetId] ?: targetId
                result.getOrPut(remapped) { JsonObject() }.add(inputName, value)
            }
        }
        return result
    }

    /**
     * Maps each instance input slot to its subgraph input index, like the ComfyUI frontend
     * (SubgraphNode._rebindInputSubgraphSlots): match by name + type first, then by name, using each
     * subgraph input at most once. A serialized instance can list only some inputs, in any order.
     */
    internal fun mapInstanceInputs(instance: JsonObject, definition: SubgraphDefinition): Map<Int, Int> {
        val instanceInputs = instance.getAsJsonArray("inputs")?.map { it.asJsonObject } ?: return emptyMap()
        // Definitions without declared inputs (older/synthetic graphs): keep positional slots.
        if (definition.inputs.isEmpty()) return instanceInputs.indices.associateWith { it }

        fun str(obj: JsonObject, key: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        val assigned = mutableSetOf<Int>()
        val result = mutableMapOf<Int, Int>()
        fun match(slot: Int, predicate: (SubgraphInput) -> Boolean) {
            val index = definition.inputs.indices.firstOrNull { it !in assigned && predicate(definition.inputs[it]) }
            if (index != null) {
                result[slot] = index
                assigned += index
            }
        }
        instanceInputs.forEachIndexed { slot, input ->
            val name = str(input, "name")
            val type = str(input, "type")
            match(slot) { it.name == name && it.type == type }
        }
        instanceInputs.forEachIndexed { slot, input ->
            if (slot in result) return@forEachIndexed
            val name = str(input, "name")
            match(slot) { it.name == name }
            if (slot !in result) println("SUBGRAPH_DEBUG: Instance input '$name' matches no input of subgraph ${definition.id}; ignored")
        }
        return result
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
