package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterFallbackTest {

    @Test
    fun testFallbackForMissingMetadata() {
        // A graph with a node that has NO metadata in objectInfo
        val graphJson = """
            {
              "nodes": [
                {
                  "id": "100",
                  "type": "UnknownNode",
                  "widgets_values": ["some_value"],
                  "inputs": [
                     {"name": "linked_input", "link": 1}
                  ]
                },
                {
                    "id": "1",
                    "type": "SourceNode"
                }
              ],
              "links": [
                [1, 1, 0, 100, 0, "TYPE"]
              ]
            }
        """.trimIndent()

        val emptyObjectInfo = ComfyObjectInfo(JsonObject()) // No metadata

        val result = GraphToApiConverter.convert(graphJson, emptyObjectInfo)
        
        // With fallback logic, it IS in missingNodes (because metadata is null), 
        // BUT it should be included in the JSON.
        assertTrue("UnknownNode should be in missingNodes list (warning)", result.missingNodes.contains("UnknownNode"))
        
        val json = com.google.gson.JsonParser.parseString(result.json).asJsonObject
        assertTrue("JSON should contain node 100", json.has("100"))
        
        val node100 = json.getAsJsonObject("100")
        val inputs = node100.getAsJsonObject("inputs")
        
        // Check Linked Input (Explicit in Graph)
        assertTrue("Should have linked_input", inputs.has("linked_input"))
        
        // Check Heuristic Widget Mapping
        assertTrue("Should have 'value' mapped from widget", inputs.has("value"))
        assertEquals("some_value", inputs.get("value").asString)
    }

    @Test
    fun testFallbackImageHeuristic() {
        val graphJson = """
            {
              "nodes": [
                {
                  "id": "200",
                  "type": "CustomLoader",
                  "widgets_values": ["my_photo.png", "image"]
                }
              ],
              "links": []
            }
        """.trimIndent()

        val emptyObjectInfo = ComfyObjectInfo(JsonObject())
        val result = GraphToApiConverter.convert(graphJson, emptyObjectInfo)
        
        val json = com.google.gson.JsonParser.parseString(result.json).asJsonObject
        assertTrue(json.has("200"))
        
        val inputs = json.getAsJsonObject("200").getAsJsonObject("inputs")
        assertTrue("Should map .png widget to 'image'", inputs.has("image"))
        assertEquals("my_photo.png", inputs.get("image").asString)
    }
}
