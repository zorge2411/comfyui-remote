package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterTest {

    @Test
    fun convert_BasicGraph_ReturnsApiFormat() {
        // Mock Object Info
        val objectInfoJson = """
            {
                "CheckpointLoaderSimple": {
                    "input": {
                        "required": {
                            "ckpt_name": [ ["v1.5.ckpt"], {} ]
                        }
                    }
                },
                "EmptyLatentImage": {
                    "input": {
                        "required": {
                            "width": ["INT", {"default": 512}],
                            "height": ["INT", {"default": 512}],
                            "batch_size": ["INT", {"default": 1}]
                        }
                    }
                },
                "KSampler": {
                    "input": {
                         "required": {
                             "model": ["MODEL", {}],
                             "seed": ["INT", {}],
                             "steps": ["INT", {}],
                             "cfg": ["FLOAT", {}],
                             "sampler_name": ["COMBO", {}],
                             "scheduler": ["COMBO", {}],
                             "positive": ["CONDITIONING", {}],
                             "negative": ["CONDITIONING", {}],
                             "latent_image": ["LATENT", {}],
                             "denoise": ["FLOAT", {}]
                         }
                    }
                }
            }
        """.trimIndent()
        
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(objectInfoJson).asJsonObject)

        // Graph JSON (simplified)
        val graphJson = """
            {
                "nodes": [
                    {
                        "id": 10, // String in API, Int in Graph (typ)
                        "type": "CheckpointLoaderSimple",
                        "inputs": [],
                        "widgets_values": ["v1.5.ckpt"]
                    },
                    {
                        "id": 20,
                        "type": "EmptyLatentImage",
                        "inputs": [],
                        "widgets_values": [512, 768, 1]
                    },
                    {
                        "id": 30,
                        "type": "KSampler",
                        "inputs": [
                            {"name": "model", "type": "MODEL", "link": 1},
                            {"name": "positive", "type": "CONDITIONING", "link": null}, 
                            {"name": "latent_image", "type": "LATENT", "link": 2}
                        ],
                        "widgets_values": [1234, 20, 8.0, "euler", "normal", 1.0]
                    }
                ],
                "links": [
                    [1, 10, 0, 30, 0, "MODEL"],
                    [2, 20, 0, 30, 2, "LATENT"]
                ]
            }
        """.trimIndent()

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val apiObj = JsonParser.parseString(result.json).asJsonObject

        assertTrue(apiObj.has("10"))
        assertTrue(apiObj.has("20"))
        assertTrue(apiObj.has("30"))

        // Check 10 - CheckpointLoaderSimple
        val node10 = apiObj.getAsJsonObject("10")
        assertEquals("CheckpointLoaderSimple", node10.get("class_type").asString)
        val inputs10 = node10.getAsJsonObject("inputs")
        assertEquals("v1.5.ckpt", inputs10.get("ckpt_name").asString)

        // Check 20 - EmptyLatentImage
        val node20 = apiObj.getAsJsonObject("20")
        assertEquals("EmptyLatentImage", node20.get("class_type").asString)
        val inputs20 = node20.getAsJsonObject("inputs")
        assertEquals(512, inputs20.get("width").asInt)
        assertEquals(768, inputs20.get("height").asInt)
        assertEquals(1, inputs20.get("batch_size").asInt)
        
        // Check 30 - KSampler (Mixed Links and Widgets)
        val node30 = apiObj.getAsJsonObject("30")
        assertEquals("KSampler", node30.get("class_type").asString)
        val inputs30 = node30.getAsJsonObject("inputs")
        
        // Link: model -> [10, 0] (Because link ID 1 is from node 10 slot 0)
        assertTrue(inputs30.get("model").isJsonArray)
        val modelLink = inputs30.getAsJsonArray("model")
        assertEquals(10, modelLink[0].asInt)
        assertEquals(0, modelLink[1].asInt)

        // Link: latent_image -> [20, 0]
        assertTrue(inputs30.get("latent_image").isJsonArray)
        val latentLink = inputs30.getAsJsonArray("latent_image")
        assertEquals(20, latentLink[0].asInt)
        assertEquals(0, latentLink[1].asInt)
        
        // Widget: seed (Index 0 of required inputs that are NOT slots/links?)
        // In definition: model(link, index0), seed(widget, index1), steps(widget, index2)...
        // In graph widgets_values: [1234, 20, ...]
        
        // Logic check: KSampler def order:
        // 1. model (LINKED in graph) -> Takes from 'links' map. SKIPS widget value?
        // Wait, the logic is: Iterate Definition Keys.
        // If key is present in 'slotNames' (meaning it CAN be a link):
        //    If linked: use link.
        //    Else: use widget value? OR does Comfy use widget value for unlinked slots?
        //    Usually slots don't have widget values unless converted to widget.
        //    But my logical impl: 'if (slotNames.contains(key))' -> treat as slot.
        //    If not slot -> take next widget value.
        
        // 'seed' is NOT in 'graphInputs' list in my mock setup above?
        // Let's verify 'graphInputs' key set for KSampler mock:
        // {"name": "model"}, {"name": "positive"}, {"name": "latent_image"}
        // So 'seed' is NOT in slotNames.
        // So it should take from widgetValues.
        // widgetIndex 0 = 1234.
        
        assertEquals(1234, inputs30.get("seed").asInt)
        assertEquals(20, inputs30.get("steps").asInt)
    }

    @Test
    fun convert_SkipsNonExecutableNodes() {
        val objectInfoJson = """
            {
                "CheckpointLoaderSimple": { "input": { "required": { "ckpt_name": [["v1.5.ckpt"], {}] } } }
            }
        """.trimIndent()
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(objectInfoJson).asJsonObject)

        val graphJson = """
            {
                "nodes": [
                    { "id": 1, "type": "CheckpointLoaderSimple", "widgets_values": ["v1.5.ckpt"] },
                    { "id": 2, "type": "MarkdownNote", "widgets_values": ["This is a note"] },
                    { "id": 3, "type": "Note", "widgets_values": ["Another note"] }
                ],
                "links": []
            }
        """.trimIndent()

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val apiObj = JsonParser.parseString(result.json).asJsonObject

        assertTrue("Should include CheckpointLoaderSimple", apiObj.has("1"))
        assertFalse("Should skip MarkdownNote", apiObj.has("2"))
        assertFalse("Should skip Note", apiObj.has("3"))
    }

    @Test
    fun convert_IdentifiesMissingNodes() {
        val objectInfoJson = """
            {
                "SafeNode": { "input": { "required": {} } }
            }
        """.trimIndent()
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(objectInfoJson).asJsonObject)

        val graphJson = """
            {
                "nodes": [
                    { "id": 1, "type": "SafeNode", "inputs": [], "widgets_values": [] },
                    { "id": 2, "type": "MissingNodeClass", "inputs": [], "widgets_values": [] }
                ],
                "links": []
            }
        """.trimIndent()

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val apiObj = JsonParser.parseString(result.json).asJsonObject

        assertTrue(apiObj.has("1"))
        // Current behavior: includes node even if missing. 
        // Desired behavior: skip node if missing.
        assertFalse("Should skip nodes with missing definitions", apiObj.has("2"))
        assertTrue("Should report missing node type", result.missingNodes.contains("MissingNodeClass"))
    }

    @Test
    fun convert_PreservesNodeTitleInMeta() {
        val objectInfoJson = """
            {
                "SomeNode": { "input": { "required": {} } }
            }
        """.trimIndent()
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(objectInfoJson).asJsonObject)

        val graphJson = """
            {
                "nodes": [
                    {
                        "id": 1,
                        "type": "SomeNode",
                        "title": "Custom Title",
                        "inputs": [],
                        "widgets_values": []
                    },
                    {
                        "id": 2,
                        "type": "SomeNode",
                        // No title provided
                        "inputs": [],
                        "widgets_values": []
                    }
                ],
                "links": []
            }
        """.trimIndent()

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val apiObj = JsonParser.parseString(result.json).asJsonObject

        // Check Node 1 (Has Title)
        assertTrue(apiObj.has("1"))
        val node1 = apiObj.getAsJsonObject("1")
        assertTrue("Node 1 should have _meta", node1.has("_meta"))
        assertEquals("Custom Title", node1.getAsJsonObject("_meta").get("title").asString)

        // Check Node 2 (No Title -> Fallback to Type?)
        // Implementation might fallback to Type if title missing, or just not set it?
        // Plan said: "Fallback to classifier or type if title is missing"
        assertTrue(apiObj.has("2"))
        val node2 = apiObj.getAsJsonObject("2")
        assertTrue("Node 2 should have _meta", node2.has("_meta"))
        assertEquals("SomeNode", node2.getAsJsonObject("_meta").get("title").asString)
    }

    @Test
    fun convert_AllowKeyedInputsWithoutMetadata() {
        val objectInfoJson = "{}" // Empty metadata
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(objectInfoJson).asJsonObject)

        val graphJson = """
            {
                "nodes": [
                    {
                        "id": 1,
                        "type": "KeyedNode",
                        "inputs": {
                            "text": "hello",
                            "value": 123
                        }
                    }
                ],
                "links": []
            }
        """.trimIndent()

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val apiObj = JsonParser.parseString(result.json).asJsonObject

        assertTrue("Should include node even if metadata missing because it has keyed inputs", apiObj.has("1"))
        val node1 = apiObj.getAsJsonObject("1")
        assertEquals("KeyedNode", node1.get("class_type").asString)
        val inputs = node1.getAsJsonObject("inputs")
        assertEquals("hello", inputs.get("text").asString)
        assertEquals(123, inputs.get("value").asInt)
        assertTrue("Should still report as missing node for info purposes", result.missingNodes.contains("KeyedNode"))
    }
}
