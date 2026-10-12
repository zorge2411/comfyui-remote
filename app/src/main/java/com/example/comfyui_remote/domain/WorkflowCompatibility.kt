package com.example.comfyui_remote.domain

import com.google.gson.JsonObject

/** Whether a workflow will run on the connected server (Phase 103): the badge on the workflow list. */
sealed interface Compatibility {
    data object Ready : Compatibility
    data class Warnings(val issues: List<PromptValidator.Issue>) : Compatibility
    data class WillFail(val issues: List<PromptValidator.Issue>) : Compatibility
}

object WorkflowCompatibility {

    /**
     * Checks [prompt] (built the way Generate builds it) against the server's /object_info.
     * An image missing on the server only warns: the user picks one in the form before running.
     */
    fun check(prompt: JsonObject, objectInfo: JsonObject): Compatibility {
        val issues = PromptValidator.validate(prompt, objectInfo).map { issue ->
            if (issue.kind == PromptValidator.Kind.VALUE_NOT_IN_LIST && issue.inputName == "image") {
                issue.copy(severity = PromptValidator.Severity.WARNING)
            } else {
                issue
            }
        }
        return when {
            issues.any { it.severity == PromptValidator.Severity.ERROR } ->
                Compatibility.WillFail(issues.sortedBy { it.severity != PromptValidator.Severity.ERROR })
            issues.isNotEmpty() -> Compatibility.Warnings(issues)
            else -> Compatibility.Ready
        }
    }
}
