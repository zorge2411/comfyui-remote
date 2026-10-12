package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.PromptValidator.Issue
import com.example.comfyui_remote.domain.PromptValidator.Kind
import com.example.comfyui_remote.domain.WorkflowCompatibility.Level
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class WorkflowCompatibilityTest {

    private fun issue(kind: Kind, cls: String = "K", title: String = "Sampler", input: String? = null, msg: String = "m") =
        Issue("1", cls, title, kind, input, msg)

    private val info: JsonObject = JsonParser.parseString("""
        {
          "Load": {"input": {"required": {"image": [["a.png", "b.png"], {}]}}, "output": ["IMAGE"]},
          "Ckpt": {"input": {"required": {"ckpt_name": [["good.safetensors"], {}]}}, "output": ["MODEL"]},
          "Img": {"input": {"required": {"ckpt_name": [["good.safetensors"], {}]}}, "output": ["IMAGE"]},
          "Save": {"input": {"required": {"images": ["IMAGE", {}]}}, "output": [], "output_node": true}
        }
    """.trimIndent()).asJsonObject

    @Test fun `no issues is ready`() {
        assertEquals(WorkflowCompatibility.Result(Level.READY, ""), WorkflowCompatibility.assess(emptyList()))
    }

    @Test fun `warnings only counts them`() {
        val one = WorkflowCompatibility.assess(listOf(issue(Kind.OUT_OF_RANGE)))
        assertEquals(WorkflowCompatibility.Result(Level.WARNINGS, "1 warning"), one)
        val two = WorkflowCompatibility.assess(listOf(issue(Kind.OUT_OF_RANGE), issue(Kind.VALUE_NOT_IN_LIST)))
        assertEquals("2 warnings", two.summary)
    }

    @Test fun `missing nodes are listed once each, sorted, ahead of other errors`() {
        val r = WorkflowCompatibility.assess(listOf(
            issue(Kind.BAD_LINK, msg = "bad"),
            issue(Kind.MISSING_NODE_TYPE, cls = "Zed"),
            issue(Kind.MISSING_NODE_TYPE, cls = "Alpha"),
            issue(Kind.MISSING_NODE_TYPE, cls = "Zed")
        ))
        assertEquals(Level.WILL_FAIL, r.level)
        assertEquals("Missing nodes: Alpha, Zed", r.summary)
    }

    @Test fun `other errors use the node title and message`() {
        val r = WorkflowCompatibility.assess(listOf(issue(Kind.REQUIRED_INPUT_MISSING, title = "KSampler", msg = "Required input is missing")))
        assertEquals(WorkflowCompatibility.Result(Level.WILL_FAIL, "KSampler: Required input is missing"), r)
    }

    @Test fun `check of a valid workflow is ready`() {
        val json = """{"1": {"class_type": "Load", "inputs": {"image": "a.png"}}, "2": {"class_type": "Save", "inputs": {"images": ["1", 0]}}}"""
        assertEquals(Level.READY, WorkflowCompatibility.check(json, info)?.level)
    }

    @Test fun `check of an unknown class fails with the class name`() {
        val json = """{"1": {"class_type": "Nope", "inputs": {}}, "2": {"class_type": "Save", "inputs": {"images": ["1", 0]}}}"""
        val r = WorkflowCompatibility.check(json, info)!!
        assertEquals(Level.WILL_FAIL, r.level)
        assertEquals("Missing nodes: Nope", r.summary)
    }

    @Test fun `an image file the server lacks is not flagged`() {
        val json = """{"1": {"class_type": "Load", "inputs": {"image": "gone.png"}}, "2": {"class_type": "Save", "inputs": {"images": ["1", 0]}}}"""
        assertEquals(Level.READY, WorkflowCompatibility.check(json, info)?.level)
    }

    @Test fun `a model file the server lacks is a warning`() {
        val json = """{"1": {"class_type": "Img", "inputs": {"ckpt_name": "other.safetensors"}}, "2": {"class_type": "Save", "inputs": {"images": ["1", 0]}}}"""
        assertEquals(WorkflowCompatibility.Result(Level.WARNINGS, "1 warning"), WorkflowCompatibility.check(json, info))
    }

    @Test fun `unreadable JSON gives no result`() {
        for (t in listOf("{nope", "[1]", "")) assertNull(t, WorkflowCompatibility.check(t, info))
    }
}
