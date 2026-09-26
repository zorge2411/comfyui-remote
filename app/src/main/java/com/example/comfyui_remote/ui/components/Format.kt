package com.example.comfyui_remote.ui.components

/** "63.8 MB" / "1.3 GB" (binary units, one decimal). */
internal fun formatBytes(bytes: Long): String =
    if (bytes >= 1024L * 1024 * 1024) "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else "%.1f MB".format(bytes / (1024.0 * 1024))
