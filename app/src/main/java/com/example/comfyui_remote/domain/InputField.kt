package com.example.comfyui_remote.domain

sealed class InputField(
    val label: String
) {
    abstract val nodeTitle: String
    abstract val nodeId: String
    abstract val fieldName: String

    data class StringInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String,
        override val nodeTitle: String
    ) : InputField("Text")

    data class IntInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: Int,
        override val nodeTitle: String
    ) : InputField("Number")

    data class SeedInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: Long,
        override val nodeTitle: String,
        // Phase 101: true sends the value as typed; null (older saved or queued JSON) or false randomizes each run
        val fixed: Boolean? = null
    ) : InputField("Seed")

    data class FloatInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: Float,
        override val nodeTitle: String
    ) : InputField("Number (Float)")

    @Deprecated("Use SelectionInput for generic dropdowns")
    data class ModelInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String,
        override val nodeTitle: String
    ) : InputField("Model")

    data class SelectionInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String,
        val options: List<String>,
        override val nodeTitle: String
    ) : InputField("Selection")

    data class ImageInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String? = null, // The server filename
        val localUri: String? = null, // For preview before upload completes or just preview
        override val nodeTitle: String
    ) : InputField("Image")
}

/** Field name for display: inputs of a dynamic combo option are dotted ("format.codec" -> "format › codec"). */
val InputField.displayName: String
    get() = fieldName.replace(".", " › ")

/** Identifies a field across re-parses and regrouping: "nodeId/fieldName". */
val InputField.key: String
    get() = "$nodeId/$fieldName"
