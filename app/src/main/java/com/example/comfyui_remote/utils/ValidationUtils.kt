package com.example.comfyui_remote.utils

object ValidationUtils {
    // Basic hostname/IP validation: Alphanumeric, dot, hyphen.
    // This prevents scheme injection (http://), port injection (:80), and path injection (/foo).
    // It also enforces standard hostname rules roughly.
    private val HOST_PATTERN = Regex("^[a-zA-Z0-9.-]+$")

    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false

        // Check for invalid characters
        if (!HOST_PATTERN.matches(host)) return false

        // No consecutive dots
        if (host.contains("..")) return false

        // No starting/ending dot or hyphen
        if (host.startsWith(".") || host.startsWith("-") ||
            host.endsWith(".") || host.endsWith("-")) return false

        // Max length for hostname
        if (host.length > 255) return false

        return true
    }

    fun isValidPort(port: String): Boolean {
        val p = port.toIntOrNull() ?: return false
        return p in 1..65535
    }

    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }
}
