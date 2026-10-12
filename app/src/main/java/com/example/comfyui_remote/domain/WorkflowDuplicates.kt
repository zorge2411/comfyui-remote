package com.example.comfyui_remote.domain

/**
 * Finds an already-imported copy of a workflow (Phase 98). [sourceKey] is the template name or server file
 * path; workflows without one (file, paste) are matched by source and name.
 */
data class WorkflowRef(val id: Long, val name: String, val source: String?, val sourceKey: String?)

object WorkflowDuplicates {

    fun find(existing: List<WorkflowRef>, source: String?, sourceKey: String?, name: String): WorkflowRef? {
        if (sourceKey != null) {
            existing.firstOrNull { it.source == source && it.sourceKey == sourceKey }?.let { return it }
        }
        return existing.firstOrNull { it.source == source && it.sourceKey == null && it.name.equals(name, ignoreCase = true) }
    }

    /** "X", then "X (2)", "X (3)" ... the first that no existing name uses (case-insensitive). */
    fun uniqueName(name: String, existingNames: Collection<String>): String {
        val taken = existingNames.map { it.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while ("$name ($n)".lowercase() in taken) n++
        return "$name ($n)"
    }
}
