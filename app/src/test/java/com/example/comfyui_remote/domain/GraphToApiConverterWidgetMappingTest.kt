package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterWidgetMappingTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "Src": { "input": { "required": {} } },
          "Video": { "input": {
            "required": { "images": ["IMAGE", {}], "fps": ["FLOAT", {}] },
            "optional": { "bit_depth": ["INT", {}] }
          } },
          "Easy": { "input": { "required": {
            "mode": [["image", "reference"], {"default": "image"}],
            "keyframe_role": [["first", "last"], {"default": "first"}],
            "ref_image_size": [["match", "1k", "1.5k"], {"default": "1k"}],
            "mention": [["filename", "index"], {"default": "index"}],
            "guide": [["none", "brand_promo"], {}]
          } } }
        }
    """.trimIndent()).asJsonObject)

    private fun inputsOf(graph: String, nodeId: String): JsonObject =
        JsonParser.parseString(GraphToApiConverter.convert(graph, objectInfo).json)
            .asJsonObject.getAsJsonObject(nodeId).getAsJsonObject("inputs")

    private fun src(id: Int) = """{"id": $id, "type": "Src", "inputs": [], "widgets_values": []}"""

    @Test
    fun `linked widget input still consumes its widgets_values slot`() {
        val inputs = inputsOf("""
            {"nodes": [ ${src(1)}, ${src(2)},
              {"id": 3, "type": "Video", "widgets_values": [24, 8],
               "inputs": [ {"name": "images", "link": 10},
                           {"name": "fps", "link": 11, "widget": {"name": "fps"}},
                           {"name": "bit_depth", "link": null, "widget": {"name": "bit_depth"}} ]}
            ], "links": [ [10, 1, 0, 3, 0, "IMAGE"], [11, 2, 0, 3, 1, "FLOAT"] ]}
        """.trimIndent(), "3")
        assertTrue(inputs.get("fps").isJsonArray)
        assertEquals(8, inputs.get("bit_depth").asInt)
    }

    @Test
    fun `linked slot without widget property does not consume a widget value`() {
        val inputs = inputsOf("""
            {"nodes": [ ${src(1)}, ${src(2)},
              {"id": 3, "type": "Video", "widgets_values": [24, 8],
               "inputs": [ {"name": "images", "link": 10},
                           {"name": "fps", "link": null, "widget": {"name": "fps"}},
                           {"name": "bit_depth", "link": null, "widget": {"name": "bit_depth"}} ]}
            ], "links": [ [10, 1, 0, 3, 0, "IMAGE"] ]}
        """.trimIndent(), "3")
        assertEquals(24, inputs.get("fps").asInt)
        assertEquals(8, inputs.get("bit_depth").asInt)
    }

    private fun easy(vararg widgets: String): JsonObject {
        val w = widgets.joinToString(",") { "\"$it\"" }
        return inputsOf("""
            {"nodes": [ {"id": 3, "type": "Easy", "widgets_values": [$w], "inputs": []} ], "links": []}
        """.trimIndent(), "3")
    }

    @Test
    fun `combo label resolves by substring`() {
        val inputs = easy("image", "First frame priority", "1K area (~1MP)", "By filename", "none")
        assertEquals("first", inputs.get("keyframe_role").asString)
        assertEquals("1k", inputs.get("ref_image_size").asString)
        assertEquals("filename", inputs.get("mention").asString)
    }

    @Test
    fun `combo label falls back to default`() {
        val inputs = easy("I2V or First/Last Frame", "first", "1k", "filename", "none")
        assertEquals("image", inputs.get("mode").asString)
    }

    @Test
    fun `valid combo value is untouched and unresolvable label without default is kept`() {
        val inputs = easy("reference", "last", "1.5k", "index", "Some Unknown Label")
        assertEquals("reference", inputs.get("mode").asString)
        assertEquals("1.5k", inputs.get("ref_image_size").asString)
        assertEquals("Some Unknown Label", inputs.get("guide").asString)
    }
}
