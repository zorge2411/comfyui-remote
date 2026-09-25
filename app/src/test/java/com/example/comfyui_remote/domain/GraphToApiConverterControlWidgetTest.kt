package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Phase 95: control_after_generate widgets and socket inputs take no values meant for other inputs. */
class GraphToApiConverterControlWidgetTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "Src": { "input": { "required": {} }, "output": ["INT", "IMAGE"] },
          "Gemini": { "input": { "required": {
            "prompt": ["STRING", {}],
            "seed": ["INT", {"control_after_generate": true}],
            "aspect_ratio": ["COMBO", {"options": ["auto", "16:9", "9:16"]}],
            "resolution": ["COMBO", {"options": ["1K", "2K", "4K"]}]
          } }, "output": ["IMAGE"] },
          "Legacy": { "input": { "required": {
            "seed": ["INT", {}],
            "steps": ["INT", {}],
            "mode": ["COMBO", {"options": ["a", "b"]}]
          } }, "output": [] },
          "NoControl": { "input": { "required": {
            "seed": ["INT", {"control_after_generate": false}],
            "label": ["STRING", {}]
          } }, "output": [] },
          "Picker": { "input": { "required": {
            "choice": [["x", "y", "z"], {"control_after_generate": true}],
            "note": ["STRING", {}]
          } }, "output": [] },
          "Masked": { "input": {
            "required": { "text": ["STRING", {}] },
            "optional": { "mask": ["IMAGE", {}], "suffix": ["STRING", {}] }
          }, "output": [] },
          "Api": { "input": { "required": {
            "model": ["COMFY_DYNAMICCOMBO_V3", {"options": [
              {"key": "m1", "inputs": {"required": {
                "seed": ["INT", {"control_after_generate": true}],
                "size": ["COMBO", {"options": ["1K", "2K"]}]}}}]}]
          } }, "output": [] }
        }
    """.trimIndent()).asJsonObject)

    private fun convert(nodes: String, links: String = "[]"): JsonObject =
        JsonParser.parseString(
            GraphToApiConverter.convert("""{"nodes": [$nodes], "links": $links}""", objectInfo).json
        ).asJsonObject

    private fun inputs(api: JsonObject, id: String) = api.getAsJsonObject(id).getAsJsonObject("inputs")

    @Test
    fun `seed control value is not taken by the next V3 combo`() {
        val api = convert("""{"id": 5, "type": "Gemini", "inputs": [], "widgets_values": ["a cat", 54321, "fixed", "9:16", "2K"]}""")
        val i = inputs(api, "5")
        assertEquals(54321L, i.get("seed").asLong)
        assertEquals("9:16", i.get("aspect_ratio").asString)
        assertEquals("2K", i.get("resolution").asString)
    }

    @Test
    fun `input named seed without the flag still skips its control value but steps does not`() {
        val i = inputs(convert("""{"id": 5, "type": "Legacy", "inputs": [], "widgets_values": [7, "randomize", 20, "b"]}"""), "5")
        assertEquals(7, i.get("seed").asInt)
        assertEquals(20, i.get("steps").asInt)
        assertEquals("b", i.get("mode").asString)
    }

    @Test
    fun `explicit control_after_generate false means no control slot`() {
        val i = inputs(convert("""{"id": 5, "type": "NoControl", "inputs": [], "widgets_values": [7, "fixed"]}"""), "5")
        assertEquals("fixed", i.get("label").asString)
    }

    @Test
    fun `combo with control skips the control value and the filter list`() {
        val i = inputs(convert("""{"id": 5, "type": "Picker", "inputs": [], "widgets_values": ["y", "increment-wrap", "", "hello"]}"""), "5")
        assertEquals("y", i.get("choice").asString)
        assertEquals("hello", i.get("note").asString)
    }

    @Test
    fun `older save without a control slot keeps the next value`() {
        val i = inputs(convert("""{"id": 5, "type": "Gemini", "inputs": [], "widgets_values": ["a cat", 42, "9:16", "4K"]}"""), "5")
        assertEquals("9:16", i.get("aspect_ratio").asString)
        assertEquals("4K", i.get("resolution").asString)
    }

    @Test
    fun `linked seed widget still skips its control value`() {
        val api = convert("""
            {"id": 1, "type": "Src", "inputs": [], "outputs": [{"name": "INT", "type": "INT"}], "widgets_values": []},
            {"id": 5, "type": "Gemini", "inputs": [{"name": "seed", "type": "INT", "widget": {"name": "seed"}, "link": 10}],
             "widgets_values": ["a cat", 0, "randomize", "16:9", "1K"]}
        """, """[[10, 1, 0, 5, 0, "INT"]]""")
        val i = inputs(api, "5")
        assertEquals("1", i.getAsJsonArray("seed")[0].asString)
        assertEquals("16:9", i.get("aspect_ratio").asString)
        assertEquals("1K", i.get("resolution").asString)
    }

    @Test
    fun `unlinked socket input does not take a widget value`() {
        val i = inputs(convert("""
            {"id": 5, "type": "Masked", "inputs": [{"name": "mask", "type": "IMAGE", "link": null}],
             "widgets_values": ["main text", "suffix text"]}
        """), "5")
        assertEquals("main text", i.get("text").asString)
        assertFalse(i.has("mask"))
        assertEquals("suffix text", i.get("suffix").asString)
    }

    @Test
    fun `seed inside a dynamic combo option skips its control value`() {
        val i = inputs(convert("""{"id": 5, "type": "Api", "inputs": [], "widgets_values": ["m1", 99, "randomize", "2K"]}"""), "5")
        assertEquals(99, i.get("model.seed").asInt)
        assertEquals("2K", i.get("model.size").asString)
    }
}
