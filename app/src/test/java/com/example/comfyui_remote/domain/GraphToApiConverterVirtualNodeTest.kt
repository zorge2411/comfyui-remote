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
                                                "cfg": ["FLOAT", {"default": 8.0}] } } },
          "Seeded": { "input": { "required": { "seed": ["INT", {}], "steps": ["INT", {}] } } },
          "Picker": { "input": { "required": { "mode": [["Alpha", "Beta"], {}] } } },
          "TextEnc": { "input": { "required": { "text": ["STRING", {}] } } },
          "Latent": { "input": { "required": { "width": ["INT", {}], "height": ["INT", {}] } } }
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

    // --- PrimitiveNode (widgetInputs.ts applyToGraph) ---

    private fun primitive(id: Int, value: String, type: String, widget: String, links: String, mode: Int = 0) =
        """{"id": $id, "type": "PrimitiveNode", "mode": $mode, "inputs": [],
            "outputs": [{"name": "$type", "type": "$type", "widget": {"name": "$widget"}, "links": [$links]}],
            "widgets_values": [$value, "fixed"]}"""

    private fun sampler(id: Int, stepsLink: Int, values: String) =
        """{"id": $id, "type": "Sampler", "mode": 0,
            "inputs": [{"name": "model", "type": "MODEL", "link": null},
                       {"name": "steps", "type": "INT", "widget": {"name": "steps"}, "link": $stepsLink}],
            "widgets_values": [$values]}"""

    @Test
    fun `primitive value replaces the target's saved value and the primitive is not sent`() {
        val result = result("""
            {"nodes": [ ${primitive(7, "42", "INT", "steps", "20")}, ${sampler(3, 20, "30, 6.5")} ],
             "links": [ [20, 7, 0, 3, 1, "INT"] ]}
        """.trimIndent())
        val api = JsonParser.parseString(result.json).asJsonObject
        assertFalse(api.has("7"))
        assertEquals(42, inputsOf(api, 3).get("steps").asInt)
        assertEquals(6.5, inputsOf(api, 3).get("cfg").asDouble, 0.0)
        assertTrue(result.missingNodes.isEmpty())
    }

    @Test
    fun `one primitive sets every target`() {
        val api = convert("""
            {"nodes": [ ${primitive(7, "42", "INT", "steps", "20, 21")},
              ${sampler(3, 20, "30, 6.5")}, ${sampler(4, 21, "25, 5.0")} ],
             "links": [ [20, 7, 0, 3, 1, "INT"], [21, 7, 0, 4, 1, "INT"] ]}
        """.trimIndent())
        assertEquals(42, inputsOf(api, 3).get("steps").asInt)
        assertEquals(42, inputsOf(api, 4).get("steps").asInt)
    }

    @Test
    fun `seed primitive keeps later widgets aligned past the target's control value`() {
        val api = convert("""
            {"nodes": [ ${primitive(7, "31", "INT", "seed", "20")},
              {"id": 3, "type": "Seeded", "mode": 0,
               "inputs": [{"name": "seed", "type": "INT", "widget": {"name": "seed"}, "link": 20}],
               "widgets_values": [31, "fixed", 8]} ],
             "links": [ [20, 7, 0, 3, 0, "INT"] ]}
        """.trimIndent())
        assertEquals(31, inputsOf(api, 3).get("seed").asInt)
        assertEquals(8, inputsOf(api, 3).get("steps").asInt)
    }

    @Test
    fun `primitive combo value is resolved against the options`() {
        val api = convert("""
            {"nodes": [ ${primitive(7, "\"beta\"", "COMBO", "mode", "20")},
              {"id": 3, "type": "Picker", "mode": 0,
               "inputs": [{"name": "mode", "type": "COMBO", "widget": {"name": "mode"}, "link": 20}],
               "widgets_values": ["Alpha"]} ],
             "links": [ [20, 7, 0, 3, 0, "COMBO"] ]}
        """.trimIndent())
        assertEquals("Beta", inputsOf(api, 3).get("mode").asString)
    }

    @Test
    fun `primitive through a reroute leaves the target's saved value`() {
        val api = convert("""
            {"nodes": [ ${primitive(7, "42", "INT", "steps", "20")},
              {"id": 2, "type": "Reroute", "mode": 0, "inputs": [{"name": "", "type": "*", "link": 20}],
               "outputs": [{"name": "", "type": "INT", "links": [21]}]},
              ${sampler(3, 21, "30, 6.5")} ],
             "links": [ [20, 7, 0, 2, 0, "*"], [21, 2, 0, 3, 1, "INT"] ]}
        """.trimIndent())
        assertFalse(api.has("2"))
        assertEquals(30, inputsOf(api, 3).get("steps").asInt)
    }

    @Test
    fun `muted primitive still applies its value`() {
        val api = convert("""
            {"nodes": [ ${primitive(7, "42", "INT", "steps", "20", mode = 2)}, ${sampler(3, 20, "30, 6.5")} ],
             "links": [ [20, 7, 0, 3, 1, "INT"] ]}
        """.trimIndent())
        assertFalse(api.has("7"))
        assertEquals(42, inputsOf(api, 3).get("steps").asInt)
    }

    @Test
    fun `primitive feeding a subgraph instance input reaches the interior node`() {
        val subgraph = """
            {"id": "SG", "name": "SG",
             "inputs": [{"id": "i0", "name": "width", "type": "INT", "linkIds": [902]}],
             "outputs": [],
             "nodes": [
               {"id": 11, "type": "Latent", "inputs": [
                  {"name": "width", "type": "INT", "widget": {"name": "width"}, "link": 902},
                  {"name": "height", "type": "INT", "widget": {"name": "height"}, "link": null}], "widgets_values": [512, 768]}],
             "links": [{"id": 902, "origin_id": -10, "origin_slot": 0, "target_id": 11, "target_slot": 0, "type": "INT"}]}
        """
        val api = convert("""
            {"definitions": {"subgraphs": [$subgraph]},
             "nodes": [ ${primitive(7, "1024", "INT", "width", "50")},
               {"id": 2, "type": "SG", "inputs": [{"name": "width", "type": "INT", "widget": {"name": "width"}, "link": 50}],
                "widgets_values": []} ],
             "links": [[50, 7, 0, 2, 0, "INT"]]}
        """.trimIndent())
        val latent = api.entrySet().single { it.value.asJsonObject.get("class_type").asString == "Latent" }
            .value.asJsonObject.getAsJsonObject("inputs")
        assertEquals(1024, latent.get("width").asInt)
        assertEquals(768, latent.get("height").asInt)
        assertFalse(api.entrySet().any { it.value.asJsonObject.get("class_type").asString == "PrimitiveNode" })
    }

    // --- KJNodes SetNode / GetNode (setgetnodes.js) ---

    private fun setNode(id: Int, name: String, link: Int?, type: String = "MODEL") =
        """{"id": $id, "type": "SetNode", "mode": 0, "inputs": [{"name": "$type", "type": "$type", "link": $link}],
            "outputs": [{"name": "*", "type": "*", "links": []}], "widgets_values": ["$name"]}"""

    private fun getNode(id: Int, name: String, links: String, type: String = "MODEL") =
        """{"id": $id, "type": "GetNode", "mode": 0, "inputs": [],
            "outputs": [{"name": "$type", "type": "$type", "links": [$links]}], "widgets_values": ["$name"]}"""

    @Test
    fun `get node resolves to its set node's source`() {
        val result = result("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")}, ${setNode(5, "model", 10)}, ${getNode(6, "model", "11")}, ${sink(3, 11)} ],
             "links": [ [10, 1, 0, 5, 0, "MODEL"], [11, 6, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        val api = JsonParser.parseString(result.json).asJsonObject
        assertFalse(api.has("5"))
        assertFalse(api.has("6"))
        assertEquals("1", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
        assertTrue(result.missingNodes.isEmpty())
    }

    @Test
    fun `set node fed through a reroute and a bypassed node resolves upstream`() {
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")},
              {"id": 2, "type": "Sink", "mode": 4, "inputs": [{"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              {"id": 4, "type": "Reroute", "mode": 0, "inputs": [{"name": "", "type": "*", "link": 12}],
               "outputs": [{"name": "", "type": "MODEL", "links": [13]}]},
              ${setNode(5, "model", 13)}, ${getNode(6, "model", "11")}, ${sink(3, 11)} ],
             "links": [ [10, 1, 0, 2, 0, "MODEL"], [12, 2, 0, 4, 0, "MODEL"], [13, 4, 0, 5, 0, "MODEL"], [11, 6, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertEquals("1", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
    }

    @Test
    fun `get node without a set node drops the link and a widget input keeps its value`() {
        val api = convert("""
            {"nodes": [ ${getNode(6, "missing", "11")}, ${getNode(7, "missing", "12", type = "INT")},
              ${sink(3, 11)}, ${sampler(4, 12, "30, 6.5")} ],
             "links": [ [11, 6, 0, 3, 0, "MODEL"], [12, 7, 0, 4, 1, "INT"] ]}
        """.trimIndent())
        assertFalse(inputsOf(api, 3).has("model"))
        assertEquals(30, inputsOf(api, 4).get("steps").asInt)
    }

    @Test
    fun `duplicate set node names use the first in node order`() {
        val api = convert("""
            {"nodes": [ ${src(1, "ModelSrc", "MODEL")}, ${src(2, "ModelSrc", "MODEL")},
              ${setNode(9, "model", 10)}, ${setNode(5, "model", 12)}, ${getNode(6, "model", "11")}, ${sink(3, 11)} ],
             "links": [ [10, 1, 0, 9, 0, "MODEL"], [12, 2, 0, 5, 0, "MODEL"], [11, 6, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertEquals("1", inputsOf(api, 3).getAsJsonArray("model")[0].asString)
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
