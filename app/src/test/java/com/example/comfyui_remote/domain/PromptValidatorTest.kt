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
        assertEquals(listOf(Kind.VALUE_NOT_IN_LIST), kinds(prompt(sampler = "\"steps\": 30, \"cfg\": 7.5, \"sampler_name\": \"Euler A\"")))
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
        assertEquals(PromptValidator.Severity.WARNING, model.severity)
        assertEquals("Load Checkpoint", model.nodeTitle)
        assertEquals("Sampler", issues.single { it.kind == Kind.REQUIRED_INPUT_MISSING }.nodeTitle)
    }

    @Test
    fun `wrapped list widget values are not links`() {
        assertEquals(emptyList<Kind>(), kinds(prompt(extra = "").apply {
            getAsJsonObject("3").getAsJsonObject("inputs").add("extra", JsonParser.parseString("""{"__value__": ["", ""]}"""))
        }))
    }
}
