package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.WorkflowEntity

enum class WorkflowSort { LAST_USED, NAME, NEWEST }

/** Search and sort for the workflow list (Phase 103). */
object WorkflowListView {

    /** [query] matches the workflow name or one of its models, ignoring case. */
    fun apply(
        workflows: List<WorkflowEntity>,
        modelsById: Map<Long, List<ModelRef>>,
        query: String,
        sort: WorkflowSort
    ): List<WorkflowEntity> {
        val q = query.trim()
        val matching = if (q.isEmpty()) workflows else workflows.filter { wf ->
            wf.name.contains(q, ignoreCase = true) ||
                modelsById[wf.id].orEmpty().any { it.baseName.contains(q, ignoreCase = true) }
        }
        return when (sort) {
            WorkflowSort.LAST_USED -> matching.sortedByDescending { it.lastUsedAt ?: it.createdAt }
            WorkflowSort.NAME -> matching.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { wf: WorkflowEntity -> wf.name }.thenBy { it.id }
            )
            WorkflowSort.NEWEST -> matching.sortedByDescending { it.createdAt }
        }
    }
}
