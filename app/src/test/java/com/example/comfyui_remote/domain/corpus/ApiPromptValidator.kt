package com.example.comfyui_remote.domain.corpus

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Structural checks on an API-format prompt produced by GraphToApiConverter, against a
 * saved /object_info. No server needed. Check IDs (see .gsd/phases/89/89-CONTEXT.md, D-05):
 *  C1 unknown class_type, C2 dangling link, C3 link type mismatch, C4 missing required input,
 *  C5 invalid combo value (file-name values are not checked), C6 muted/bypassed node sent, C7 frontend-only node sent.
 */
object ApiPromptValidator {

    data class Violation(val check: String, val nodeId: String, val detail: String) {
        override fun toString() = "$check node $nodeId: $detail"
    }

    val FRONTEND_ONLY = setOf("Reroute", "PrimitiveNode", "Note", "MarkdownNote")

    // V3 dynamic input kinds: their API keys are dotted ("values.a", "model.max_tokens").
    private val DOTTED_KINDS = setOf("COMFY_AUTOGROW_V3", "COMFY_DYNAMICCOMBO_V3")
    private const val MATCH_TYPE = "COMFY_MATCHTYPE_V3"

    // Model and input file names depend on the server's files, which a snapshot can't know.
    private val FILE_VALUE = Regex("""[\\/]|\.(safetensors|ckpt|pt|pth|bin|gguf|onnx|sft|png|jpe?g|webp|gif|mp4|webm|mov|wav|mp3|flac|glb|gltf|fbx|obj|ply|stl|usdz|spz|splat)$""", RegexOption.IGNORE_CASE)

    fun validate(api: JsonObject, objectInfo: JsonObject, graph: JsonObject): List<Violation> {
        val out = mutableListOf<Violation>()

        val skipped = graph.getAsJsonArray("nodes")?.mapNotNull { el ->
            val n = el.asJsonObject
            val mode = n.get("mode")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            if (mode == 2 || mode == 4) n.get("id").asString else null
        }?.toSet().orEmpty()

        for ((id, nodeEl) in api.entrySet()) {
            val node = nodeEl.asJsonObject
            val classType = node.get("class_type")?.asString ?: "<none>"
            val inputs = node.getAsJsonObject("inputs") ?: JsonObject()

            if (id in skipped) out += Violation("C6", id, "$classType is muted/bypassed in the graph but was sent")
            if (classType in FRONTEND_ONLY) {
                out += Violation("C7", id, "frontend-only $classType was sent")
                continue
            }
            val def = objectInfo.getAsJsonObject(classType)
            if (def == null) {
                out += Violation("C1", id, "unknown class_type $classType")
                continue
            }
            val (specs, required) = effectiveSpecs(def, inputs)

            for ((key, value) in inputs.entrySet()) {
                val link = asLink(value)
                val spec = specs[key]
                if (link != null) {
                    val (srcId, slot) = link
                    val src = api.getAsJsonObject(srcId)
                    if (src == null) {
                        out += Violation("C2", id, "$classType.$key links to missing node $srcId")
                        continue
                    }
                    val srcOutputs = objectInfo.getAsJsonObject(src.get("class_type").asString)?.getAsJsonArray("output")
                        ?: continue // C1 reported on the source node itself
                    if (slot >= srcOutputs.size()) {
                        out += Violation("C2", id, "$classType.$key links to slot $slot of $srcId, which has ${srcOutputs.size()} outputs")
                        continue
                    }
                    val declared = spec?.let { declaredType(it) } ?: continue // dotted/unknown keys: no declared type
                    val produced = srcOutputs[slot].asString
                    if (!compatible(produced, declared)) {
                        out += Violation("C3", id, "$classType.$key expects $declared but $srcId:$slot gives $produced")
                    }
                } else if (spec != null) {
                    val options = comboOptions(spec) ?: continue
                    if (options.isNotEmpty() && value.isJsonPrimitive && value.asString !in options &&
                        !FILE_VALUE.containsMatchIn(value.asString)
                    ) {
                        out += Violation("C5", id, "$classType.$key = \"${value.asString}\" not in ${options.take(5)}")
                    }
                }
            }

            for (key in required) {
                if (specs[key]?.let { isOptionalAutogrow(it) } == true) continue
                if (!inputs.has(key) && inputs.keySet().none { it.startsWith("$key.") }) {
                    out += Violation("C4", id, "$classType is missing required input $key")
                }
            }
        }
        return out
    }

    /**
     * Input specs in effect for this node, keyed by API input name, plus the required names. A
     * COMFY_DYNAMICCOMBO_V3 input adds its selected option's inputs as "key.sub", recursively, like the
     * server (comfy_api _io.py DynamicCombo). An unknown option adds nothing; C5 reports the key.
     */
    private fun effectiveSpecs(def: JsonObject, inputs: JsonObject): Pair<Map<String, JsonArray>, Set<String>> {
        val specs = linkedMapOf<String, JsonArray>()
        val required = linkedSetOf<String>()

        fun add(prefix: String?, group: JsonObject?) {
            for (section in listOf("required", "optional")) {
                group?.getAsJsonObject(section)?.entrySet()?.forEach { (name, v) ->
                    if (!v.isJsonArray) return@forEach
                    val path = if (prefix == null) name else "$prefix.$name"
                    val spec = v.asJsonArray
                    specs[path] = spec
                    if (section == "required") required += path
                    if (spec[0].isJsonPrimitive && spec[0].asString == "COMFY_DYNAMICCOMBO_V3") {
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
        if (!spec[0].isJsonPrimitive || spec[0].asString != "COMFY_AUTOGROW_V3" || spec.size() < 2) return false
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
        if (!a.isJsonPrimitive || !a.asJsonPrimitive.isString) return null
        if (!b.isJsonPrimitive || !b.asJsonPrimitive.isNumber) return null
        return a.asString to b.asInt
    }

    /** Declared input type, or null when any link is acceptable (combos, dynamic and match types). */
    private fun declaredType(spec: JsonArray): String? {
        val first = spec[0]
        if (!first.isJsonPrimitive) return null // legacy combo [[options], {...}]
        val type = first.asString
        return when (type) {
            "COMBO", MATCH_TYPE -> null
            in DOTTED_KINDS -> null
            else -> type
        }
    }

    /** Valid literal values for a combo input, or null when the input is not a combo. */
    private fun comboOptions(spec: JsonArray): List<String>? {
        val first = spec[0]
        val config = spec.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject
        return when {
            first.isJsonArray -> first.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            first.asString == "COMBO" ->
                config?.getAsJsonArray("options")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            first.asString == "COMFY_DYNAMICCOMBO_V3" ->
                config?.getAsJsonArray("options")?.mapNotNull { it.asJsonObject.get("key")?.asString }
            else -> null
        }
    }

    fun compatible(produced: String, declared: String): Boolean {
        // Match-type outputs (e.g. ComfySwitchNode) take their input's type at runtime.
        if (produced == "*" || declared == "*" || produced == MATCH_TYPE) return true
        val p = produced.split(",").map { it.trim() }.toSet()
        val d = declared.split(",").map { it.trim() }.toSet()
        return p.intersect(d).isNotEmpty()
    }
}
