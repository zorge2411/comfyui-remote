package com.example.comfyui_remote.utils

import java.util.regex.Pattern

object ValidationUtils {

    // Regex for IPv4 address
    private val IP_V4_REGEX = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    // Regex for standard hostname (RFC 1123)
    // Allows single labels (e.g. "localhost", "my-pc") and dot-separated labels.
    private val HOSTNAME_REGEX = Pattern.compile(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*)$"
    )

    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false

        // Strip scheme if user accidentally pasted it
        var cleanHost = host.trim()
        if (cleanHost.startsWith("http://")) cleanHost = cleanHost.substring(7)
        if (cleanHost.startsWith("https://")) cleanHost = cleanHost.substring(8)
        cleanHost = cleanHost.removeSuffix("/")

        // Reject if it contains port separator to enforce using the Port field
        if (cleanHost.contains(":")) {
             return false
        }

        if (cleanHost.equals("localhost", ignoreCase = true)) return true

        if (IP_V4_REGEX.matcher(cleanHost).matches()) return true

        if (HOSTNAME_REGEX.matcher(cleanHost).matches()) return true

        return false
    }

    fun isValidPort(port: String): Boolean {
        if (port.isBlank()) return false
        val p = port.toIntOrNull() ?: return false
        return p in 1..65535
    }
}
