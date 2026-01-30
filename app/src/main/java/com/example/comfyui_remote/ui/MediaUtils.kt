package com.example.comfyui_remote.ui

import com.example.comfyui_remote.data.GeneratedMediaListing

fun GeneratedMediaListing.constructUrl(
    currentHost: String,
    currentPort: String,
    isSecure: Boolean
): String {
    val portInt = currentPort.toIntOrNull() ?: 8188
    val shouldUseSecure = isSecure && this.serverHost == currentHost && this.serverPort == portInt
    val protocol = if (shouldUseSecure) "https" else "http"
    return "$protocol://${this.serverHost}:${this.serverPort}/view?filename=${this.fileName}${if (this.subfolder != null) "&subfolder=${this.subfolder}" else ""}&type=${this.serverType}"
}
