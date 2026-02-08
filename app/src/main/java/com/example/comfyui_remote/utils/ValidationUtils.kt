package com.example.comfyui_remote.utils

import java.util.regex.Pattern

object ValidationUtils {
    // Basic regex for hostname/IP (supports localhost, IPv4, simple domains)
    // Avoids complex regex for full RFC compliance to keep it simple but safe.
    // Allow alphanumeric, dots, hyphens.
    private val HOSTNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$")

    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host.length > 255) return false

        // Prevent starting/ending with dot or hyphen
        if (host.startsWith(".") || host.startsWith("-") ||
            host.endsWith(".") || host.endsWith("-")) return false

        // Prevent consecutive dots
        if (host.contains("..")) return false

        return HOSTNAME_PATTERN.matcher(host).matches()
    }

    fun isValidPort(portStr: String): Boolean {
        return try {
            val port = portStr.toInt()
            port in 1..65535
        } catch (e: NumberFormatException) {
            false
        }
    }
}
