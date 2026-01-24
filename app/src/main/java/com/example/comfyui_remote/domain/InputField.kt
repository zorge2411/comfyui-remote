package com.example.comfyui_remote.domain

sealed class InputField(
    val label: String,
    open val nodeTitle: String,
    open val nodeId: String,
    open val fieldName: String
) {
    data class StringInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String,
        override val nodeTitle: String
    ) : InputField("Text", nodeTitle, nodeId, fieldName)

    data class IntInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: Int,
        override val nodeTitle: String
    ) : InputField("Number", nodeTitle, nodeId, fieldName)

    data class SeedInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: Long,
        override val nodeTitle: String
    ) : InputField("Seed", nodeTitle, nodeId, fieldName)

    data class ModelInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String,
        override val nodeTitle: String
    ) : InputField("Model", nodeTitle, nodeId, fieldName)

    data class ImageInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String? = null, // The server filename
        val localUri: String? = null, // For preview before upload completes or just preview
        override val nodeTitle: String
    ) : InputField("Image", nodeTitle, nodeId, fieldName)
}
