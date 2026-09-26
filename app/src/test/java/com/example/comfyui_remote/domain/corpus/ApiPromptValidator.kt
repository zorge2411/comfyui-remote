package com.example.comfyui_remote.domain.corpus

import com.example.comfyui_remote.domain.PromptValidator
import com.google.gson.JsonObject

/**
 * Corpus checks on an API-format prompt produced by GraphToApiConverter, against a saved /object_info.
 * C1–C5 come from the app's PromptValidator (every node, no range/conversion checks, file names skipped
 * because the snapshot has no files); C6/C7 need the original graph. Check IDs: see
 * .gsd/phases/89/89-CONTEXT.md (D-05):
 *  C1 unknown class_type, C2 dangling link, C3 link type mismatch, C4 missing required input,
 *  C5 invalid combo value, C6 muted/bypassed node sent, C7 frontend-only node sent.
 */
object ApiPromptValidator {

    data class Violation(val check: String, val nodeId: String, val detail: String) {
        override fun toString() = "$check node $nodeId: $detail"
    }

    val FRONTEND_ONLY = setOf("Reroute", "PrimitiveNode", "Note", "MarkdownNote")

    private val CHECK_IDS = mapOf(
        PromptValidator.Kind.MISSING_NODE_TYPE to "C1",
        PromptValidator.Kind.BAD_LINK to "C2",
        PromptValidator.Kind.TYPE_MISMATCH to "C3",
        PromptValidator.Kind.REQUIRED_INPUT_MISSING to "C4",
        PromptValidator.Kind.VALUE_NOT_IN_LIST to "C5"
    )

    fun validate(api: JsonObject, objectInfo: JsonObject, graph: JsonObject): List<Violation> {
        val out = mutableListOf<Violation>()

        val skipped = graph.getAsJsonArray("nodes")?.mapNotNull { el ->
            val n = el.asJsonObject
            val mode = n.get("mode")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            if (mode == 2 || mode == 4) n.get("id").asString else null
        }?.toSet().orEmpty()

        // Frontend-only nodes are C7 only (the snapshot lacks them, so they would also be C1)
        val frontendOnly = api.entrySet().filter { (_, el) ->
            el.asJsonObject.get("class_type")?.asString in FRONTEND_ONLY
        }.map { it.key }.toSet()

        for ((id, el) in api.entrySet()) {
            val classType = el.asJsonObject.get("class_type")?.asString ?: "<none>"
            if (id in skipped) out += Violation("C6", id, "$classType is muted/bypassed in the graph but was sent")
            if (id in frontendOnly) out += Violation("C7", id, "frontend-only $classType was sent")
        }

        PromptValidator.validate(api, objectInfo, checkFileValues = false, reachableOnly = false, valueChecks = false)
            .filter { it.nodeId !in frontendOnly }
            .forEach { issue ->
                val check = CHECK_IDS[issue.kind] ?: return@forEach
                out += Violation(check, issue.nodeId, "${issue.classType}${issue.inputName?.let { ".$it" } ?: ""}: ${issue.message}")
            }
        return out
    }

    fun compatible(produced: String, declared: String): Boolean = PromptValidator.compatible(produced, declared)
}
