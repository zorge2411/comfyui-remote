package com.example.comfyui_remote.domain

sealed class InputField(val label: String, open val nodeTitle: String) {
    data class StringInput(
        val nodeId: String,
        val fieldName: String,
        val value: String,
        override val nodeTitle: String
    ) : InputField("Text", nodeTitle)

    data class IntInput(
        val nodeId: String,
        val fieldName: String,
        val value: Int,
        override val nodeTitle: String
    ) : InputField("Number", nodeTitle)

    data class SeedInput(
        val nodeId: String,
        val fieldName: String,
        val value: Long,
        override val nodeTitle: String
    ) : InputField("Seed", nodeTitle)
}
