package com.example.comfyui_remote.util

object ValidationUtils {
    // Allows alphanumeric, dots, hyphens, underscores (local mDNS), colons (IPv6), and brackets (IPv6 literals).
    // explicitly disallows slashes (/), query chars (?), fragments (#), user info (@), etc.
    private val HOST_PATTERN = Regex("^[a-zA-Z0-9.\\-_:\\[\\]]+$")

    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host.length > 253) return false // Max DNS length
        return HOST_PATTERN.matches(host)
    }

    fun isValidPort(portStr: String): Boolean {
        val port = portStr.toIntOrNull() ?: return false
        return port in 1..65535
    }
}
