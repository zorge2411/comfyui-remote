package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** COMFY_DYNAMICCOMBO_V3 inputs (Phase 94): the selected option's inputs become dotted API keys. */
class GraphToApiConverterDynamicComboTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "Src": { "input": { "required": {} }, "output": ["IMAGE"] },
          "Resize": { "input": { "required": {
            "input": ["IMAGE", {}],
            "resize_type": ["COMFY_DYNAMICCOMBO_V3", {"options": [
              {"key": "scale dimensions", "inputs": {"required": {"width": ["INT", {}], "height": ["INT", {}]}}},
              {"key": "scale total pixels", "inputs": {"required": {"megapixels": ["FLOAT", {}]}}}]}],
            "scale_method": ["COMBO", {"options": ["nearest-exact", "area", "lanczos"]}]
          } }, "output": ["IMAGE"] },
          "SaveVid": { "input": {
            "required": {
              "video": ["IMAGE", {}],
              "filename_prefix": ["STRING", {}],
              "format": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                {"key": "auto", "inputs": {"required": {}}},
                {"key": "mp4", "inputs": {"required": {
                  "codec": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                    {"key": "auto", "inputs": {"required": {}}},
                    {"key": "h264", "inputs": {"required": {}, "optional": {
                      "encoding": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                        {"key": "auto", "inputs": {"required": {}}},
                        {"key": "re-encode", "inputs": {"required": {"crf": ["FLOAT", {}]}}}]}]}}}]}]}}}]}]
            },
            "optional": { "codec": [["auto", "h264"], {}] } },
            "output": [] },
          "Gen": { "input": { "required": {
            "model": ["COMFY_DYNAMICCOMBO_V3", {"options": [
              {"key": "m1", "inputs": {
                "required": {
                  "prompt": ["STRING", {}],
                  "images": ["COMFY_AUTOGROW_V3", {"template": {"input": {"required": {"image": ["IMAGE", {}]}}, "prefix": "image_", "min": 0}}],
                  "size": ["COMBO", {"options": ["1024x1024", "1536x1024"]}]},
                "optional": { "mask": ["IMAGE", {}], "strength": ["FLOAT", {}] }}}]}],
            "n": ["INT", {}]
          } }, "output": ["IMAGE"] }
        }
    """.trimIndent()).asJsonObject)

    private fun convert(graph: String): JsonObject =
        JsonParser.parseString(GraphToApiConverter.convert(graph.trimIndent(), objectInfo).json).asJsonObject

    private fun inputsOf(api: JsonObject, id: String) = api.getAsJsonObject(id).getAsJsonObject("inputs")

    private val src = """{"id": 1, "type": "Src", "inputs": [], "outputs": [{"name": "IMAGE", "type": "IMAGE"}], "widgets_values": []}"""

    @Test
    fun `selected option inputs become dotted keys and later widgets stay aligned`() {
        val api = convert("""
            {"nodes": [ $src,
              {"id": 2, "type": "Resize", "inputs": [{"name": "input", "type": "IMAGE", "link": 10}],
               "widgets_values": ["scale total pixels", 1.6, "area"]}
            ], "links": [[10, 1, 0, 2, 0, "IMAGE"]]}
        """)
        val inputs = inputsOf(api, "2")
        assertEquals("scale total pixels", inputs.get("resize_type").asString)
        assertEquals(1.6, inputs.get("resize_type.megapixels").asDouble, 0.0)
        assertEquals("area", inputs.get("scale_method").asString)
        assertFalse(inputs.has("resize_type.width"))
    }

    @Test
    fun `nested dynamic combos expand depth-first and the legacy top-level key takes no saved value`() {
        val api = convert("""
            {"nodes": [ $src,
              {"id": 2, "type": "SaveVid", "inputs": [{"name": "video", "type": "IMAGE", "link": 10}],
               "widgets_values": ["video/ComfyUI", "mp4", "h264", "re-encode", 23]}
            ], "links": [[10, 1, 0, 2, 0, "IMAGE"]]}
        """)
        val inputs = inputsOf(api, "2")
        assertEquals("video/ComfyUI", inputs.get("filename_prefix").asString)
        assertEquals("mp4", inputs.get("format").asString)
        assertEquals("h264", inputs.get("format.codec").asString)
        assertEquals("re-encode", inputs.get("format.codec.encoding").asString)
        assertEquals(23, inputs.get("format.codec.encoding.crf").asInt)
        // The hidden legacy "codec" widget was not in this save; like the frontend, it gets its default
        // ("auto") rather than a value meant for format.codec (Phase 95).
        assertEquals("auto", inputs.get("codec").asString)
    }

    private fun gen(widgets: String, inputs: String) = """
        {"nodes": [ $src,
          {"id": 2, "type": "Gen", "inputs": $inputs, "widgets_values": $widgets}
        ], "links": [[10, 1, 0, 2, 0, "IMAGE"], [11, 1, 0, 2, 1, "IMAGE"]]}
    """

    @Test
    fun `linked socket sub-input is sent and consumes no widget`() {
        val api = convert(gen("""["m1", "a cat", "1536x1024", 0.5, 4]""",
            """[{"name": "model.mask", "type": "IMAGE", "link": 10}]"""))
        val inputs = inputsOf(api, "2")
        assertEquals("1", inputs.getAsJsonArray("model.mask")[0].asString)
        assertEquals("a cat", inputs.get("model.prompt").asString)
        assertEquals("1536x1024", inputs.get("model.size").asString)
        assertEquals(0.5, inputs.get("model.strength").asDouble, 0.0)
        assertEquals(4, inputs.get("n").asInt)
    }

    @Test
    fun `linked autogrow entries inside an option are copied and unlinked ones left out`() {
        val api = convert(gen("""["m1", "a cat", "1024x1024", 0.5, 1]""",
            """[{"name": "model.images.image_1", "type": "IMAGE", "link": 10},
                {"name": "model.images.image_2", "type": "IMAGE", "link": null}]"""))
        val inputs = inputsOf(api, "2")
        assertEquals("1", inputs.getAsJsonArray("model.images.image_1")[0].asString)
        assertFalse(inputs.has("model.images.image_2"))
        assertEquals("a cat", inputs.get("model.prompt").asString)
        assertEquals(1, inputs.get("n").asInt)
    }

    @Test
    fun `linked widget sub-input still consumes its widget value`() {
        val api = convert(gen("""["m1", "ignored prompt", "1024x1024", 0.5, 2]""",
            """[{"name": "model.prompt", "type": "STRING", "widget": {"name": "model.prompt"}, "link": 10}]"""))
        val inputs = inputsOf(api, "2")
        assertEquals("1", inputs.getAsJsonArray("model.prompt")[0].asString)
        assertEquals("1024x1024", inputs.get("model.size").asString)
        assertEquals(2, inputs.get("n").asInt)
    }

    @Test
    fun `unknown option key is sent alone without crashing`() {
        val api = convert("""
            {"nodes": [ $src,
              {"id": 2, "type": "Resize", "inputs": [{"name": "input", "type": "IMAGE", "link": 10}],
               "widgets_values": ["scale longest side", "lanczos"]}
            ], "links": [[10, 1, 0, 2, 0, "IMAGE"]]}
        """)
        val inputs = inputsOf(api, "2")
        assertEquals("scale longest side", inputs.get("resize_type").asString)
        assertEquals("lanczos", inputs.get("scale_method").asString)
        assertTrue(inputs.keySet().none { it.startsWith("resize_type.") })
    }

    @Test
    fun `V3 combo sub-input label resolves to its option value`() {
        val api = convert(gen("""["m1", "a cat", "1536X1024", 0.5, 1]""", "[]"))
        assertEquals("1536x1024", inputsOf(api, "2").get("model.size").asString)
    }
}
