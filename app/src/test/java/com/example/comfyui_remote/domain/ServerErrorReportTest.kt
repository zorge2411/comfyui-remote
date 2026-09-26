package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Payload shapes from ComfyUI v0.37.2 server.py post_prompt and execution.py. */
class ServerErrorReportTest {

    private fun obj(s: String): JsonObject = JsonParser.parseString(s.trimIndent()).asJsonObject

    private val sentPrompt = obj("""
        {
          "3": {"class_type": "KSampler", "inputs": {}, "_meta": {"title": "Main Sampler"}},
          "4": {"class_type": "CheckpointLoaderSimple", "inputs": {}, "_meta": {"title": "Load Checkpoint"}},
          "9": {"class_type": "SaveImage", "inputs": {}}
        }
    """)

    private val validationBody = """
        {"error": {"type": "prompt_outputs_failed_validation", "message": "Prompt outputs failed validation",
                   "details": "Required input is missing: cfg\nValue 200 bigger than max of 150: steps", "extra_info": {}},
         "node_errors": {
           "3": {"errors": [
                   {"type": "required_input_missing", "message": "Required input is missing", "details": "cfg",
                    "extra_info": {"input_name": "cfg"}},
                   {"type": "value_bigger_than_max", "message": "Value 200 bigger than max of 150", "details": "steps",
                    "extra_info": {"input_name": "steps", "received_value": 200}}],
                 "dependent_outputs": ["9"], "class_type": "KSampler"},
           "4": {"errors": [
                   {"type": "value_not_in_list", "message": "Value not in list",
                    "details": "ckpt_name: 'juggernaut.safetensors' not in ['sd_xl_base.safetensors']",
                    "extra_info": {"input_name": "ckpt_name"}}],
                 "dependent_outputs": ["9"], "class_type": "CheckpointLoaderSimple"}}}
    """

    @Test
    fun `validation errors list every node and every reason with titles and inputs`() {
        val report = ServerErrorReport.fromPromptError(400, validationBody, sentPrompt)
        assertEquals("Prompt outputs failed validation", report.summary)
        assertEquals(listOf("Main Sampler", "Load Checkpoint"), report.nodes.map { it.title })
        assertEquals(listOf("cfg", "steps"), report.nodes[0].errors.map { it.inputName })
        assertEquals(3, report.nodes.sumOf { it.errors.size })
        assertFalse(report.isPartial)
    }

    @Test
    fun `format lists nodes and reasons and drops details that repeat the input name`() {
        val text = ServerErrorReport.fromPromptError(400, validationBody, sentPrompt).format()
        assertEquals(
            """
            Prompt outputs failed validation

            Main Sampler (KSampler #3)
            • cfg: Required input is missing
            • steps: Value 200 bigger than max of 150

            Load Checkpoint (CheckpointLoaderSimple #4)
            • ckpt_name: Value not in list — ckpt_name: 'juggernaut.safetensors' not in ['sd_xl_base.safetensors']
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `error without node errors keeps its details`() {
        val report = ServerErrorReport.fromPromptError(400, """
            {"error": {"type": "missing_node_type", "message": "Node 'CustomThing' not found. The custom node may not be installed.",
                       "details": "Node ID '#12'", "extra_info": {"node_id": "12", "class_type": "CustomThing"}},
             "node_errors": {}}
        """, sentPrompt)
        assertTrue(report.nodes.isEmpty())
        assertEquals("Node 'CustomThing' not found. The custom node may not be installed.\nNode ID '#12'", report.summary)
    }

    @Test
    fun `unparseable body falls back to the HTTP code and a truncated body`() {
        val html = "<html><body>" + "x".repeat(1000) + "</body></html>"
        val report = ServerErrorReport.fromPromptError(502, html, null)
        assertTrue(report.summary.startsWith("HTTP 502\n<html><body>"))
        assertTrue(report.summary.endsWith("…"))
        assertTrue(report.summary.length < 520)
        assertEquals("HTTP 500", ServerErrorReport.fromPromptError(500, null, null).summary)
    }

    @Test
    fun `partial acceptance reports skipped outputs and nothing when empty`() {
        val nodeErrors = obj(validationBody).getAsJsonObject("node_errors")
        val report = ServerErrorReport.fromPartialAcceptance(nodeErrors, sentPrompt)!!
        assertTrue(report.isPartial)
        assertEquals("Some outputs were skipped by the server", report.summary)
        assertEquals(2, report.nodes.size)
        assertNull(ServerErrorReport.fromPartialAcceptance(JsonObject(), sentPrompt))
        assertNull(ServerErrorReport.fromPartialAcceptance(null, sentPrompt))
    }

    @Test
    fun `execution error names the node and the exception`() {
        val data = obj("""
            {"prompt_id": "abc", "node_id": "3", "node_type": "KSampler", "executed": ["4"],
             "exception_message": "CUDA out of memory. Tried to allocate 2.00 GiB\n",
             "exception_type": "torch.OutOfMemoryError", "traceback": ["..."], "current_inputs": {}, "current_outputs": []}
        """)
        val report = ServerErrorReport.fromExecutionError(data, sentPrompt)
        assertEquals("Execution failed", report.summary)
        assertEquals("Main Sampler", report.nodes.single().title)
        assertEquals(
            "Execution failed\n\nMain Sampler (KSampler #3)\n• OutOfMemoryError: CUDA out of memory. Tried to allocate 2.00 GiB",
            report.format()
        )
    }

    @Test
    fun `title falls back to the class type without a sent prompt`() {
        val report = ServerErrorReport.fromPromptError(400, validationBody, null)
        assertEquals(listOf("KSampler", "CheckpointLoaderSimple"), report.nodes.map { it.title })
        assertTrue(report.format().contains("\nKSampler (#3)\n"))
    }
}
