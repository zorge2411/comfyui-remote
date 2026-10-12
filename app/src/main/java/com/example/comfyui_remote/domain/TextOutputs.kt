package com.example.comfyui_remote.domain

import com.google.gson.JsonObject

/**
 * Text results of a run: the `text` list that display nodes (Display Any, Preview Any, Show Text...) put in
 * `/history` outputs. Shown in the media viewer and as a display-only item in the form's Main settings.
 */
object TextOutputs {
    /** All text of one run, in node order, separated by a blank line; null when no node produced any. */
    fun from(outputs: JsonObject?): String? {
        if (outputs == null) return null
        val parts = mutableListOf<String>()
        for (entry in outputs.entrySet()) {
            val nodeOutput = entry.value
            if (!nodeOutput.isJsonObject) continue
            val texts = nodeOutput.asJsonObject.get("text")
            if (texts == null || !texts.isJsonArray) continue
            for (el in texts.asJsonArray) {
                if (!el.isJsonPrimitive || !el.asJsonPrimitive.isString) continue
                val s = el.asString.trim()
                if (s.isNotEmpty()) parts.add(s)
            }
        }
        return parts.joinToString("\n\n").takeIf { it.isNotEmpty() }
    }

    /** Node types that show text; used to tell whether a workflow has a text result to wait for. */
    private val DISPLAY_TYPES = setOf("Display Any (rgthree)", "PreviewAny", "ShowText|pysssss", "easy showAnything")

    fun hasDisplayNode(promptJson: String?): Boolean {
        if (promptJson.isNullOrBlank()) return false
        return try {
            com.google.gson.JsonParser.parseString(promptJson).asJsonObject.entrySet().any { (_, node) ->
                node.isJsonObject && node.asJsonObject.get("class_type")?.asString in DISPLAY_TYPES
            }
        } catch (e: Exception) {
            false
        }
    }
}
