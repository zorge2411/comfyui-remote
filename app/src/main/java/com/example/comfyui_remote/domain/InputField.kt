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
        override val nodeTitle: String
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
        override val nodeTitle: String,
        /** True for a dynamic combo: picking another option rebuilds the node's sub-fields (Phase 101). */
        val switchesOptions: Boolean = false
    ) : InputField("Selection")

    /** A COLOR widget (Phase 102): "#rrggbb". */
    data class ColorInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String,
        override val nodeTitle: String
    ) : InputField("Color")

    /** A BOUNDING_BOX widget (Phase 102): {"x", "y", "width", "height"}. */
    data class BoundingBoxInput(
        override val nodeId: String,
        override val fieldName: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        override val nodeTitle: String
    ) : InputField("Bounding Box")

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

/** Hex colour text for COLOR widgets: "#rgb" or "#rrggbb". */
object ColorHex {
    private val PATTERN = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    fun isValid(text: String): Boolean = PATTERN.matches(text.trim())

    /** "#abc" -> "#aabbcc", lower case; null if invalid. */
    fun normalize(text: String): String? {
        val t = text.trim()
        if (!isValid(t)) return null
        val hex = t.substring(1).lowercase()
        return "#" + if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
    }

    /** 0xRRGGBB, or null if invalid. */
    fun toRgb(text: String): Int? = normalize(text)?.substring(1)?.toInt(16)
}
