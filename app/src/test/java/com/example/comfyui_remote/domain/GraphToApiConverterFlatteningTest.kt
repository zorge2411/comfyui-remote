package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.example.comfyui_remote.domain.GraphToApiConverter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterFlatteningTest {

    @Test
    fun testSingleHopFlattening() {
        // A -> Unknown -> B
        // output: A -> B
        
        val graphJson = """
            {
              "nodes": [
                { "id": "1", "type": "SourceNode", "inputs": [] },
                { "id": "2", "type": "UnknownNode", "inputs": [
                    {"name":"in", "type":"IMAGE", "link":10}
                ]},
                { "id": "3", "type": "TargetNode", "inputs": [
                    {"name":"images", "type":"IMAGE", "link":11}
                ]}
              ],
              "links": [
                [10, 1, 0, 2, 0, "IMAGE"], 
                [11, 2, 0, 3, 0, "IMAGE"]
              ]
            }
        """.trimIndent()
        
        val metaJson = """
            {
                "SourceNode": { "input": {"required":{}}, "output": ["IMAGE"] },
                "TargetNode": { "input": {"required":{"images":["IMAGE"]}}, "output": [] }
            }
        """
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(metaJson).asJsonObject)
        
        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val json = JsonParser.parseString(result.json).asJsonObject
        
        assertTrue("Output should contain Node 3", json.has("3"))
        val node3inputs = json.getAsJsonObject("3").getAsJsonObject("inputs")
        
        assertTrue("Node 3 input 'images' should exist", node3inputs.has("images"))
        val link = node3inputs.getAsJsonArray("images")
        
        assertEquals("Should link to Node 1", "1", link.get(0).asString)
        assertEquals("Should link to Slot 0", 0, link.get(1).asInt)
    }

    @Test
    fun testMultiHopFlattening() {
        // A -> U1 -> U2 -> B
        // Should result in A -> B
        
        val graphJson = """
            {
              "nodes": [
                { "id": "1", "type": "SourceNode", "inputs": [] },
                { "id": "2", "type": "UnknownNode1", "inputs": [
                    {"name":"in", "type":"LATENT", "link":10}
                ]},
                { "id": "3", "type": "UnknownNode2", "inputs": [
                    {"name":"in", "type":"LATENT", "link":11}
                ]},
                { "id": "4", "type": "TargetNode", "inputs": [
                    {"name":"samples", "type":"LATENT", "link":12}
                ]}
              ],
              "links": [
                [10, 1, 0, 2, 0, "LATENT"], 
                [11, 2, 0, 3, 0, "LATENT"], 
                [12, 3, 0, 4, 0, "LATENT"]
              ]
            }
        """.trimIndent()
        
        val metaJson = """
            {
                "SourceNode": { "input": {}, "output": ["LATENT"] },
                "TargetNode": { "input": {"required":{"samples":["LATENT"]}}, "output": [] }
            }
        """
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(metaJson).asJsonObject)
        
        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val json = JsonParser.parseString(result.json).asJsonObject
        
        val node4 = json.getAsJsonObject("4")
        val link = node4.getAsJsonObject("inputs").getAsJsonArray("samples")
        
        assertEquals("Should link to Node 1", "1", link.get(0).asString)
    }

    @Test
    fun testAmbiguityResolution() {
        // (A, B) -> Unknown -> C
        // Unknown has 2 inputs. We should pick the first valid link found (heuristic).
        
        val graphJson = """
            {
              "nodes": [
                { "id": "1", "type": "SourceA", "inputs": [] },
                { "id": "2", "type": "SourceB", "inputs": [] },
                { "id": "3", "type": "UnknownNode", "inputs": [
                     {"name":"in1", "type":"IMAGE", "link":10},
                     {"name":"in2", "type":"IMAGE", "link":11}
                ]},
                { "id": "4", "type": "TargetNode", "inputs": [
                     {"name":"image", "type":"IMAGE", "link":12}
                ]}
              ],
              "links": [
                [10, 1, 0, 3, 0, "IMAGE"], 
                [11, 2, 0, 3, 1, "IMAGE"], 
                [12, 3, 0, 4, 0, "IMAGE"]
              ]
            }
        """.trimIndent()
         val metaJson = """
            {
                "SourceA": {}, "SourceB": {},
                "TargetNode": { "input": {"required":{"image":["IMAGE"]}} }
            }
        """
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(metaJson).asJsonObject)
        
        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val json = JsonParser.parseString(result.json).asJsonObject
        
        val node4 = json.getAsJsonObject("4")
        val inputs = node4.getAsJsonObject("inputs")
        
        assertTrue("Should have image input", inputs.has("image"))
        val link = inputs.getAsJsonArray("image")
        
        // Heuristic: Should pick the input at slot 0 (Node 1)
        assertEquals("Should prioritize input from Node 1", "1", link.get(0).asString)
    }

    @Test
    fun testUuidNodeWithWidgetsFlattening() {
        // A -> UUID(Phantom) -> B
        // Phantom has widgets, so current heuristic SKIPS flattening and lets it fall through.
        // Expected: Should be Flattened because UUID suggests it's a frontend-only node (or we decide it should be).
        
        val graphJson = """
            {
              "nodes": [
                { "id": "1", "type": "SourceNode", "inputs": [] },
                { "id": "2", "type": "db9e0685-d161-4026-b52c-d0cd40ff7381", 
                  "widgets_values": ["Some Text Widget Value"],
                  "inputs": [
                    {"name":"in", "type":"IMAGE", "link":10}
                  ]
                },
                { "id": "3", "type": "TargetNode", "inputs": [
                    {"name":"images", "type":"IMAGE", "link":11}
                ]}
              ],
              "links": [
                [10, 1, 0, 2, 0, "IMAGE"], 
                [11, 2, 0, 3, 0, "IMAGE"]
              ]
            }
        """.trimIndent()
        
        val metaJson = """
            {
                "SourceNode": { "input": {"required":{}}, "output": ["IMAGE"] },
                "TargetNode": { "input": {"required":{"images":["IMAGE"]}}, "output": [] }
            }
        """
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(metaJson).asJsonObject)
        
        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val json = JsonParser.parseString(result.json).asJsonObject
        
        assertFalse("Output should NOT contain Node 2 (Should be flattened)", json.has("2"))
        assertTrue("Output should contain Node 3", json.has("3"))
        
        val node3inputs = json.getAsJsonObject("3").getAsJsonObject("inputs")
        val link = node3inputs.getAsJsonArray("images")
        
        assertEquals("Should link directly to Node 1 (bypassing Node 2)", "1", link.get(0).asString)
    }
}
