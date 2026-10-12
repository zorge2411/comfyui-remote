package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Decides what an imported workflow is stored as (Phase 97). A graph-format (editor) workflow is only ever
 * stored after a successful conversion: stored unconverted it shows no form fields and the server rejects
 * it, so it looks imported but can never run.
 */
object WorkflowImportPrep {

    enum class Reason { INVALID_JSON, NEEDS_SERVER_NODE_LIST, CONVERSION_FAILED }

    sealed class Result {
        data class Ready(val json: String, val missingNodes: List<String>) : Result()
        data class Failed(val reason: Reason, val detail: String? = null) : Result()
    }

    enum class Step(val label: String) {
        FETCH("downloading"),
        METADATA("loading the server's node list"),
        CONVERT("converting"),
        SAVE("saving")
    }

    /** What went wrong importing [name], and where. */
    data class ImportFailure(val step: Step, val name: String, val message: String) {
        fun format(): String = "Couldn't import \"$name\" (${step.label}):\n$message"
    }

    fun prepare(json: String, nodeMetadata: JsonObject?): Result {
        val root = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return Result.Failed(Reason.INVALID_JSON)

        val isGraphFormat = root.has("nodes") && root.has("links")
        if (!isGraphFormat) return Result.Ready(json, emptyList())
        if (nodeMetadata == null) return Result.Failed(Reason.NEEDS_SERVER_NODE_LIST)

        return try {
            val converted = GraphToApiConverter.convert(json, ComfyObjectInfo(nodeMetadata))
            Result.Ready(converted.json, converted.missingNodes)
        } catch (e: Exception) {
            Result.Failed(Reason.CONVERSION_FAILED, e.message ?: e.javaClass.simpleName)
        }
    }

    /** A failed [prepare] as the failure to show. */
    fun fromPrepFailure(failed: Result.Failed, name: String): ImportFailure {
        val message = when (failed.reason) {
            Reason.INVALID_JSON -> "That isn't valid workflow JSON."
            Reason.NEEDS_SERVER_NODE_LIST ->
                "The server's node list is needed to convert this workflow, and it couldn't be loaded. " +
                    "Connect to your server and try again."
            Reason.CONVERSION_FAILED -> "The workflow couldn't be converted: ${failed.detail ?: "unknown error"}"
        }
        return ImportFailure(Step.CONVERT, name, message)
    }

    /** Plain-language text for an exception. HTTP errors are passed as [httpCode] so no Retrofit type is needed. */
    fun describe(cause: Throwable?, httpCode: Int? = null, serverAddress: String? = null): String {
        val server = serverAddress?.let { "the server at $it" } ?: "the server"
        return when {
            httpCode == 404 -> "The server doesn't have that file."
            httpCode != null -> "The server answered HTTP $httpCode."
            cause is UnknownHostException || cause is ConnectException ||
                cause is NoRouteToHostException || cause is SocketTimeoutException ->
                "Can't reach $server. Check the connection."
            cause is SSLException -> "Couldn't make a secure connection to $server."
            cause == null -> "Something went wrong."
            else -> cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
        }
    }
}
