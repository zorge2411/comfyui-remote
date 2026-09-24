package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterModeTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "ModelSrc": { "input": { "required": {} } },
          "Patch": { "input": { "required": { "model": ["MODEL", {}] } } },
          "Mixer": { "input": { "required": { "clip": ["CLIP", {}], "model": ["MODEL", {}] } } },
          "Sink": { "input": { "required": { "model": ["MODEL", {}] } } }
        }
    """.trimIndent()).asJsonObject)

    private fun convert(graph: String): JsonObject =
        JsonParser.parseString(GraphToApiConverter.convert(graph, objectInfo).json).asJsonObject

    private fun modelSrc(id: Int) =
        """{"id": $id, "type": "ModelSrc", "mode": 0, "inputs": [], "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []}"""

    private fun sink(id: Int, link: Int) =
        """{"id": $id, "type": "Sink", "mode": 0, "inputs": [{"name": "model", "type": "MODEL", "link": $link}], "widgets_values": []}"""

    @Test
    fun `bypassed node is removed and consumer is rewired to its upstream`() {
        val api = convert("""
            {"nodes": [ ${modelSrc(1)},
              {"id": 2, "type": "Patch", "mode": 4,
               "inputs": [{"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "MODEL"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertFalse("bypassed node must not be sent", api.has("2"))
        val link = api.getAsJsonObject("3").getAsJsonObject("inputs").getAsJsonArray("model")
        assertEquals("1", link[0].asString)
    }

    @Test
    fun `bypass picks the input matching the output type not the first input`() {
        val api = convert("""
            {"nodes": [ ${modelSrc(1)},
              {"id": 4, "type": "Src2", "mode": 0, "inputs": [], "outputs": [{"name": "clip", "type": "CLIP"}], "widgets_values": []},
              {"id": 2, "type": "Mixer", "mode": 4,
               "inputs": [{"name": "clip", "type": "CLIP", "link": 12}, {"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [12, 4, 0, 2, 0, "CLIP"], [10, 1, 0, 2, 1, "MODEL"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        val link = api.getAsJsonObject("3").getAsJsonObject("inputs").getAsJsonArray("model")
        assertEquals("1", link[0].asString)
    }

    @Test
    fun `chained bypassed nodes resolve through both`() {
        val api = convert("""
            {"nodes": [ ${modelSrc(1)},
              {"id": 2, "type": "Patch", "mode": 4, "inputs": [{"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              {"id": 5, "type": "Patch", "mode": 4, "inputs": [{"name": "model", "type": "MODEL", "link": 13}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "MODEL"], [13, 2, 0, 5, 0, "MODEL"], [11, 5, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertFalse(api.has("2"))
        assertFalse(api.has("5"))
        assertEquals("1", api.getAsJsonObject("3").getAsJsonObject("inputs").getAsJsonArray("model")[0].asString)
    }

    @Test
    fun `muted node is removed and consumer input is dropped`() {
        val api = convert("""
            {"nodes": [ ${modelSrc(1)},
              {"id": 2, "type": "Patch", "mode": 2, "inputs": [{"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "MODEL"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertFalse(api.has("2"))
        assertFalse(api.getAsJsonObject("3").getAsJsonObject("inputs").has("model"))
    }

    @Test
    fun `active node with mode 0 is unaffected`() {
        val api = convert("""
            {"nodes": [ ${modelSrc(1)},
              {"id": 2, "type": "Patch", "mode": 0, "inputs": [{"name": "model", "type": "MODEL", "link": 10}],
               "outputs": [{"name": "model", "type": "MODEL"}], "widgets_values": []},
              ${sink(3, 11)}
            ], "links": [ [10, 1, 0, 2, 0, "MODEL"], [11, 2, 0, 3, 0, "MODEL"] ]}
        """.trimIndent())
        assertTrue(api.has("2"))
        assertEquals("2", api.getAsJsonObject("3").getAsJsonObject("inputs").getAsJsonArray("model")[0].asString)
    }
}
