package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Whether a stored workflow should run on the connected server, for the badge on the workflow list (Phase 100).
 * Built on [PromptValidator]; opening the workflow still shows the full detail.
 */
object WorkflowCompatibility {

    enum class Level { READY, WARNINGS, WILL_FAIL }

    data class Result(val level: Level, val summary: String)

    // The form replaces these file inputs at run time, so a stored name the server lacks is not a problem
    private val FORM_FILE_INPUTS = setOf("image", "video", "audio", "mask")

    fun assess(issues: List<PromptValidator.Issue>): Result {
        val errors = issues.filter { it.severity == PromptValidator.Severity.ERROR }
        if (errors.isNotEmpty()) {
            val missing = errors.filter { it.kind == PromptValidator.Kind.MISSING_NODE_TYPE }
                .map { it.classType }.distinct().sorted()
            val summary = if (missing.isNotEmpty()) {
                "Missing nodes: ${missing.joinToString(", ")}"
            } else {
                errors.first().let { "${it.nodeTitle}: ${it.message}" }
            }
            return Result(Level.WILL_FAIL, summary)
        }
        if (issues.isNotEmpty()) {
            return Result(Level.WARNINGS, if (issues.size == 1) "1 warning" else "${issues.size} warnings")
        }
        return Result(Level.READY, "")
    }

    /** The stored workflow (API format) against the server's /object_info; null if it can't be read. */
    fun check(json: String, objectInfo: JsonObject): Result? {
        val prompt = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            assess(
                PromptValidator.validate(prompt, objectInfo).filterNot {
                    it.kind == PromptValidator.Kind.VALUE_NOT_IN_LIST && it.inputName in FORM_FILE_INPUTS
                }
            )
        } catch (e: Exception) {
            null
        }
    }
}
