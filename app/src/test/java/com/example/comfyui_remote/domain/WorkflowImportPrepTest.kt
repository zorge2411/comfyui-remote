package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.WorkflowImportPrep.Reason
import com.example.comfyui_remote.domain.WorkflowImportPrep.Result
import com.example.comfyui_remote.domain.WorkflowImportPrep.Step
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class WorkflowImportPrepTest {

    private val nodeMetadata: JsonObject = JsonParser.parseString("""
        {
          "Src": { "input": { "required": {} }, "output": ["IMAGE"] },
          "Save": { "input": { "required": { "images": ["IMAGE", {}] } }, "output": [], "output_node": true }
        }
    """.trimIndent()).asJsonObject

    private val apiFormat = """{"1": {"class_type": "Save", "inputs": {"images": ["2", 0]}}}"""

    private val graphFormat = """
        {"nodes": [
           {"id": 1, "type": "Src", "mode": 0, "inputs": [], "outputs": [{"name": "o", "type": "IMAGE"}], "widgets_values": []},
           {"id": 2, "type": "Save", "mode": 0, "inputs": [{"name": "images", "type": "IMAGE", "link": 10}], "widgets_values": []}],
         "links": [[10, 1, 0, 2, 0, "IMAGE"]]}
    """.trimIndent()

    @Test
    fun `API format is stored as is, with or without the node list`() {
        for (meta in listOf(null, nodeMetadata)) {
            val result = WorkflowImportPrep.prepare(apiFormat, meta) as Result.Ready
            assertEquals(apiFormat, result.json)
            assertTrue(result.missingNodes.isEmpty())
        }
    }

    @Test
    fun `graph format with the node list is converted`() {
        val result = WorkflowImportPrep.prepare(graphFormat, nodeMetadata) as Result.Ready
        val api = JsonParser.parseString(result.json).asJsonObject
        assertFalse("converted prompt must not keep the graph arrays", api.has("nodes"))
        assertEquals("Save", api.getAsJsonObject("2").get("class_type").asString)
        assertEquals("1", api.getAsJsonObject("2").getAsJsonObject("inputs").getAsJsonArray("images")[0].asString)
    }

    @Test
    fun `graph format without the node list is never stored`() {
        val result = WorkflowImportPrep.prepare(graphFormat, null)
        assertEquals(Result.Failed(Reason.NEEDS_SERVER_NODE_LIST), result)
    }

    @Test
    fun `a conversion that throws is a failure with its detail`() {
        // A node without a type makes the converter throw
        val broken = """{"nodes": [{"id": 1}], "links": []}"""
        val result = WorkflowImportPrep.prepare(broken, nodeMetadata)
        assertTrue(result is Result.Failed)
        assertEquals(Reason.CONVERSION_FAILED, (result as Result.Failed).reason)
        assertFalse(result.detail.isNullOrBlank())
    }

    @Test
    fun `invalid JSON, arrays and empty text are rejected`() {
        for (text in listOf("{not json", "[1, 2]", "", "   ", "\"text\"")) {
            assertEquals(text, Result.Failed(Reason.INVALID_JSON), WorkflowImportPrep.prepare(text, nodeMetadata))
        }
    }

    @Test
    fun `graph format never comes back unconverted`() {
        for (meta in listOf(null, nodeMetadata)) {
            val result = WorkflowImportPrep.prepare(graphFormat, meta)
            if (result is Result.Ready) assertNotEquals(graphFormat, result.json)
        }
    }

    @Test
    fun `describe explains connection problems with the server address`() {
        for (e in listOf(UnknownHostException("x"), ConnectException("refused"), SocketTimeoutException("slow"))) {
            assertEquals(
                "Can't reach the server at 192.168.1.5:8188. Check the connection.",
                WorkflowImportPrep.describe(e, serverAddress = "192.168.1.5:8188")
            )
        }
        assertEquals("Can't reach the server. Check the connection.", WorkflowImportPrep.describe(ConnectException()))
    }

    @Test
    fun `describe maps HTTP codes and falls back to the message`() {
        assertEquals("The server doesn't have that file.", WorkflowImportPrep.describe(null, httpCode = 404))
        assertEquals("The server answered HTTP 500.", WorkflowImportPrep.describe(null, httpCode = 500))
        assertEquals("disk full", WorkflowImportPrep.describe(IllegalStateException("disk full")))
        assertEquals("IllegalStateException", WorkflowImportPrep.describe(IllegalStateException()))
        assertEquals("Something went wrong.", WorkflowImportPrep.describe(null))
    }

    @Test
    fun `failure text names the workflow and the step`() {
        val failure = WorkflowImportPrep.fromPrepFailure(Result.Failed(Reason.NEEDS_SERVER_NODE_LIST), "My flow")
        assertEquals(Step.CONVERT, failure.step)
        assertEquals(
            "Couldn't import \"My flow\" (converting):\n" +
                "The server's node list is needed to convert this workflow, and it couldn't be loaded. " +
                "Connect to your server and try again.",
            failure.format()
        )
        assertEquals(
            "Couldn't import \"x\" (downloading):\nThe server doesn't have that file.",
            WorkflowImportPrep.ImportFailure(Step.FETCH, "x", WorkflowImportPrep.describe(null, httpCode = 404)).format()
        )
        assertTrue(
            WorkflowImportPrep.fromPrepFailure(Result.Failed(Reason.CONVERSION_FAILED, "boom"), "y").message.endsWith("boom")
        )
    }
}
