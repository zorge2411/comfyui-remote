package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterAutogrowTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "Src": { "input": { "required": {} } },
          "MathNode": { "input": { "required": {
            "expression": ["STRING", {"default": "a + b"}],
            "values": ["COMFY_AUTOGROW_V3", {"template": {"input": {"required": {"value": ["FLOAT,INT,BOOLEAN", {}]}}, "names": ["a","b"], "min": 1}}]
          } } },
          "FormatNode": { "input": { "required": {
            "values": ["COMFY_AUTOGROW_V3", {"template": {"input": {"required": {"value": ["*", {}]}}, "names": ["a","b"], "min": 0}}],
            "f_string": ["STRING", {"default": "{a}"}]
          } } },
          "BatchNode": { "input": { "required": {
            "images": ["COMFY_AUTOGROW_V3", {"template": {"input": {"required": {"image": ["IMAGE", {}]}}, "prefix": "image", "min": 1, "max": 50}}]
          } } }
        }
    """.trimIndent()).asJsonObject)

    private fun convert(nodesAndLinks: String) =
        JsonParser.parseString(GraphToApiConverter.convert(nodesAndLinks, objectInfo).json).asJsonObject

    private fun src(id: Int) = """{"id": $id, "type": "Src", "inputs": [], "widgets_values": []}"""

    @Test
    fun `convert copies dotted autogrow slot links`() {
        val api = convert("""
            {"nodes": [ ${src(1)}, ${src(2)},
              {"id": 3, "type": "MathNode", "widgets_values": ["a + b"],
               "inputs": [ {"name": "values.a", "link": 10}, {"name": "values.b", "link": 11} ]}
            ], "links": [ [10, 1, 0, 3, 0, "FLOAT"], [11, 2, 0, 3, 1, "FLOAT"] ]}
        """.trimIndent())
        val inputs = api.getAsJsonObject("3").getAsJsonObject("inputs")
        assertEquals("1", inputs.getAsJsonArray("values.a")[0].asString)
        assertEquals("2", inputs.getAsJsonArray("values.b")[0].asString)
        assertEquals("a + b", inputs.get("expression").asString)
    }

    @Test
    fun `autogrow key does not consume a widget value`() {
        val api = convert("""
            {"nodes": [ ${src(1)},
              {"id": 3, "type": "FormatNode", "widgets_values": ["{a}"],
               "inputs": [ {"name": "values.a", "link": 10} ]}
            ], "links": [ [10, 1, 0, 3, 0, "*"] ]}
        """.trimIndent())
        val inputs = api.getAsJsonObject("3").getAsJsonObject("inputs")
        assertEquals("{a}", inputs.get("f_string").asString)
        assertFalse(inputs.has("values"))
        assertTrue(inputs.has("values.a"))
    }

    @Test
    fun `prefix template variant is copied`() {
        val api = convert("""
            {"nodes": [ ${src(1)}, ${src(2)},
              {"id": 3, "type": "BatchNode", "widgets_values": [],
               "inputs": [ {"name": "images.image0", "link": 10}, {"name": "images.image1", "link": 11} ]}
            ], "links": [ [10, 1, 0, 3, 0, "IMAGE"], [11, 2, 0, 3, 1, "IMAGE"] ]}
        """.trimIndent())
        val inputs = api.getAsJsonObject("3").getAsJsonObject("inputs")
        assertTrue(inputs.has("images.image0"))
        assertTrue(inputs.has("images.image1"))
    }

    @Test
    fun `unlinked dotted slot is omitted`() {
        val api = convert("""
            {"nodes": [ ${src(1)},
              {"id": 3, "type": "MathNode", "widgets_values": ["a + b"],
               "inputs": [ {"name": "values.a", "link": 10}, {"name": "values.b", "link": null} ]}
            ], "links": [ [10, 1, 0, 3, 0, "FLOAT"] ]}
        """.trimIndent())
        val inputs = api.getAsJsonObject("3").getAsJsonObject("inputs")
        assertTrue(inputs.has("values.a"))
        assertFalse(inputs.has("values.b"))
    }
}
