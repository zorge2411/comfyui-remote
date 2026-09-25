package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 90: frontend-only nodes and link resolution as the ComfyUI frontend does it
 * (ExecutableNodeDTO.resolveOutput, graphToPrompt).
 */
class GraphToApiConverterVirtualNodeTest {

    private val objectInfoJson = """
        {
          "ModelSrc": { "input": { "required": {} } },
          "VisionSrc": { "input": { "required": {} } },
          "Encode": { "input": { "required": { "clip_vision": ["CLIP_VISION", {}] } } },
          "Cond": { "input": { "required": { "text": ["STRING", {}] },
                               "optional": { "clip_vision_output": ["CLIP_VISION_OUTPUT", {}] } } },
          "Mixer": { "input": { "required": { "clip": ["CLIP", {}], "model": ["MODEL", {}] } } },
          "Pair": { "input": { "required": { "a": ["MODEL", {}], "b": ["MODEL", {}] } } },
          "Sink": { "input": { "required": { "model": ["MODEL", {}] } } },
          "IntSrc": { "input": { "required": {} } },
          "Sampler": { "input": { "required": { "model": ["MODEL", {}], "steps": ["INT", {"default": 20}],
                                                "cfg": ["FLOAT", {"default": 8.0}] } } }
        }
    """.trimIndent()

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString(objectInfoJson).asJsonObject)

    private fun result(graph: String, info: ComfyObjectInfo = objectInfo) = GraphToApiConverter.convert(graph, info)

    private fun convert(graph: String, info: ComfyObjectInfo = objectInfo): JsonObject =
        JsonParser.parseString(result(graph, info).json).asJsonObject

    private fun inputsOf(api: JsonObject, id: Int): JsonObject = api.getAsJsonObject("$id").getAsJsonObject("inputs")

    private fun src(id: Int, type: String, outType: String) =
        """{"id": $id, "type": "$type", "mode": 0, "inputs": [], "outputs": [{"name": "out", "type": "$outType"}], "widgets_values": []}"""

    private fun sink(id: Int, link: Int) =
        """{"id": $id, "type": "Sink", "mode": 0, "inputs": [{"name": "model", "type": "MODEL", "link": $link}], "widgets_values": []}"""

    @Test
    fun `bypassed node with no input of the output type drops the link`() {
        // 3d_hunyuan3d_multiview_to_model: CLIPVisionEncode bypassed, CLIP_VISION must not reach CLIP_VISION_OUTPUT
        val api = convert("""
            {"nodes": [ ${src(1, "VisionSrc", "CLIP_VISION")},
              {"id": 2, "type": "Encode", "mode": 4, "inputs": [{"name": "clip_vision", "type": "CLIP_VISION", "link": 10}],
               "outputs": [{"name": "CLIP_VISION_OUTPUT", "type": "CLIP_VISION_OUTPUT"}], "widgets_values": []},
              {"id": 3, "type": "Cond", "mode": 0, "inputs": [{"name": "clip_vision_output", "type": "CLIP_VISION_OUTPUT", "link": 11}],
               "widgets_values": ["hello"]}
            ], "links": [ [10, 1, 0, 2, 0, "CLIP_VISION"], [11, 2, 0, 3, 0, "CLIP_VISION_OUTPUT"] ]}
        """.trimIndent())
        assertFalse(api.has("2"))
        assertFalse(inputsOf(api, 3).has("clip_vision_output"))
        assertEquals("hello", inputsOf(api, 3).get("text").asString)
    }

    @Test
    fun `bypass prefers the same-index input when its type fits`() {
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")}, ${src(4, "ModelSrc", "MODEL")},
              {"id": 2, "type": "Pair", "mode": 4,
               "inputs": [{"name": "a", "type": "MODEL", "link": 10}, {"name": "b", "type": "MODEL", "link": 12}],
               "outputs": [{"name": "a", "type": "MODEL"}, {"name": "b", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "MODEL"], [12, 4, 0, 2, 1, "MODEL"], [11, 2, 1, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertEquals("4", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
    }

    @Test
    fun `bypass with a wildcard target type uses the same-index input`() {
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")}, ${src(4, "VisionSrc", "CLIP")},
              {"id": 2, "type": "Mixer", "mode": 4,
               "inputs": [{"name": "clip", "type": "CLIP", "link": 12}, {"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "clip", "type": "CLIP"}, {"name": "model", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [12, 4, 0, 2, 0, "CLIP"], [10, 1, 0, 2, 1, "MODEL"], [11, 2, 1, 3, 0, "*"] ]}
        """.trimIndent())
        assertEquals("1", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
    }

    @Test
    fun `bypass that picks an unlinked matching input drops the link`() {
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")},
              {"id": 2, "type": "Pair", "mode": 4,
               "inputs": [{"name": "a", "type": "MODEL", "link": null}, {"name": "b", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "a", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 1, "MODEL"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertFalse(inputsOf(api, 3).has("model"))
    }

    @Test
    fun `reroute passes through and is not reported missing`() {
        val result = result("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")},
              {"id": 2, "type": "Reroute", "mode": 0, "inputs": [{"name": "", "type": "*", "link": 10}],
               "outputs": [{"name": "", "type": "MODEL", "links": [11]}], "properties": {"showOutputText": false}},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "*"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        val api = JsonParser.parseString(result.json).asJsonObject
        assertFalse(api.has("2"))
        assertEquals("1", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
        assertTrue(result.missingNodes.isEmpty())
    }

    @Test
    fun `notes are not sent and not reported missing`() {
        val result = result("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")}, ${sink(3, 11)},
              {"id": 5, "type": "Note", "mode": 0, "inputs": [], "outputs": [], "widgets_values": ["read me"]},
              {"id": 6, "type": "MarkdownNote", "mode": 0, "inputs": [], "outputs": [], "widgets_values": ["# hi"]}
            ], "links": [ [11, 1, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        val api = JsonParser.parseString(result.json).asJsonObject
        assertFalse(api.has("5"))
        assertFalse(api.has("6"))
        assertTrue(result.missingNodes.isEmpty())
    }

    @Test
    fun `widget input linked from a muted node sends its saved widget value`() {
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")},
              {"id": 4, "type": "IntSrc", "mode": 2, "inputs": [], "outputs": [{"name": "INT", "type": "INT"}], "widgets_values": []},
              {"id": 3, "type": "Sampler", "mode": 0,
               "inputs": [{"name": "model", "type": "MODEL", "link": 11},
                          {"name": "steps", "type": "INT", "widget": {"name": "steps"}, "link": 12}],
               "widgets_values": [30, 6.5]}
            ], "links": [ [11, 1, 0, 3, 0, "MODEL"], [12, 4, 0, 3, 1, "INT"] ]}
        """.trimIndent())
        val inputs = inputsOf(api, 3)
        assertEquals(30, inputs.get("steps").asInt)
        assertEquals(6.5, inputs.get("cfg").asDouble, 0.0)
    }

    @Test
    fun `socket input linked from a muted node is absent`() {
        val api = convert("""
            {"nodes": [ {"id": 1, "type": "ModelSrc", "mode": 2, "inputs": [], "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              {"id": 3, "type": "Sampler", "mode": 0, "inputs": [{"name": "model", "type": "MODEL", "link": 11}],
               "widgets_values": [30, 6.5]}
            ], "links": [ [11, 1, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertFalse(inputsOf(api, 3).has("model"))
        assertEquals(30, inputsOf(api, 3).get("steps").asInt)
    }

    @Test
    fun `a virtual type the server defines is treated as a real node`() {
        val json = JsonParser.parseString(objectInfoJson).asJsonObject
        json.add("Reroute", JsonParser.parseString("""{ "input": { "required": { "value": ["*", {}] } } }"""))
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")},
              {"id": 2, "type": "Reroute", "mode": 0, "inputs": [{"name": "value", "type": "*", "link": 10}],
               "outputs": [{"name": "", "type": "MODEL", "links": [11]}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "*"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent(), ComfyObjectInfo(json))
        assertTrue(api.has("2"))
        assertEquals("2", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
    }
}
