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
          "Compare": { "input": {
            "required": { "compare_view": ["IMAGECOMPARE", {"socketless": true}] },
            "optional": { "image_a": ["IMAGE", {}] } }, "output": [] },
          "Scale": { "input": { "required": {
            "image": ["IMAGE", {}],
            "upscale_method": [["nearest-exact", "area"], {}],
            "megapixels": ["FLOAT", {"default": 1.0}],
            "resolution_steps": ["INT", {"default": 1}]
          } }, "output": [] },
          "Defaults": { "input": { "required": {
            "flag": ["BOOLEAN", {}],
            "pick": ["COMBO", {"options": ["first", "second"]}],
            "picked": [["p", "q"], {"default": "q"}],
            "text": ["STRING", {}]
          } }, "output": [] },
          "Named": { "input": { "required": {
            "prompt": ["STRING", {}],
            "added": ["INT", {"default": 5}],
            "steps": ["INT", {}]
          } }, "output": [] },
          "Forced": { "input": { "required": {
            "value": ["INT", {"forceInput": true}],
            "label": ["STRING", {}],
            "rate": ["FLOAT,INT", {"widgetType": "FLOAT"}]
          } }, "output": [] },
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

    // --- Plan 95.2: named values, defaults, display-only and socket-only widget kinds ---

    @Test
    fun `IMAGECOMPARE is sent as a wrapped empty pair and takes no saved value`() {
        val i = inputs(convert("""{"id": 5, "type": "Compare", "inputs": [], "widgets_values": []}"""), "5")
        // List widget values are wrapped like the frontend does, so the server doesn't read them as links
        assertEquals(listOf("", ""), i.getAsJsonObject("compare_view").getAsJsonArray("__value__").map { it.asString })
    }

    @Test
    fun `widget added after the workflow was saved gets its default`() {
        val i = inputs(convert("""
            {"id": 5, "type": "Scale", "inputs": [{"name": "image", "type": "IMAGE", "link": null}], "widgets_values": ["area", 1.5]}
        """), "5")
        assertEquals("area", i.get("upscale_method").asString)
        assertEquals(1.5, i.get("megapixels").asDouble, 0.0)
        assertEquals(1, i.get("resolution_steps").asInt)
    }

    @Test
    fun `defaults follow the frontend for combos booleans and strings`() {
        val i = inputs(convert("""{"id": 5, "type": "Defaults", "inputs": [], "widgets_values": []}"""), "5")
        assertFalse(i.get("flag").asBoolean)
        assertEquals("first", i.get("pick").asString)
        assertEquals("q", i.get("picked").asString)
        assertEquals("", i.get("text").asString)
    }

    @Test
    fun `named values win when the positional array no longer matches`() {
        // Saved before "added" existed: positionally 20 would land on "added"
        val i = inputs(convert("""
            {"id": 5, "type": "Named", "inputs": [], "widgets_values": ["hello", 20],
             "widgets_values_named": {"prompt": "hello", "steps": 20}}
        """), "5")
        assertEquals("hello", i.get("prompt").asString)
        assertEquals(20, i.get("steps").asInt)
        assertEquals(5, i.get("added").asInt)
    }

    @Test
    fun `named values cover dotted dynamic sub-widgets`() {
        val i = inputs(convert("""
            {"id": 5, "type": "Api", "inputs": [], "widgets_values": ["m1", 1, "randomize", "1K"],
             "widgets_values_named": {"model": "m1", "model.seed": 77, "model.size": "2K"}}
        """), "5")
        assertEquals(77, i.get("model.seed").asInt)
        assertEquals("2K", i.get("model.size").asString)
    }

    @Test
    fun `linked input ignores its named value`() {
        val api = convert("""
            {"id": 1, "type": "Src", "inputs": [], "outputs": [{"name": "INT", "type": "INT"}], "widgets_values": []},
            {"id": 5, "type": "Gemini", "inputs": [{"name": "seed", "type": "INT", "widget": {"name": "seed"}, "link": 10}],
             "widgets_values": ["a cat", 0, "fixed", "auto", "1K"],
             "widgets_values_named": {"prompt": "a cat", "seed": 0, "aspect_ratio": "auto", "resolution": "1K"}}
        """, """[[10, 1, 0, 5, 0, "INT"]]""")
        assertEquals("1", inputs(api, "5").getAsJsonArray("seed")[0].asString)
    }

    @Test
    fun `forceInput is a socket and widgetType picks the widget kind`() {
        val i = inputs(convert("""
            {"id": 5, "type": "Forced", "inputs": [{"name": "value", "type": "INT", "link": null}],
             "widgets_values": ["name", 24.0]}
        """), "5")
        assertFalse(i.has("value"))
        assertEquals("name", i.get("label").asString)
        assertEquals(24.0, i.get("rate").asDouble, 0.0)
    }
}
