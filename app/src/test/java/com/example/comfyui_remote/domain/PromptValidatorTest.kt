package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.PromptValidator.Kind
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class PromptValidatorTest {

    private val objectInfo = obj("""
        {
          "Ckpt": { "input": { "required": { "ckpt_name": [["sd_xl_base.safetensors", "flux1-dev.safetensors"], {}] } },
                    "output": ["MODEL", "CLIP", "VAE"], "output_node": false },
          "Sampler": { "input": { "required": {
              "model": ["MODEL", {}],
              "steps": ["INT", {"default": 20, "min": 1, "max": 150}],
              "cfg": ["FLOAT", {"min": 0.0, "max": 30.0}],
              "sampler_name": ["COMBO", {"options": ["euler", "dpmpp_2m"]}] } },
            "output": ["LATENT"], "output_node": false },
          "Save": { "input": { "required": { "samples": ["LATENT", {}] } }, "output": [], "output_node": true },
          "Loose": { "input": { "required": { "x": ["INT", {}] } }, "output": ["INT"], "output_node": false }
        }
    """)

    private fun obj(s: String): JsonObject = JsonParser.parseString(s.trimIndent()).asJsonObject

    private fun prompt(sampler: String = "\"steps\": 30, \"cfg\": 7.5, \"sampler_name\": \"euler\"",
                       ckpt: String = "sd_xl_base.safetensors", extra: String = "") = obj("""
        {
          "1": {"class_type": "Ckpt", "inputs": {"ckpt_name": "$ckpt"}, "_meta": {"title": "Load Checkpoint"}},
          "2": {"class_type": "Sampler", "inputs": {"model": ["1", 0], $sampler}},
          "3": {"class_type": "Save", "inputs": {"samples": ["2", 0]}}
          $extra
        }
    """)

    private fun kinds(p: JsonObject, checkFiles: Boolean = true) =
        PromptValidator.validate(p, objectInfo, checkFileValues = checkFiles).map { it.kind }

    @Test
    fun `clean prompt has no issues`() {
        assertEquals(emptyList<Kind>(), kinds(prompt()))
    }

    @Test
    fun `missing node type`() {
        assertEquals(listOf(Kind.MISSING_NODE_TYPE), kinds(prompt(extra = """, "9": {"class_type": "CustomThing", "inputs": {}}""")))
    }

    @Test
    fun `required input missing`() {
        assertEquals(listOf(Kind.REQUIRED_INPUT_MISSING), kinds(prompt(sampler = "\"cfg\": 7.5, \"sampler_name\": \"euler\"")))
    }

    @Test
    fun `bad link and type mismatch`() {
        assertEquals(listOf(Kind.BAD_LINK), kinds(obj("""
            {"2": {"class_type": "Sampler", "inputs": {"model": ["7", 0], "steps": 30, "cfg": 7.5, "sampler_name": "euler"}},
             "3": {"class_type": "Save", "inputs": {"samples": ["2", 0]}}}
        """)))
        assertEquals(listOf(Kind.TYPE_MISMATCH), kinds(prompt().apply {
            getAsJsonObject("2").getAsJsonObject("inputs").add("model", JsonParser.parseString("""["1", 2]"""))
        }))
    }

    @Test
    fun `range and conversion follow the server's wording`() {
        val issues = PromptValidator.validate(prompt(sampler = "\"steps\": 200, \"cfg\": \"abc\", \"sampler_name\": \"euler\""), objectInfo)
        assertEquals(setOf(Kind.OUT_OF_RANGE, Kind.INVALID_VALUE_TYPE), issues.map { it.kind }.toSet())
        assertEquals("Value 200 bigger than max of 150", issues.single { it.kind == Kind.OUT_OF_RANGE }.message)
    }

    @Test
    fun `value not in list, and missing model files only when file checks are on`() {
        val badSampler = PromptValidator.validate(prompt(sampler = "\"steps\": 30, \"cfg\": 7.5, \"sampler_name\": \"Euler A\""), objectInfo)
        assertEquals(listOf(Kind.VALUE_NOT_IN_LIST), badSampler.map { it.kind })
        assertEquals(PromptValidator.Severity.WARNING, badSampler.single().severity)
        val missingModel = prompt(ckpt = "juggernaut.safetensors")
        assertEquals(listOf(Kind.VALUE_NOT_IN_LIST), kinds(missingModel))
        assertEquals(emptyList<Kind>(), kinds(missingModel, checkFiles = false))
    }

    @Test
    fun `nodes not feeding an output are not validated`() {
        assertEquals(emptyList<Kind>(), kinds(prompt(extra = """, "8": {"class_type": "Loose", "inputs": {}}""")))
    }

    @Test
    fun `prompt without an output node`() {
        assertEquals(listOf(Kind.NO_OUTPUT_NODE), kinds(obj("""{"1": {"class_type": "Ckpt", "inputs": {"ckpt_name": "sd_xl_base.safetensors"}}}""")))
    }

    @Test
    fun `severity and node title`() {
        val issues = PromptValidator.validate(prompt(ckpt = "missing.safetensors", sampler = "\"cfg\": 7.5, \"sampler_name\": \"euler\""), objectInfo)
        assertEquals(PromptValidator.Severity.ERROR, issues.single { it.kind == Kind.REQUIRED_INPUT_MISSING }.severity)
        val model = issues.single { it.kind == Kind.VALUE_NOT_IN_LIST }
        assertEquals(PromptValidator.Severity.ERROR, model.severity)
        assertEquals("File not on the server: 'missing.safetensors' not in [sd_xl_base.safetensors, flux1-dev.safetensors]", model.message)
        assertEquals("Load Checkpoint", model.nodeTitle)
        assertEquals("Sampler", issues.single { it.kind == Kind.REQUIRED_INPUT_MISSING }.nodeTitle)
    }

    @Test
    fun `wrapped list widget values are not links`() {
        assertEquals(emptyList<Kind>(), kinds(prompt(extra = "").apply {
            getAsJsonObject("3").getAsJsonObject("inputs").add("extra", JsonParser.parseString("""{"__value__": ["", ""]}"""))
        }))
    }

    // Phase 98: the server lists no files for an input (e.g. no videos in its input folder)
    private val emptyListInfo = obj("""
        {
          "LoadVideo": { "input": { "required": { "file": ["COMBO", {"multiselect": false, "options": [], "video_upload": true}] } },
                         "output": ["VIDEO"], "output_node": false },
          "LoadImg": { "input": { "required": { "image": [[], {"image_upload": true}] } }, "output": ["IMAGE"], "output_node": false },
          "Pick": { "input": { "required": { "mode": ["COMBO", {"options": []}] } }, "output": ["INT"], "output_node": false },
          "Out": { "input": { "required": { "video": ["VIDEO", {}], "image": ["IMAGE", {}], "n": ["INT", {}] } },
                   "output": [], "output_node": true }
        }
    """)

    private fun emptyListPrompt(video: String = "gan_input.mp4", image: String = "example.png", mode: String = "fast") = obj("""
        {
          "1": {"class_type": "LoadVideo", "inputs": {"file": "$video"}},
          "2": {"class_type": "LoadImg", "inputs": {"image": "$image"}},
          "3": {"class_type": "Pick", "inputs": {"mode": "$mode"}},
          "4": {"class_type": "Out", "inputs": {"video": ["1", 0], "image": ["2", 0], "n": ["3", 0]}}
        }
    """)

    @Test
    fun `a file value is missing when the server lists no files for the input`() {
        val issues = PromptValidator.validate(emptyListPrompt(), emptyListInfo)
        assertEquals(setOf("1", "2"), issues.map { it.nodeId }.toSet())
        val video = issues.single { it.nodeId == "1" }
        assertEquals(Kind.VALUE_NOT_IN_LIST, video.kind)
        assertEquals(PromptValidator.Severity.ERROR, video.severity)
        assertEquals(
            "File not on the server: 'gan_input.mp4' (the server has none for this input); pick another file or upload it",
            video.message
        )
    }

    @Test
    fun `a non-file value on an empty non-upload list is not reported`() {
        val issues = PromptValidator.validate(emptyListPrompt(), emptyListInfo)
        assertTrue(issues.none { it.nodeId == "3" })
    }

    // Phase 103 device check: an Ollama node's model tag has a slash but isn't a file on the server
    @Test
    fun `a remote model tag on an empty list is not a missing file`() {
        val info = obj("""
            {
              "Ollama": { "input": { "required": { "model": ["COMBO", {"options": []}] } }, "output": ["STRING"], "output_node": false },
              "Out": { "input": { "required": { "s": ["STRING", {}] } }, "output": [], "output_node": true }
            }
        """)
        val prompt = obj("""
            {
              "1": {"class_type": "Ollama", "inputs": {"model": "hf.co/user/some-model-GGUF:Q8_0"}},
              "2": {"class_type": "Out", "inputs": {"s": ["1", 0]}}
            }
        """)
        assertEquals(emptyList<Kind>(), PromptValidator.validate(prompt, info).map { it.kind })
        // A model file on an empty list is still reported
        prompt.getAsJsonObject("1").getAsJsonObject("inputs").addProperty("model", "loras/detail.safetensors")
        assertEquals(listOf(Kind.VALUE_NOT_IN_LIST), PromptValidator.validate(prompt, info).map { it.kind })
    }

    @Test
    fun `empty lists are not checked when file checks are off`() {
        assertEquals(emptyList<Kind>(), PromptValidator.validate(emptyListPrompt(), emptyListInfo, checkFileValues = false).map { it.kind })
    }
}
