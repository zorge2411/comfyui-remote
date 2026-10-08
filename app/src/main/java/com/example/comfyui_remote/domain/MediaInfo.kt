package com.example.comfyui_remote.domain

import com.google.gson.JsonObject

/**
 * What the media viewer's Info sheet shows about how an item was made (Phase 104): the prompts and the
 * main settings, read from the stored API prompt with the same layout rules and labels as the form.
 */
data class MediaInfo(
    val prompt: String?,
    val negative: String?,
    /** Label and value, in the form's main-settings order (Seed, Width, Height, Steps, CFG...). */
    val settings: List<Pair<String, String>>,
    /** Text the run's display nodes produced (e.g. the Ollama prompt). */
    val resultText: String? = null
) {
    val isEmpty: Boolean get() = prompt == null && negative == null && settings.isEmpty() && resultText == null

    companion object {
        val EMPTY = MediaInfo(null, null, emptyList())

        fun from(promptJson: String?, parser: WorkflowParser, metadata: JsonObject? = null): MediaInfo {
            if (promptJson.isNullOrBlank()) return EMPTY
            return try {
                val inputs = parser.parse(promptJson, metadata)
                if (inputs.isEmpty()) return EMPTY
                val model = FormLayout.build(
                    inputs,
                    parser.findPositivePromptNodeId(promptJson),
                    parser.findNegativePromptNodeId(promptJson)
                )
                MediaInfo(
                    prompt = model.prompt?.field?.let(::text)?.takeIf { it.isNotBlank() },
                    negative = model.negative?.field?.let(::text)?.takeIf { it.isNotBlank() },
                    settings = model.main.mapNotNull { lf -> text(lf.field)?.let { lf.label to it } }
                )
            } catch (e: Exception) {
                EMPTY
            }
        }

        private fun text(field: InputField): String? = when (field) {
            is InputField.StringInput -> field.value
            is InputField.IntInput -> field.value.toString()
            is InputField.SeedInput -> field.value.toString()
            is InputField.FloatInput -> field.value.toBigDecimal().stripTrailingZeros().toPlainString()
            is InputField.ModelInput -> field.value.substringAfterLast('/').substringAfterLast('\\')
            is InputField.SelectionInput -> field.value.substringAfterLast('/').substringAfterLast('\\')
            is InputField.ImageInput -> field.value
        }
    }
}
