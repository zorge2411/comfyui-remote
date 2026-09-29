package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowCompatibilityTest {

    private fun obj(s: String): JsonObject = JsonParser.parseString(s.trimIndent()).asJsonObject

    private val objectInfo = obj("""
        {
          "Ckpt": { "input": { "required": { "ckpt_name": [["sd_xl_base.safetensors"], {}] } },
                    "output": ["MODEL"], "output_node": false },
          "LoadImage": { "input": { "required": { "image": [["cat.png"], {"image_upload": true}] } },
                    "output": ["IMAGE"], "output_node": false },
          "Sampler": { "input": { "required": { "model": ["MODEL", {}], "steps": ["INT", {"min": 1, "max": 150}] },
                                  "optional": { "image": ["IMAGE", {}] } },
                    "output": ["LATENT"], "output_node": false },
          "Save": { "input": { "required": { "samples": ["LATENT", {}] } }, "output": [], "output_node": true }
        }
    """)

    private fun prompt(ckpt: String = "sd_xl_base.safetensors", steps: Int = 30, image: String? = null, extra: String = ""): JsonObject {
        val load = image?.let { """, "5": {"class_type": "LoadImage", "inputs": {"image": "$it"}}""" } ?: ""
        val imageLink = if (image != null) """, "image": ["5", 0]""" else ""
        return obj("""
            {
              "1": {"class_type": "Ckpt", "inputs": {"ckpt_name": "$ckpt"}},
              "2": {"class_type": "Sampler", "inputs": {"model": ["1", 0], "steps": $steps$imageLink}},
              "3": {"class_type": "Save", "inputs": {"samples": ["2", 0]}}
              $load $extra
            }
        """)
    }

    @Test
    fun `a clean prompt is ready`() {
        assertEquals(Compatibility.Ready, WorkflowCompatibility.check(prompt(), objectInfo))
    }

    @Test
    fun `a missing node type will fail`() {
        val result = WorkflowCompatibility.check(prompt(extra = """, "9": {"class_type": "Custom", "inputs": {}}"""), objectInfo)
        assertTrue(result is Compatibility.WillFail)
        assertEquals(PromptValidator.Kind.MISSING_NODE_TYPE, (result as Compatibility.WillFail).issues.first().kind)
    }

    @Test
    fun `an out of range value only warns`() {
        assertTrue(WorkflowCompatibility.check(prompt(steps = 500), objectInfo) is Compatibility.Warnings)
    }

    @Test
    fun `an input image missing on the server only warns`() {
        val result = WorkflowCompatibility.check(prompt(image = "dog.png"), objectInfo)
        assertTrue(result is Compatibility.Warnings)
        assertEquals(PromptValidator.Kind.VALUE_NOT_IN_LIST, (result as Compatibility.Warnings).issues.single().kind)
    }

    @Test
    fun `a missing checkpoint will fail`() {
        assertTrue(WorkflowCompatibility.check(prompt(ckpt = "juggernaut.safetensors"), objectInfo) is Compatibility.WillFail)
    }
}
