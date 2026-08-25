package com.example.comfyui_remote.data

/**
 * Represents a filter configuration for gallery sync operations.
 * Users can define custom filters to sync specific subsets of their gallery.
 */
data class GallerySyncFilter(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val maxItems: Int = 100,
    val workflowNameFilter: String? = null,
    val fileNameFilter: String? = null,
    val mediaType: MediaType? = null,
    val serverFilter: String? = null, // Format: "host:port"
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST
) {
    enum class MediaType {
        IMAGE,
        VIDEO
    }

    enum class SortOrder {
        NEWEST_FIRST,
        OLDEST_FIRST,
        NAME_ASC
    }

    /**
     * Check if any filters are currently active.
     * @return true if any filter parameter is set
     */
    fun isActive(): Boolean =
        startDate != null || endDate != null ||
        !workflowNameFilter.isNullOrBlank() ||
        !fileNameFilter.isNullOrBlank() ||
        mediaType != null || serverFilter != null

    /**
     * Validate the filter parameters.
     * @return true if the filter is valid
     */
    fun isValid(): Boolean {
        // Check date range validity
        if (startDate != null && endDate != null && startDate > endDate) {
            return false
        }

        // Check max items validity
        if (maxItems < 1 || maxItems > 10000) {
            return false
        }

        return true
    }

    /**
     * Get a human-readable summary of the active filters.
     * @return A string describing the active filters
     */
    fun getSummary(): String {
        val parts = mutableListOf<String>()

        if (startDate != null || endDate != null) {
            parts.add("Date range")
        }

        if (!workflowNameFilter.isNullOrBlank()) {
            parts.add("Workflow: $workflowNameFilter")
        }

        if (!fileNameFilter.isNullOrBlank()) {
            parts.add("File: $fileNameFilter")
        }

        if (mediaType != null) {
            parts.add("Type: ${mediaType.name}")
        }

        if (serverFilter != null) {
            parts.add("Server: $serverFilter")
        }

        if (maxItems != 100) {
            parts.add("Max: $maxItems")
        }

        return if (parts.isEmpty()) "No filters" else parts.joinToString(", ")
    }

    companion object {
        /**
         * Create a default filter with no active filters.
         */
        fun default() = GallerySyncFilter()

        /**
         * Check if a text matches a filter string with support for:
         * - Semicolon-separated terms (;)
         * - Wildcards (*)
         * - Exclusions (-)
         * - Inclusions (+)
         * 
         * Logic:
         * 1. If any exclude (-) term matches, returns false.
         * 2. If there are include terms (+ or no prefix), returns true if any match.
         * 3. If there are NO include terms (only excludes), returns true (since not excluded).
         */
        fun matches(text: String, filter: String?): Boolean {
            if (filter.isNullOrBlank()) return true
            
            val terms = filter.split(";").map { it.trim() }.filter { it.isNotBlank() }
            if (terms.isEmpty()) return true

            val excludeTerms = mutableListOf<String>()
            val includeTerms = mutableListOf<String>()

            terms.forEach { trimmed ->
                if (trimmed.startsWith("-")) {
                    excludeTerms.add(trimmed.substring(1).trim())
                } else if (trimmed.startsWith("+")) {
                    includeTerms.add(trimmed.substring(1).trim())
                } else {
                    includeTerms.add(trimmed)
                }
            }

            // 1. Check exclusions
            excludeTerms.forEach { pattern ->
                if (wildcardMatch(text, pattern)) return false
            }

            // 2. Check inclusions
            if (includeTerms.isEmpty()) return true
            
            includeTerms.forEach { pattern ->
                if (wildcardMatch(text, pattern)) return true
            }

            return false
        }

        private fun wildcardMatch(text: String, pattern: String): Boolean {
            if (pattern == "*" || pattern.isEmpty()) return true
            
            // If pattern doesn't have wildcards, it's a simple contains (case-insensitive)
            if (!pattern.contains("*")) {
                return text.contains(pattern, ignoreCase = true)
            }

            val regexParts = pattern.split("*").map { java.util.regex.Pattern.quote(it) }
            val regexString = "^" + regexParts.joinToString(".*") + "$"
            
            return try {
                val regex = Regex(regexString, RegexOption.IGNORE_CASE)
                regex.matches(text)
            } catch (e: Exception) {
                // Fallback for invalid regex combinations
                text.contains(pattern.replace("*", ""), ignoreCase = true)
            }
        }
    }
}
