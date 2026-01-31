package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class WorkflowParserTest {

    private val parser = WorkflowParser()

    @Test
    fun `parse detects Float inputs`() {
        val json = """
            {
                "1": {
                    "class_type": "KSampler",
                    "_meta": { "title": "Sampler" },
                    "inputs": {
                        "cfg": 7.0,
                        "denoise": 0.75,
                        "steps": 20
                    }
                }
            }
        """.trimIndent()

        val inputs = parser.parse(json)
        
        val cfg = inputs.find { it.fieldName == "cfg" }
        val denoise = inputs.find { it.fieldName == "denoise" }
        val steps = inputs.find { it.fieldName == "steps" }

        assertTrue("CFG should be FloatInput", cfg is InputField.FloatInput)
        assertEquals(7.0f, (cfg as InputField.FloatInput).value, 0.001f)

        assertTrue("Denoise should be FloatInput", denoise is InputField.FloatInput)
        assertEquals(0.75f, (denoise as InputField.FloatInput).value, 0.001f)

        assertTrue("Steps should be IntInput", steps is InputField.IntInput)
        assertEquals(20, (steps as InputField.IntInput).value)
    }

    @Test
    fun `parse detects generic loaders`() {
        val json = """
            {
                "1": {
                    "class_type": "LoraLoader",
                    "_meta": { "title": "Load LoRA" },
                    "inputs": {
                        "lora_name": "my_lora.safetensors",
                        "strength_model": 1.0
                    }
                }
            }
        """.trimIndent()

        val inputs = parser.parse(json)
        
        val lora = inputs.find { it.fieldName == "lora_name" }
        
        assertNotNull(lora)
        // We mapped generically to ModelInput (or SelectionInput if metadata existed, but here tested without metadata)
        assertTrue("lora_name should be ModelInput", lora is InputField.ModelInput)
        assertEquals("my_lora.safetensors", (lora as InputField.ModelInput).value)
    }
}
