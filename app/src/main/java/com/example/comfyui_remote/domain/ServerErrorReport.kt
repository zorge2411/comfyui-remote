package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * What the ComfyUI server said about a failed (or partly failed) prompt, per node (Phase 92).
 * Built from a /prompt error response, a 200 response that still lists node_errors, or a websocket
 * execution_error message (ComfyUI server.py post_prompt, execution.py validate_prompt / handle_execution_error).
 */
data class ServerErrorReport(
    val summary: String,
    val nodes: List<NodeError>,
    /** True when the server queued the prompt but skipped the outputs depending on these nodes. */
    val isPartial: Boolean = false
) {
    data class NodeError(val nodeId: String, val classType: String, val title: String, val errors: List<Reason>)

    data class Reason(val inputName: String?, val message: String, val details: String?)

    /** Readable text: the summary, then each node by title with every reason. */
    fun format(): String = buildString {
        append(summary)
        nodes.forEach { node ->
            append("\n\n")
            append(node.title)
            val label = if (node.classType.isNotEmpty() && node.classType != node.title) "${node.classType} #${node.nodeId}" else "#${node.nodeId}"
            append(" ($label)")
            node.errors.forEach { reason ->
                append("\n• ")
                reason.inputName?.let { append(it).append(": ") }
                append(reason.message)
                val details = reason.details?.lineSequence()?.firstOrNull()?.trim()
                if (!details.isNullOrEmpty() && details != reason.inputName) append(" — ").append(details)
            }
        }
    }

    companion object {
        private const val RAW_BODY_LIMIT = 500

        /** A non-2xx /prompt response. */
        fun fromPromptError(httpCode: Int, body: String?, sentPrompt: JsonObject?): ServerErrorReport {
            val root = body?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return ServerErrorReport(
                    "HTTP $httpCode" + (body?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { "\n" + it.take(RAW_BODY_LIMIT) + if (it.length > RAW_BODY_LIMIT) "…" else "" } ?: ""),
                    emptyList()
                )
            val error = root.get("error")
            val message = when {
                error == null || error.isJsonNull -> "Prompt rejected by the server (HTTP $httpCode)"
                error.isJsonObject -> error.asJsonObject.str("message") ?: "Prompt rejected by the server (HTTP $httpCode)"
                else -> error.asString
            }
            val nodes = nodeErrors(root.obj("node_errors"), sentPrompt)
            val details = error?.takeIf { it.isJsonObject }?.asJsonObject?.str("details")?.trim()
            val summary = if (nodes.isEmpty() && !details.isNullOrEmpty()) "$message\n$details" else message
            return ServerErrorReport(summary, nodes)
        }

        /** A 200 /prompt response that still lists node_errors: those outputs were skipped. */
        fun fromPartialAcceptance(nodeErrors: JsonObject?, sentPrompt: JsonObject?): ServerErrorReport? {
            val nodes = nodeErrors(nodeErrors, sentPrompt)
            if (nodes.isEmpty()) return null
            return ServerErrorReport("Some outputs were skipped by the server", nodes, isPartial = true)
        }

        /** A websocket execution_error message's data. */
        fun fromExecutionError(data: JsonObject, sentPrompt: JsonObject?): ServerErrorReport {
            val nodeId = data.str("node_id") ?: ""
            val classType = data.str("node_type") ?: sentPrompt?.obj(nodeId)?.str("class_type") ?: ""
            val exceptionType = data.str("exception_type")?.substringAfterLast('.')
            val exceptionMessage = data.str("exception_message")?.trim().orEmpty()
            val message = listOfNotNull(exceptionType, exceptionMessage.takeIf { it.isNotEmpty() }).joinToString(": ")
                .ifEmpty { "Unknown error" }
            val node = NodeError(nodeId, classType, titleOf(nodeId, classType, sentPrompt), listOf(Reason(null, message, null)))
            return ServerErrorReport("Execution failed", if (nodeId.isEmpty()) emptyList() else listOf(node))
        }

        private fun nodeErrors(nodeErrors: JsonObject?, sentPrompt: JsonObject?): List<NodeError> =
            nodeErrors?.entrySet()?.mapNotNull { (nodeId, el) ->
                val entry = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val classType = entry.str("class_type") ?: sentPrompt?.obj(nodeId)?.str("class_type") ?: ""
                val reasons = entry.getAsJsonArray("errors")?.mapNotNull { r ->
                    val reason = r.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    Reason(
                        inputName = reason.obj("extra_info")?.str("input_name"),
                        message = reason.str("message") ?: reason.str("type") ?: "Error",
                        details = reason.str("details")
                    )
                }.orEmpty()
                NodeError(nodeId, classType, titleOf(nodeId, classType, sentPrompt), reasons)
            }.orEmpty()

        private fun titleOf(nodeId: String, classType: String, sentPrompt: JsonObject?): String =
            sentPrompt?.obj(nodeId)?.obj("_meta")?.str("title")
                ?: classType.ifEmpty { "Node $nodeId" }

        private fun JsonObject.str(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString

        private fun JsonObject.obj(key: String): JsonObject? =
            get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    }
}
