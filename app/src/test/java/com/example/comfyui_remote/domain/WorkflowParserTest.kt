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

    // --- Phase 94: V3 combos, dynamic combo keys and dotted sub-inputs ---

    private val v3Metadata = com.google.gson.JsonParser.parseString("""
        {
          "SaveVid": { "input": {
            "required": {
              "filename_prefix": ["STRING", {}],
              "format": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                {"key": "auto", "inputs": {"required": {}}},
                {"key": "mp4", "inputs": {"required": {
                  "codec": ["COMBO", {"options": ["auto", "h264", "av1"]}]}}}]}]
            } } },
          "Resize": { "input": { "required": {
            "resize_type": ["COMFY_DYNAMICCOMBO_V3", {"options": [
              {"key": "scale total pixels", "inputs": {"required": {"megapixels": ["FLOAT", {}]}}}]}],
            "scale_method": ["COMBO", {"options": ["nearest-exact", "area", "lanczos"]}]
          } } }
        }
    """.trimIndent()).asJsonObject

    private val v3Prompt = """
        {
          "1": { "class_type": "Resize", "_meta": { "title": "Resize" },
                 "inputs": { "resize_type": "scale total pixels", "resize_type.megapixels": 1.5, "scale_method": "area" } },
          "2": { "class_type": "SaveVid", "_meta": { "title": "Save" },
                 "inputs": { "filename_prefix": "video/ComfyUI", "format": "mp4", "format.codec": "h264" } }
        }
    """.trimIndent()

    private fun field(fields: List<InputField>, node: String, name: String) =
        fields.firstOrNull { it.nodeId == node && it.fieldName == name }

    @Test
    fun `V3 COMBO inputs become dropdowns with their options`() {
        val scale = field(parser.parse(v3Prompt, v3Metadata), "1", "scale_method")
        assertTrue(scale is InputField.SelectionInput)
        assertEquals(listOf("nearest-exact", "area", "lanczos"), (scale as InputField.SelectionInput).options)
    }

    @Test
    fun `dynamic combo keys are not editable but stay in the prompt`() {
        val fields = parser.parse(v3Prompt, v3Metadata)
        assertNull(field(fields, "2", "format"))
        assertNull(field(fields, "1", "resize_type"))

        val patched = com.google.gson.JsonParser.parseString(WorkflowExecutor().injectValues(v3Prompt, fields)).asJsonObject
        assertEquals("mp4", patched.getAsJsonObject("2").getAsJsonObject("inputs").get("format").asString)
    }

    @Test
    fun `dotted sub-input resolves options through the selected option`() {
        val codec = field(parser.parse(v3Prompt, v3Metadata), "2", "format.codec")
        assertTrue(codec is InputField.SelectionInput)
        assertEquals(listOf("auto", "h264", "av1"), (codec as InputField.SelectionInput).options)
        assertEquals("format › codec", codec.displayName)
    }

    @Test
    fun `dotted numeric sub-input becomes a float field`() {
        val mp = field(parser.parse(v3Prompt, v3Metadata), "1", "resize_type.megapixels")
        assertTrue(mp is InputField.FloatInput)
    }

    @Test
    fun `dotted key whose option is not selected falls back to heuristics`() {
        val prompt = v3Prompt.replace("\"format\": \"mp4\"", "\"format\": \"mkv\"")
        val codec = field(parser.parse(prompt, v3Metadata), "2", "format.codec")
        assertTrue(codec is InputField.StringInput)
    }
}
