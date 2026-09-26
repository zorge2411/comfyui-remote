package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphToApiConverterWidgetTest {

    @Test
    fun `convert preserves unknown nodes if they have widgets`() {
        // A graph with an UnknownNode that has widgets (e.g. a KSampler without metadata)
        // It should NOT be flattened/removed.
        val graphJson = """
            {
              "nodes": [
                {
                  "id": 1,
                  "type": "UnknownFunctionalNode",
                  "inputs": [],
                  "widgets_values": [123, "random_string"] 
                },
                {
                  "id": 2,
                  "type": "Note", 
                  "widgets_values": ["Just a note"]
                }
              ],
              "links": []
            }
        """.trimIndent()

        // Empty object info (simulating missing metadata)
        val objectInfo = ComfyObjectInfo(JsonObject())

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        
        // We expect UnknownFunctionalNode to be PRESENT in the output API json (keys)
        // because it has widgets, implying it's not just a routing node.
        val resultJson = com.google.gson.JsonParser.parseString(result.json).asJsonObject
        
        assertTrue("Should preserve functional node with widgets", resultJson.has("1"))
        
        // Note: 'Note' type is explicitly filtered in code, so it might be gone, or kept?
        // Code filters "MarkdownNote", "Note" explicitly.
        assertFalse("Should filter explicit Note types", resultJson.has("2"))
    }
}
