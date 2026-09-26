package com.example.comfyui_remote.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Checks an API-format prompt against a server's /object_info the way ComfyUI's execution.py does
 * (validate_prompt / validate_inputs), so the app can warn before queueing (Phase 91).
 *
 * The server skips range and list checks for inputs a node validates itself, which /object_info does not
 * reveal, so those findings are warnings; structural problems the server always rejects are errors.
 * A file name (model, image...) missing from the server's list is an error too: loaders use the list
 * check, and nodes that validate files themselves (LoadImage) reject a missing file anyway.
 */
object PromptValidator {

    enum class Kind(val severity: Severity) {
        MISSING_NODE_TYPE(Severity.ERROR),
        NO_OUTPUT_NODE(Severity.ERROR),
        BAD_LINK(Severity.ERROR),
        TYPE_MISMATCH(Severity.ERROR),
        REQUIRED_INPUT_MISSING(Severity.ERROR),
        INVALID_VALUE_TYPE(Severity.WARNING),
        OUT_OF_RANGE(Severity.WARNING),
        VALUE_NOT_IN_LIST(Severity.WARNING)
    }

    enum class Severity { ERROR, WARNING }

    data class Issue(
        val nodeId: String,
        val classType: String,
        val nodeTitle: String,
        val kind: Kind,
        val inputName: String?,
        val message: String,
        val severity: Severity = kind.severity
    ) {
        override fun toString() = "${kind.name} node $nodeId ($classType)${inputName?.let { ".$it" } ?: ""}: $message"
    }

    private const val DYNAMIC_COMBO = "COMFY_DYNAMICCOMBO_V3"
    private const val AUTOGROW = "COMFY_AUTOGROW_V3"
    private const val MATCH_TYPE = "COMFY_MATCHTYPE_V3"

    // Values naming a server file (model, image, video, 3D asset...)
    private val FILE_VALUE = Regex(
        """[\\/]|\.(safetensors|ckpt|pt|pth|bin|gguf|onnx|sft|png|jpe?g|webp|gif|mp4|webm|mov|wav|mp3|flac|glb|gltf|fbx|obj|ply|stl|usdz|spz|splat)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * @param checkFileValues check file-name values (model, image...) against the server's lists
     * @param reachableOnly validate only nodes that feed an output node, as the server does
     * @param valueChecks also check value conversion and min/max ranges
     */
    fun validate(
        prompt: JsonObject,
        objectInfo: JsonObject,
        checkFileValues: Boolean = true,
        reachableOnly: Boolean = true,
        valueChecks: Boolean = true
    ): List<Issue> {
        val issues = mutableListOf<Issue>()

        fun node(id: String) = prompt.get(id)?.takeIf { it.isJsonObject }?.asJsonObject
        fun classOf(node: JsonObject) = node.get("class_type")?.takeIf { it.isJsonPrimitive }?.asString ?: "<none>"
        fun titleOf(node: JsonObject) =
            node.getAsJsonObject("_meta")?.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: classOf(node)
        fun issue(id: String, node: JsonObject, kind: Kind, input: String?, message: String, severity: Severity = kind.severity) {
            issues += Issue(id, classOf(node), titleOf(node), kind, input, message, severity)
        }

        // Missing node types are reported for every node: the server rejects the whole prompt
        for ((id, el) in prompt.entrySet()) {
            val n = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (objectInfo.getAsJsonObject(classOf(n)) == null) {
                issue(id, n, Kind.MISSING_NODE_TYPE, null, "Node type ${classOf(n)} is not installed on the server")
            }
        }

        val toValidate: Collection<String> = if (reachableOnly) {
            val outputs = prompt.entrySet().filter { (_, el) ->
                el.isJsonObject && objectInfo.getAsJsonObject(classOf(el.asJsonObject))
                    ?.get("output_node")?.let { it.isJsonPrimitive && it.asBoolean } == true
            }.map { it.key }
            if (outputs.isEmpty()) {
                issues += Issue("", "", "Workflow", Kind.NO_OUTPUT_NODE, null, "Prompt has no outputs")
                return issues
            }
            val seen = linkedSetOf<String>()
            val stack = ArrayDeque(outputs)
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                if (!seen.add(id)) continue
                node(id)?.getAsJsonObject("inputs")?.entrySet()?.forEach { (_, v) ->
                    asLink(v)?.let { (src, _) -> if (node(src) != null) stack.addLast(src) }
                }
            }
            seen
        } else {
            prompt.keySet()
        }

        for (id in toValidate) {
            val n = node(id) ?: continue
            val def = objectInfo.getAsJsonObject(classOf(n)) ?: continue // reported above
            val inputs = n.getAsJsonObject("inputs") ?: JsonObject()
            val (specs, required) = effectiveSpecs(def, inputs)

            for ((key, value) in inputs.entrySet()) {
                val spec = specs[key]
                if (value.isJsonArray) {
                    val link = asLink(value)
                    if (link == null) {
                        issue(id, n, Kind.BAD_LINK, key, "Bad linked input, must be a length-2 list of [node_id, slot_index]")
                        continue
                    }
                    val (srcId, slot) = link
                    val src = node(srcId)
                    if (src == null) {
                        issue(id, n, Kind.BAD_LINK, key, "Linked node $srcId is not in the prompt")
                        continue
                    }
                    val srcOutputs = objectInfo.getAsJsonObject(classOf(src))?.getAsJsonArray("output") ?: continue
                    if (slot !in 0 until srcOutputs.size()) {
                        issue(id, n, Kind.BAD_LINK, key, "Linked node $srcId has no output $slot")
                        continue
                    }
                    val declared = spec?.let { declaredType(it) } ?: continue
                    val produced = srcOutputs[slot].asString
                    if (!compatible(produced, declared)) {
                        issue(id, n, Kind.TYPE_MISMATCH, key,
                            "Return type mismatch between linked nodes: received $produced, expected $declared")
                    }
                    continue
                }
                if (spec == null || !value.isJsonPrimitive) continue
                if (valueChecks) valueIssue(spec, value)?.let { (kind, message) -> issue(id, n, kind, key, message) }
                val options = GraphToApiConverter.comboOptions(spec) ?: continue
                val text = value.asString
                val isFile = FILE_VALUE.containsMatchIn(text)
                if (options.isNotEmpty() && text !in options && (checkFileValues || !isFile)) {
                    val shown = options.take(5).joinToString(", ") + if (options.size > 5) ", …" else ""
                    if (isFile) {
                        issue(id, n, Kind.VALUE_NOT_IN_LIST, key, "File not on the server: '$text' not in [$shown]", Severity.ERROR)
                    } else {
                        issue(id, n, Kind.VALUE_NOT_IN_LIST, key, "Value not in list: '$text' not in [$shown]")
                    }
                }
            }

            for (key in required) {
                if (specs[key]?.let { isOptionalAutogrow(it) } == true) continue
                if (!inputs.has(key) && inputs.keySet().none { it.startsWith("$key.") }) {
                    issue(id, n, Kind.REQUIRED_INPUT_MISSING, key, "Required input is missing")
                }
            }
        }
        return issues
    }

    /** The server's conversion and min/max checks for INT/FLOAT inputs. */
    private fun valueIssue(spec: JsonArray, value: JsonElement): Pair<Kind, String>? {
        val type = spec[0].takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (type != "INT" && type != "FLOAT") return null
        val number = value.asString.toDoubleOrNull()
            ?: return Kind.INVALID_VALUE_TYPE to "Failed to convert an input value to a $type value: '${value.asString}'"
        val config = spec.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun bound(key: String) = config.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
        val shown = value.asString
        bound("min")?.let { if (number < it) return Kind.OUT_OF_RANGE to "Value $shown smaller than min of ${config.get("min")}" }
        bound("max")?.let { if (number > it) return Kind.OUT_OF_RANGE to "Value $shown bigger than max of ${config.get("max")}" }
        return null
    }

    /**
     * Input specs in effect for a node, keyed by API input name, plus the required names. A
     * COMFY_DYNAMICCOMBO_V3 input adds its selected option's inputs as "key.sub", recursively, like the
     * server (comfy_api _io.py DynamicCombo).
     */
    private fun effectiveSpecs(def: JsonObject, inputs: JsonObject): Pair<Map<String, JsonArray>, Set<String>> {
        val specs = linkedMapOf<String, JsonArray>()
        val required = linkedSetOf<String>()

        fun add(prefix: String?, group: JsonObject?) {
            for (section in listOf("required", "optional")) {
                group?.getAsJsonObject(section)?.entrySet()?.forEach { (name, v) ->
                    if (!v.isJsonArray || v.asJsonArray.size() == 0) return@forEach
                    val path = if (prefix == null) name else "$prefix.$name"
                    val spec = v.asJsonArray
                    specs[path] = spec
                    if (section == "required") required += path
                    if (spec[0].isJsonPrimitive && spec[0].asString == DYNAMIC_COMBO) {
                        val selected = inputs.get(path)?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
                        val option = spec.takeIf { it.size() > 1 }?.get(1)?.asJsonObject?.getAsJsonArray("options")
                            ?.map { it.asJsonObject }?.firstOrNull { it.get("key")?.asString == selected }
                            ?: return@forEach
                        add(path, option.getAsJsonObject("inputs"))
                    }
                }
            }
        }
        add(null, def.getAsJsonObject("input"))
        return specs to required
    }

    /** An autogrow group with template.min == 0 may have no entries at all. */
    private fun isOptionalAutogrow(spec: JsonArray): Boolean {
        if (!spec[0].isJsonPrimitive || spec[0].asString != AUTOGROW || spec.size() < 2) return false
        val min = spec[1].asJsonObject.getAsJsonObject("template")?.get("min")
        return min != null && min.isJsonPrimitive && min.asInt == 0
    }

    /** A link is ["nodeId", slot]. */
    private fun asLink(value: JsonElement): Pair<String, Int>? {
        if (!value.isJsonArray) return null
        val arr = value.asJsonArray
        if (arr.size() != 2) return null
        val a = arr[0]
        val b = arr[1]
        if (!a.isJsonPrimitive || !b.isJsonPrimitive || !b.asJsonPrimitive.isNumber) return null
        return a.asString to b.asInt
    }

    /** Declared input type, or null when any link is acceptable (combos, dynamic and match types). */
    private fun declaredType(spec: JsonArray): String? {
        val first = spec[0]
        if (!first.isJsonPrimitive) return null // legacy combo [[options], {...}]
        return when (val type = first.asString) {
            "COMBO", MATCH_TYPE, AUTOGROW, DYNAMIC_COMBO -> null
            else -> type
        }
    }

    /** Whether a linked output type fits an input type (validate_node_input: "*", comma lists, match types). */
    fun compatible(produced: String, declared: String): Boolean {
        // Match-type outputs (e.g. ComfySwitchNode) take their input's type at runtime.
        if (produced == MATCH_TYPE) return true
        return GraphToApiConverter.typesCompatible(produced, declared)
    }
}
