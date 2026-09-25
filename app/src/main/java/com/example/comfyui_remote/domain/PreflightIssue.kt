package com.example.comfyui_remote.domain

/**
 * Pre-flight results (Phase 91), shaped so server node_errors (Phase 92) can map onto them.
 * Kinds are declared in display order: the first kinds explain most of the later ones.
 */
enum class PreflightIssueKind(val singular: String, val plural: String) {
    MISSING_NODE_TYPE("missing node type", "missing node types"),
    MISSING_FILE("missing model or file", "missing models or files"),
    INVALID_VALUE("invalid value", "invalid values"),
    MISSING_INPUT("missing input", "missing inputs"),
    CONVERSION("conversion problem", "conversion problems")
}

data class PreflightIssue(val nodeId: String, val kind: PreflightIssueKind, val message: String)

data class PreflightNodeIssues(
    val nodeId: String,
    val title: String?,
    val classType: String,
    val issues: List<PreflightIssue>
) {
    val heading: String
        get() = if (title.isNullOrBlank() || title == classType) classType else "$title ($classType)"
}

data class PreflightResult(val nodes: List<PreflightNodeIssues>) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun count(kind: PreflightIssueKind): Int = nodes.sumOf { n -> n.issues.count { it.kind == kind } }

    /** e.g. "2 missing node types, 1 missing model or file". */
    fun summary(): String = PreflightIssueKind.entries.mapNotNull { kind ->
        val n = count(kind)
        when (n) {
            0 -> null
            1 -> "1 ${kind.singular}"
            else -> "$n ${kind.plural}"
        }
    }.joinToString(", ")

    companion object {
        val EMPTY = PreflightResult(emptyList())
    }
}
