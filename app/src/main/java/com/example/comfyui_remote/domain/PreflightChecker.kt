package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Checks an API-format prompt against the server's /object_info before it is queued (Phase 91)
 * and turns ApiPromptValidator violations into issues grouped by node, most severe first.
 */
object PreflightChecker {

    fun check(promptJson: String, objectInfo: JsonObject, options: ApiPromptValidator.Options): PreflightResult {
        val prompt = try {
            JsonParser.parseString(promptJson).asJsonObject
        } catch (e: Exception) {
            println("PREFLIGHT_DEBUG: prompt is not a JSON object, skipping check: ${e.message}")
            return PreflightResult.EMPTY
        }

        val violations = try {
            ApiPromptValidator.validate(prompt, objectInfo, graph = null, options = options)
        } catch (e: Exception) {
            println("PREFLIGHT_DEBUG: validation failed, skipping check: $e")
            return PreflightResult.EMPTY
        }
        violations.forEach { println("PREFLIGHT_DEBUG: $it") }

        val nodes = violations.groupBy { it.nodeId }.map { (nodeId, list) ->
            val node = prompt.get(nodeId)?.takeIf { it.isJsonObject }?.asJsonObject
            val classType = node?.get("class_type")?.takeIf { it.isJsonPrimitive }?.asString ?: "?"
            val title = node?.getAsJsonObject("_meta")?.get("title")?.takeIf { it.isJsonPrimitive }?.asString
            val issues = list.map { toIssue(it, classType) }
                // One neutral line per node for converter problems; the details are in the log.
                .distinctBy { if (it.kind == PreflightIssueKind.CONVERSION) "conversion" else it.message }
                .sortedBy { it.kind.ordinal }
            PreflightNodeIssues(nodeId, title, classType, issues)
        }.sortedWith(
            compareBy<PreflightNodeIssues> { n -> n.issues.minOf { it.kind.ordinal } }
                .thenBy { it.nodeId.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.nodeId }
        )
        return PreflightResult(nodes)
    }

    private fun toIssue(v: ApiPromptValidator.Violation, classType: String): PreflightIssue = when (v.check) {
        "C1" -> PreflightIssue(v.nodeId, PreflightIssueKind.MISSING_NODE_TYPE, "Node type $classType isn't installed on the server")
        "C4" -> PreflightIssue(v.nodeId, PreflightIssueKind.MISSING_INPUT, "Required input ${v.input} has no value or link")
        "C5" -> {
            val value = v.value ?: ""
            if (ApiPromptValidator.FILE_VALUE.containsMatchIn(value)) {
                val available = if (v.available.isEmpty()) "" else ". Available: ${v.available.joinToString(", ")}"
                PreflightIssue(v.nodeId, PreflightIssueKind.MISSING_FILE, "$value isn't on the server$available")
            } else {
                PreflightIssue(v.nodeId, PreflightIssueKind.INVALID_VALUE, "$value isn't a valid option for ${v.input}")
            }
        }
        else -> PreflightIssue(v.nodeId, PreflightIssueKind.CONVERSION, "The workflow didn't convert cleanly (details in the log)")
    }
}
