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

    @Test
    fun `parse gives LoadImage's image field the picker, not a combo dropdown, even when server metadata declares combo options`() {
        // Real ComfyUI servers declare LoadImage's "image" input as a combo list of
        // already-uploaded server filenames in /object_info. Before the fix, the generic
        // combo-options branch was checked first and always won, so LoadImage's image field
        // rendered as a SelectionInput dropdown instead of the gallery/camera ImageInput
        // picker — silently disabling img2img image selection on any real server.
        val json = """
            {
                "1": {
                    "class_type": "LoadImage",
                    "_meta": { "title": "Load Image" },
                    "inputs": {
                        "image": "example.png"
                    }
                }
            }
        """.trimIndent()

        val metaJson = """
            {
                "LoadImage": {
                    "input": {
                        "required": {
                            "image": [["example.png", "other_uploaded.png"], {"image_upload": true}]
                        }
                    }
                }
            }
        """.trimIndent()
        val metadata = com.google.gson.JsonParser.parseString(metaJson).asJsonObject

        val inputs = parser.parse(json, metadata)

        val image = inputs.find { it.fieldName == "image" }
        assertTrue("LoadImage's image field should be ImageInput despite combo metadata", image is InputField.ImageInput)
        assertEquals("example.png", (image as InputField.ImageInput).value)
    }

    @Test
    fun `parse moves positive prompt to front via sampler topology`() {
        // Node "5" (negative) sits before node "10" (positive) in JSON key order, and the
        // sampler ("20") is last. Without topology-based reordering, "5"'s text field
        // would render before "10"'s despite "10" being the positive prompt.
        val json = """
            {
                "5": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Negative" },
                    "inputs": { "text": "blurry, low quality" }
                },
                "10": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Positive" },
                    "inputs": { "text": "a scenic mountain landscape" }
                },
                "20": {
                    "class_type": "KSampler",
                    "_meta": { "title": "Sampler" },
                    "inputs": {
                        "positive": ["10", 0],
                        "negative": ["5", 0],
                        "steps": 20
                    }
                }
            }
        """.trimIndent()

        val inputs = parser.parse(json)

        assertTrue("First field should be from the positive-source node", inputs.isNotEmpty())
        assertEquals("10", inputs.first().nodeId)
        assertEquals("a scenic mountain landscape", (inputs.first() as InputField.StringInput).value)

        val negativeIndex = inputs.indexOfFirst { it.nodeId == "5" }
        assertTrue("Negative prompt field should still be present, after the positive one", negativeIndex > 0)
    }

    @Test
    fun `parse leaves order unchanged when no sampler node is found`() {
        val json = """
            {
                "1": {
                    "class_type": "LoadImage",
                    "_meta": { "title": "Load Image" },
                    "inputs": { "image": "example.png" }
                },
                "2": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Prompt" },
                    "inputs": { "text": "a scenic mountain landscape" }
                }
            }
        """.trimIndent()

        val inputs = parser.parse(json)

        assertEquals(2, inputs.size)
        assertEquals("1", inputs[0].nodeId)
        assertEquals("2", inputs[1].nodeId)
    }

    @Test
    fun `parse leaves order unchanged when multiple samplers disagree on positive source`() {
        val json = """
            {
                "5": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Prompt A" },
                    "inputs": { "text": "prompt a" }
                },
                "6": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Prompt B" },
                    "inputs": { "text": "prompt b" }
                },
                "20": {
                    "class_type": "KSampler",
                    "_meta": { "title": "Sampler 1" },
                    "inputs": {
                        "positive": ["5", 0],
                        "negative": ["6", 0]
                    }
                },
                "21": {
                    "class_type": "KSampler",
                    "_meta": { "title": "Sampler 2" },
                    "inputs": {
                        "positive": ["6", 0],
                        "negative": ["5", 0]
                    }
                }
            }
        """.trimIndent()

        val inputs = parser.parse(json)

        assertEquals(2, inputs.size)
        assertEquals("5", inputs[0].nodeId)
        assertEquals("6", inputs[1].nodeId)
    }

    @Test
    fun `parse leaves order unchanged when positive source node has no primitive fields`() {
        // Node "10"'s own "text" input is itself a link (fed by another node), so it
        // produces zero InputFields of its own — nothing to hoist to the front.
        val json = """
            {
                "5": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Negative" },
                    "inputs": { "text": "blurry" }
                },
                "9": {
                    "class_type": "StringConcat",
                    "_meta": { "title": "Combine" },
                    "inputs": { "a": "part one", "b": "part two" }
                },
                "10": {
                    "class_type": "CLIPTextEncode",
                    "_meta": { "title": "Positive" },
                    "inputs": { "text": ["9", 0] }
                },
                "20": {
                    "class_type": "KSampler",
                    "_meta": { "title": "Sampler" },
                    "inputs": {
                        "positive": ["10", 0],
                        "negative": ["5", 0]
                    }
                }
            }
        """.trimIndent()

        val inputs = parser.parse(json)

        assertEquals(3, inputs.size)
        assertEquals("5", inputs[0].nodeId)
        assertEquals("9", inputs[1].nodeId)
        assertEquals("9", inputs[2].nodeId)
    }
}
