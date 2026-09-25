package com.example.comfyui_remote.domain.corpus

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiPromptValidatorTest {

    private val objectInfo = obj("""
        {
          "Loader": { "input": { "required": { "ckpt": [[], {}] } }, "output": ["MODEL", "CLIP"] },
          "Sampler": { "input": {
              "required": { "model": ["MODEL", {}], "sampler": [["euler", "dpmpp_2m"], {}],
                            "scheduler": ["COMBO", {"options": ["normal", "karras"]}] },
              "optional": { "steps": ["INT,FLOAT", {}] } },
            "output": ["LATENT"] },
          "Grow": { "input": { "required": { "values": ["COMFY_AUTOGROW_V3", {}] } }, "output": ["STRING"] },
          "Refs": { "input": { "required": { "images": ["COMFY_AUTOGROW_V3", {"template": {"min": 0}}] } }, "output": ["IMAGE"] },
          "Reroute": { "input": { "required": {} }, "output": ["*"] },
          "SaveVid": { "input": {
              "required": { "video": ["VIDEO", {}],
                "format": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                  {"key": "auto", "inputs": {"required": {}}},
                  {"key": "mp4", "inputs": {"required": {
                    "codec": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                      {"key": "auto", "inputs": {"required": {}}},
                      {"key": "h264", "inputs": {"required": {"crf": ["FLOAT", {}], "preset": ["COMBO", {"options": ["fast", "slow"]}]},
                                                 "optional": {"mask": ["MASK", {}]}}}]}]}}}]}] },
              "optional": { "codec": [["auto", "h264"], {}] } },
            "output": [] },
          "Vid": { "input": { "required": {} }, "output": ["VIDEO", "IMAGE"] }
        }
    """)

    private val graph = obj("""{"nodes": [{"id": 1, "mode": 0}, {"id": 2, "mode": 0}, {"id": 9, "mode": 4}]}""")

    private fun obj(s: String): JsonObject = JsonParser.parseString(s.trimIndent()).asJsonObject

    private fun checks(api: String) =
        ApiPromptValidator.validate(obj(api), objectInfo, graph).map { it.check }

    private val loader = """"1": {"class_type": "Loader", "inputs": {"ckpt": "x.safetensors"}}"""

    private fun sampler(model: String = """["1", 0]""", sampler: String = "\"euler\"", scheduler: String = "\"karras\"") =
        """"2": {"class_type": "Sampler", "inputs": {"model": $model, "sampler": $sampler, "scheduler": $scheduler}}"""

    @Test
    fun `clean prompt has no violations`() {
        assertEquals(emptyList<String>(), checks("{ $loader, ${sampler()} }"))
    }

    @Test
    fun `dotted autogrow keys satisfy the required group input`() {
        assertEquals(emptyList<String>(), checks("""{ "3": {"class_type": "Grow", "inputs": {"values.a": "x", "values.b": "y"}} }"""))
    }

    @Test
    fun `empty autogrow group with min 0 is not missing but min 1 is`() {
        assertEquals(emptyList<String>(), checks("""{ "3": {"class_type": "Refs", "inputs": {}} }"""))
        assertEquals(listOf("C4"), checks("""{ "3": {"class_type": "Grow", "inputs": {}} }"""))
    }

    private val vid = """"7": {"class_type": "Vid", "inputs": {}}"""

    private fun saveVid(extra: String) =
        """{ $vid, "8": {"class_type": "SaveVid", "inputs": {"video": ["7", 0], $extra}} }"""

    @Test
    fun `complete nested dynamic combo prompt is clean`() {
        assertEquals(emptyList<String>(), checks(saveVid(
            """"format": "mp4", "format.codec": "h264", "format.codec.crf": 23, "format.codec.preset": "slow"""")))
        assertEquals(emptyList<String>(), checks(saveVid(""""format": "auto"""")))
    }

    @Test
    fun `C4 missing required sub-input of the selected option`() {
        assertEquals(listOf("C4"), checks(saveVid(""""format": "mp4"""")))
        assertEquals(listOf("C4"), checks(saveVid(""""format": "mp4", "format.codec": "h264", "format.codec.crf": 23""")))
    }

    @Test
    fun `C5 invalid sub-combo and unknown option key`() {
        assertEquals(listOf("C5"), checks(saveVid(
            """"format": "mp4", "format.codec": "h264", "format.codec.crf": 23, "format.codec.preset": "medium"""")))
        assertEquals(listOf("C5"), checks(saveVid(""""format": "mkv"""")))
    }

    @Test
    fun `C3 linked sub-input with the wrong type`() {
        assertEquals(listOf("C3"), checks(saveVid(
            """"format": "mp4", "format.codec": "h264", "format.codec.crf": 23, "format.codec.preset": "fast",
               "format.codec.mask": ["7", 1]""")))
    }

    @Test
    fun `C1 unknown class type`() {
        assertEquals(listOf("C1"), checks("""{ "5": {"class_type": "Nope", "inputs": {}} }"""))
    }

    @Test
    fun `C2 link to missing node`() {
        assertEquals(listOf("C2"), checks("{ ${sampler(model = """["7", 0]""")} }"))
    }

    @Test
    fun `C2 link to nonexistent output slot`() {
        assertEquals(listOf("C2"), checks("{ $loader, ${sampler(model = """["1", 5]""")} }"))
    }

    @Test
    fun `C3 link type mismatch`() {
        assertEquals(listOf("C3"), checks("{ $loader, ${sampler(model = """["1", 1]""")} }"))
    }

    @Test
    fun `C3 comma separated types overlap`() {
        val api = """{ $loader, "2": {"class_type": "Sampler", "inputs": {"model": ["1", 0], "sampler": "euler",
            "scheduler": "normal", "steps": ["3", 0]}}, "3": {"class_type": "Grow", "inputs": {"values.a": "1"}} }"""
        assertEquals(listOf("C3"), checks(api)) // STRING does not overlap INT,FLOAT
    }

    @Test
    fun `file-valued combo values are not checked`() {
        assertEquals(emptyList<String>(), checks("{ $loader, ${sampler(sampler = "\"model.safetensors\"")} }"))
    }

    @Test
    fun `match-type outputs link to any input`() {
        val info = objectInfo.deepCopy().apply {
            add("Switch", JsonParser.parseString("""{"input": {"required": {}}, "output": ["COMFY_MATCHTYPE_V3"]}"""))
        }
        val api = obj("""{ "5": {"class_type": "Switch", "inputs": {}}, ${sampler(model = """["5", 0]""")} }""")
        assertEquals(emptyList<ApiPromptValidator.Violation>(), ApiPromptValidator.validate(api, info, graph))
    }

    @Test
    fun `C4 missing required input`() {
        assertEquals(listOf("C4"), checks("""{ $loader, "2": {"class_type": "Sampler", "inputs": {"model": ["1", 0], "scheduler": "normal"}} }"""))
    }

    @Test
    fun `C5 invalid legacy and V3 combo values`() {
        assertEquals(listOf("C5"), checks("{ $loader, ${sampler(sampler = "\"Euler A\"")} }"))
        assertEquals(listOf("C5"), checks("{ $loader, ${sampler(scheduler = "\"Karras\"")} }"))
    }

    @Test
    fun `C6 bypassed node sent`() {
        assertEquals(listOf("C6"), checks("""{ "9": {"class_type": "Loader", "inputs": {"ckpt": "x"}} }"""))
    }

    @Test
    fun `C7 frontend-only node sent`() {
        assertEquals(listOf("C7"), checks("""{ "4": {"class_type": "Reroute", "inputs": {}} }"""))
    }
}
