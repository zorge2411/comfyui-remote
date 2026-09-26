package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 90: bypass follows the frontend's ExecutableNodeDTO._getBypassSlotIndex (with the consuming input's
 * type), and muted/bypassed subgraph instances are not expanded.
 */
class GraphToApiConverterBypassRuleTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "Img": { "input": { "required": {} }, "output": ["IMAGE"] },
          "Vision": { "input": { "required": {} }, "output": ["CLIP_VISION"] },
          "Encode": { "input": { "required": { "clip_vision": ["CLIP_VISION", {}], "image": ["IMAGE", {}] } },
                      "output": ["CLIP_VISION_OUTPUT"] },
          "Cond": { "input": { "required": {}, "optional": { "front": ["CLIP_VISION_OUTPUT", {}] } }, "output": [] },
          "Filter": { "input": { "required": { "a": ["IMAGE", {}], "b": ["IMAGE", {}] } }, "output": ["IMAGE"] },
          "Swap": { "input": { "required": { "mask": ["MASK", {}], "image": ["IMAGE", {}] } }, "output": ["IMAGE"] },
          "Any": { "input": { "required": { "x": ["*", {}] } }, "output": [] },
          "Save": { "input": { "required": { "images": ["IMAGE", {}] } }, "output": [] },
          "Invert": { "input": { "required": { "image": ["IMAGE", {}] } }, "output": ["IMAGE"] }
        }
    """.trimIndent()).asJsonObject)

    private fun convert(graph: String): JsonObject =
        JsonParser.parseString(GraphToApiConverter.convert(graph.trimIndent(), objectInfo).json).asJsonObject

    private fun src(id: Int, type: String, out: String) =
        """{"id": $id, "type": "$type", "mode": 0, "inputs": [], "outputs": [{"name": "o", "type": "$out"}], "widgets_values": []}"""

    private fun sink(id: Int, type: String, input: String, inType: String, link: Int) =
        """{"id": $id, "type": "$type", "mode": 0, "inputs": [{"name": "$input", "type": "$inType", "link": $link}], "widgets_values": []}"""

    private fun sourceOf(api: JsonObject, node: String, input: String): String? =
        api.getAsJsonObject(node)?.getAsJsonObject("inputs")?.getAsJsonArray(input)?.get(0)?.asString

    @Test
    fun `same-slot input is used when it fits both the output and target types`() {
        val api = convert("""
            {"nodes": [ ${src(1, "Img", "IMAGE")}, ${src(2, "Img", "IMAGE")},
              {"id": 3, "type": "Filter", "mode": 4,
               "inputs": [{"name": "a", "type": "IMAGE", "link": 10}, {"name": "b", "type": "IMAGE", "link": 11}],
               "outputs": [{"name": "IMAGE", "type": "IMAGE"}]},
              ${sink(4, "Save", "images", "IMAGE", 12)}
            ], "links": [[10, 1, 0, 3, 0, "IMAGE"], [11, 2, 0, 3, 1, "IMAGE"], [12, 3, 0, 4, 0, "IMAGE"]]}
        """)
        assertEquals("1", sourceOf(api, "4", "images"))
    }

    @Test
    fun `exact target type match wins when the same-slot input does not fit`() {
        val api = convert("""
            {"nodes": [ ${src(1, "Img", "IMAGE")}, ${src(2, "Img", "MASK")},
              {"id": 3, "type": "Swap", "mode": 4,
               "inputs": [{"name": "mask", "type": "MASK", "link": 11}, {"name": "image", "type": "IMAGE", "link": 10}],
               "outputs": [{"name": "IMAGE", "type": "IMAGE"}]},
              ${sink(4, "Save", "images", "IMAGE", 12)}
            ], "links": [[10, 1, 0, 3, 1, "IMAGE"], [11, 2, 0, 3, 0, "MASK"], [12, 3, 0, 4, 0, "IMAGE"]]}
        """)
        assertEquals("1", sourceOf(api, "4", "images"))
    }

    @Test
    fun `no compatible input drops the link`() {
        // The 3d_hunyuan3d_multiview shape: a bypassed CLIPVisionEncode has no CLIP_VISION_OUTPUT input
        val api = convert("""
            {"nodes": [ ${src(1, "Vision", "CLIP_VISION")}, ${src(2, "Img", "IMAGE")},
              {"id": 3, "type": "Encode", "mode": 4,
               "inputs": [{"name": "clip_vision", "type": "CLIP_VISION", "link": 10}, {"name": "image", "type": "IMAGE", "link": 11}],
               "outputs": [{"name": "CLIP_VISION_OUTPUT", "type": "CLIP_VISION_OUTPUT"}]},
              ${sink(4, "Cond", "front", "CLIP_VISION_OUTPUT", 12)}
            ], "links": [[10, 1, 0, 3, 0, "CLIP_VISION"], [11, 2, 0, 3, 1, "IMAGE"], [12, 3, 0, 4, 0, "CLIP_VISION_OUTPUT"]]}
        """)
        assertNull(sourceOf(api, "4", "front"))
    }

    @Test
    fun `wildcard target takes the same slot`() {
        val api = convert("""
            {"nodes": [ ${src(1, "Vision", "CLIP_VISION")}, ${src(2, "Img", "IMAGE")},
              {"id": 3, "type": "Encode", "mode": 4,
               "inputs": [{"name": "clip_vision", "type": "CLIP_VISION", "link": 10}, {"name": "image", "type": "IMAGE", "link": 11}],
               "outputs": [{"name": "CLIP_VISION_OUTPUT", "type": "CLIP_VISION_OUTPUT"}]},
              ${sink(4, "Any", "x", "*", 12)}
            ], "links": [[10, 1, 0, 3, 0, "CLIP_VISION"], [11, 2, 0, 3, 1, "IMAGE"], [12, 3, 0, 4, 0, "*"]]}
        """)
        assertEquals("1", sourceOf(api, "4", "x"))
    }

    @Test
    fun `chained bypass keeps the final target type`() {
        val api = convert("""
            {"nodes": [ ${src(1, "Img", "IMAGE")}, ${src(2, "Img", "MASK")},
              {"id": 3, "type": "Swap", "mode": 4,
               "inputs": [{"name": "mask", "type": "MASK", "link": 11}, {"name": "image", "type": "IMAGE", "link": 10}],
               "outputs": [{"name": "IMAGE", "type": "IMAGE"}]},
              {"id": 5, "type": "Invert", "mode": 4,
               "inputs": [{"name": "image", "type": "IMAGE", "link": 12}], "outputs": [{"name": "IMAGE", "type": "IMAGE"}]},
              ${sink(4, "Save", "images", "IMAGE", 13)}
            ], "links": [[10, 1, 0, 3, 1, "IMAGE"], [11, 2, 0, 3, 0, "MASK"], [12, 3, 0, 5, 0, "IMAGE"], [13, 5, 0, 4, 0, "IMAGE"]]}
        """)
        assertEquals("1", sourceOf(api, "4", "images"))
    }

    /** Subgraph "G": input image -> Invert -> output image. */
    private val invertGroup = """
        {"id": "G", "name": "G",
         "inputs": [{"id": "i", "name": "image", "type": "IMAGE", "linkIds": [901]}],
         "outputs": [{"id": "o", "name": "IMAGE", "type": "IMAGE", "linkIds": [902]}],
         "nodes": [{"id": 50, "type": "Invert", "mode": 0, "inputs": [{"name": "image", "type": "IMAGE", "link": 901}],
                    "outputs": [{"name": "IMAGE", "type": "IMAGE"}]}],
         "links": [
           {"id": 901, "origin_id": -10, "origin_slot": 0, "target_id": 50, "target_slot": 0, "type": "IMAGE"},
           {"id": 902, "origin_id": 50, "origin_slot": 0, "target_id": -20, "target_slot": 0, "type": "IMAGE"}]}
    """

    private fun groupGraph(mode: Int) = """
        {"definitions": {"subgraphs": [$invertGroup]},
         "nodes": [ ${src(1, "Img", "IMAGE")},
           {"id": 2, "type": "G", "mode": $mode, "inputs": [{"name": "image", "type": "IMAGE", "link": 10}],
            "outputs": [{"name": "IMAGE", "type": "IMAGE"}]},
           ${sink(3, "Save", "images", "IMAGE", 11)}
         ], "links": [[10, 1, 0, 2, 0, "IMAGE"], [11, 2, 0, 3, 0, "IMAGE"]]}
    """

    private fun hasInvert(api: JsonObject) = api.entrySet().any { it.value.asJsonObject.get("class_type").asString == "Invert" }

    @Test
    fun `bypassed subgraph instance passes its input through and runs nothing inside`() {
        val api = convert(groupGraph(4))
        assertEquals("1", sourceOf(api, "3", "images"))
        assertFalse(hasInvert(api))
    }

    @Test
    fun `muted subgraph instance runs nothing and its consumers lose the link`() {
        val api = convert(groupGraph(2))
        assertNull(sourceOf(api, "3", "images"))
        assertFalse(hasInvert(api))
        assertTrue(GraphToApiConverter.convert(groupGraph(2).trimIndent(), objectInfo).missingNodes.isEmpty())
    }

    @Test
    fun `bypassed instance nested in an expanded subgraph is also passed through`() {
        val outer = """
            {"id": "OUTER", "name": "OUTER",
             "inputs": [{"id": "i", "name": "image", "type": "IMAGE", "linkIds": [911]}],
             "outputs": [{"id": "o", "name": "IMAGE", "type": "IMAGE", "linkIds": [912]}],
             "nodes": [{"id": 60, "type": "G", "mode": 4, "inputs": [{"name": "image", "type": "IMAGE", "link": 911}],
                        "outputs": [{"name": "IMAGE", "type": "IMAGE"}]}],
             "links": [
               {"id": 911, "origin_id": -10, "origin_slot": 0, "target_id": 60, "target_slot": 0, "type": "IMAGE"},
               {"id": 912, "origin_id": 60, "origin_slot": 0, "target_id": -20, "target_slot": 0, "type": "IMAGE"}]}
        """
        val api = convert("""
            {"definitions": {"subgraphs": [$invertGroup, $outer]},
             "nodes": [ ${src(1, "Img", "IMAGE")},
               {"id": 2, "type": "OUTER", "mode": 0, "inputs": [{"name": "image", "type": "IMAGE", "link": 10}],
                "outputs": [{"name": "IMAGE", "type": "IMAGE"}]},
               ${sink(3, "Save", "images", "IMAGE", 11)}
             ], "links": [[10, 1, 0, 2, 0, "IMAGE"], [11, 2, 0, 3, 0, "IMAGE"]]}
        """)
        assertEquals("1", sourceOf(api, "3", "images"))
        assertFalse(hasInvert(api))
    }
}
