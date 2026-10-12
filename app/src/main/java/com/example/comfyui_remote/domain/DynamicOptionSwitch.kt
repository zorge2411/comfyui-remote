package com.example.comfyui_remote.domain

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Switches the selected option of a COMFY_DYNAMICCOMBO_V3 input in an API prompt (Phase 101).
 *
 * The prompt holds the combo key plus the selected option's sub-inputs as dotted keys ("format.codec"); the
 * server reads only those (comfy_api _io.py). Switching replaces them with the new option's, as the frontend's
 * dynamicWidgets.ts does when the option changes: required then optional, spec order, nested combos recursively.
 */
object DynamicOptionSwitch {

    private const val DYNAMIC_COMBO = "COMFY_DYNAMICCOMBO_V3"
    private const val AUTOGROW = "COMFY_AUTOGROW_V3"
    private val WIDGETS = setOf("INT", "FLOAT", "STRING", "BOOLEAN", "COMBO")

    data class Result(val json: String, val droppedLinks: List<String>)

    /**
     * Option keys of the dynamic combo at [path] that the form can switch to (the current one included), or
     * null when [path] isn't a dynamic combo. Options needing a required socket input are left out.
     */
    fun switchableKeys(objectInfo: JsonObject, classType: String, path: String, nodeInputs: JsonObject): List<String>? {
        val spec = specAt(objectInfo, classType, path, nodeInputs) ?: return null
        if (kindOf(spec) != DYNAMIC_COMBO) return null
        val current = nodeInputs.get(path)?.takeIf { it.isJsonPrimitive }?.asString
        return options(spec).mapNotNull { option ->
            val key = option.get("key")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            key.takeIf { key == current || build(option, path, path, emptyMap(), LinkedHashMap()) }
        }
    }

    /** The prompt with the combo at [path] on node [nodeId] set to [newKey]; null if that isn't possible. */
    fun switch(promptJson: String, nodeId: String, path: String, newKey: String, objectInfo: JsonObject): Result? {
        val prompt = try {
            JsonParser.parseString(promptJson).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val node = prompt.get(nodeId)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val classType = node.get("class_type")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val inputs = node.get("inputs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (!inputs.has(path)) return null
        val spec = specAt(objectInfo, classType, path, inputs) ?: return null
        if (kindOf(spec) != DYNAMIC_COMBO) return null
        val option = options(spec).firstOrNull { it.get("key")?.asString == newKey } ?: return null

        val old = LinkedHashMap<String, JsonElement>()
        val dropped = mutableListOf<String>()
        inputs.entrySet().filter { it.key.startsWith("$path.") }.forEach { (key, value) ->
            val relative = key.removePrefix("$path.")
            if (value.isJsonArray) dropped += relative else old[relative] = value
        }

        val added = LinkedHashMap<String, JsonElement>()
        if (!build(option, path, path, old, added)) return null

        val rebuilt = JsonObject()
        for ((key, value) in inputs.entrySet()) {
            if (key.startsWith("$path.")) continue
            rebuilt.add(key, if (key == path) JsonPrimitive(newKey) else value)
            if (key == path) added.forEach { (k, v) -> rebuilt.add(k, v) }
        }
        node.add("inputs", rebuilt)
        return Result(prompt.toString(), dropped)
    }

    /** Carries the images picked in the old form fields over to the re-parsed ones. */
    fun keepImages(old: List<InputField>, new: List<InputField>): List<InputField> {
        val images = old.filterIsInstance<InputField.ImageInput>().associateBy { it.nodeId to it.fieldName }
        return new.map { field ->
            val previous = (field as? InputField.ImageInput)?.let { images[it.nodeId to it.fieldName] }
            if (previous != null) field.copy(value = previous.value ?: field.value, localUri = previous.localUri) else field
        }
    }

    /**
     * Adds [option]'s inputs under [prefix] to [out]. [old] holds the previous option's values by path relative
     * to [rootPath]. Returns false if the option needs an input the form can't provide (a required socket).
     */
    private fun build(
        option: JsonObject,
        rootPath: String,
        prefix: String,
        old: Map<String, JsonElement>,
        out: MutableMap<String, JsonElement>
    ): Boolean {
        val optionInputs = option.getAsJsonObject("inputs") ?: return true
        for (section in listOf("required", "optional")) {
            val group = optionInputs.get(section)?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            for ((name, el) in group.entrySet()) {
                val spec = el.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                val full = "$prefix.$name"
                val relative = full.removePrefix("$rootPath.")
                val kind = kindOf(spec) ?: continue
                when (kind) {
                    DYNAMIC_COMBO -> {
                        val choices = options(spec)
                        val chosen = old[relative]?.takeIf { it.isJsonPrimitive }?.asString
                            ?.let { key -> choices.firstOrNull { it.get("key")?.asString == key } }
                            ?: defaultOption(spec, choices)
                            ?: return false
                        out[full] = JsonPrimitive(chosen.get("key").asString)
                        if (!build(chosen, rootPath, full, old, out)) return false
                    }
                    AUTOGROW -> {}
                    in WIDGETS -> {
                        if (isForcedInput(spec)) {
                            if (section == "required") return false
                        } else {
                            val value = old[relative]?.takeIf { isValid(kind, spec, it) } ?: defaultValue(kind, spec)
                            if (value != null) out[full] = value else if (section == "required") return false
                        }
                    }
                    else -> if (section == "required") return false
                }
            }
        }
        return true
    }

    private fun options(spec: JsonArray): List<JsonObject> =
        config(spec)?.getAsJsonArray("options")?.mapNotNull { it.takeIf { o -> o.isJsonObject }?.asJsonObject }.orEmpty()

    private fun defaultOption(spec: JsonArray, choices: List<JsonObject>): JsonObject? {
        val wanted = config(spec)?.get("default")?.takeIf { it.isJsonPrimitive }?.asString
        return choices.firstOrNull { it.get("key")?.asString == wanted } ?: choices.firstOrNull()
    }

    private fun config(spec: JsonArray): JsonObject? =
        spec.takeIf { it.size() > 1 }?.get(1)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun kindOf(spec: JsonArray): String? {
        val first = spec.takeIf { it.size() > 0 }?.get(0) ?: return null
        return if (first.isJsonArray) "COMBO" else first.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun isForcedInput(spec: JsonArray): Boolean =
        listOf("forceInput", "defaultInput").any { key ->
            config(spec)?.get(key)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
        }

    private fun comboValues(spec: JsonArray): List<JsonElement> {
        val first = spec.get(0)
        val list = if (first.isJsonArray) first.asJsonArray else config(spec)?.getAsJsonArray("options")
        return list?.filter { it.isJsonPrimitive }.orEmpty()
    }

    private fun isValid(kind: String, spec: JsonArray, value: JsonElement): Boolean {
        if (!value.isJsonPrimitive) return false
        val p = value.asJsonPrimitive
        return when (kind) {
            "INT", "FLOAT" -> {
                if (!p.isNumber) return false
                val d = p.asDouble
                if (kind == "INT" && d % 1.0 != 0.0) return false
                val min = config(spec)?.get("min")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                val max = config(spec)?.get("max")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                (min == null || d >= min) && (max == null || d <= max)
            }
            "STRING" -> p.isString
            "BOOLEAN" -> p.isBoolean
            "COMBO" -> comboValues(spec).any { it.asString == p.asString }
            else -> false
        }
    }

    /** What a newly created frontend widget starts with. */
    private fun defaultValue(kind: String, spec: JsonArray): JsonElement? {
        val default = config(spec)?.get("default")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        return when (kind) {
            "INT", "FLOAT" -> default ?: JsonPrimitive(0)
            "STRING" -> default ?: JsonPrimitive("")
            "BOOLEAN" -> default ?: JsonPrimitive(false)
            "COMBO" -> {
                val values = comboValues(spec)
                values.firstOrNull { it.asString == default?.asString } ?: values.firstOrNull()
            }
            else -> null
        }
    }

    /** The /object_info spec at [path]; dotted parts walk the selected option of each parent combo. */
    private fun specAt(objectInfo: JsonObject, classType: String, path: String, nodeInputs: JsonObject): JsonArray? {
        var group = objectInfo.get(classType)?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("input")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val parts = path.split(".")
        var spec: JsonArray? = null
        parts.forEachIndexed { i, part ->
            spec = listOf("required", "optional")
                .firstNotNullOfOrNull { group.get(it)?.takeIf { g -> g.isJsonObject }?.asJsonObject?.get(part) }
                ?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            if (i < parts.lastIndex) {
                if (kindOf(spec!!) != DYNAMIC_COMBO) return null
                val selected = nodeInputs.get(parts.take(i + 1).joinToString("."))
                    ?.takeIf { it.isJsonPrimitive }?.asString ?: return null
                group = options(spec!!).firstOrNull { it.get("key")?.asString == selected }
                    ?.get("inputs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            }
        }
        return spec
    }
}
