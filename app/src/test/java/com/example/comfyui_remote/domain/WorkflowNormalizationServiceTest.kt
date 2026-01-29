package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowNormalizationServiceTest {

    private val parser = WorkflowParser()
    private val service = WorkflowNormalizationService(parser)

    @Test
    fun `test normalization extracts models from API format`() {
        val json = """
            {
              "4": {
                "inputs": {
                  "ckpt_name": "v1-5-pruned-emaonly.ckpt"
                },
                "class_type": "CheckpointLoaderSimple"
              }
            }
        """.trimIndent()

        val normalized = service.normalize("Test", json, WorkflowSource.LOCAL_IMPORT)
        
        assertEquals("Test", normalized.name)
        assertTrue(normalized.baseModels.contains("v1-5-pruned-emaonly.ckpt"))
    }

    @Test
    fun `test normalization strips UI metadata`() {
        val json = """
            {
              "1": {
                "inputs": { "text": "hello" },
                "class_type": "CLIPTextEncode",
                "pos": [100, 200],
                "size": {"0": 100, "1": 50},
                "_meta": {
                  "title": "My Prompt",
                  "extra": "noise"
                }
              }
            }
        """.trimIndent()

        val normalized = service.normalize("Clean", json, WorkflowSource.LOCAL_IMPORT)
        val resultObj = JsonParser.parseString(normalized.jsonContent).asJsonObject
        val node1 = resultObj.getAsJsonObject("1")

        assertTrue(node1.has("class_type"))
        assertTrue(node1.has("inputs"))
        assertTrue(node1.has("_meta"))
        assertEquals("My Prompt", node1.getAsJsonObject("_meta").get("title").asString)

        // Metadata that should be stripped
        assertFalse(node1.has("pos"))
        assertFalse(node1.has("size"))
        assertFalse(node1.getAsJsonObject("_meta").has("extra"))
    }

    @Test
    fun `test normalization handles Workspace format with objectInfo`() {
        // Mock Workspace format (simplified)
        val workspaceJson = """
            {
              "nodes": [
                {
                  "id": 1,
                  "type": "CLIPTextEncode",
                  "widgets_values": ["masterpiece"],
                  "title": "Text Node"
                }
              ],
              "links": []
            }
        """.trimIndent()

        val objectInfo = JsonObject()
        val nodeDef = JsonObject()
        val inputDef = JsonObject()
        val required = JsonObject()
        required.add("text", JsonParser.parseString("""["STRING", {"multiline": true}]"""))
        inputDef.add("required", required)
        nodeDef.add("input", inputDef)
        objectInfo.add("CLIPTextEncode", nodeDef)

        val normalized = service.normalize("Workspace", workspaceJson, WorkflowSource.LOCAL_IMPORT, objectInfo)
        
        val resultObj = JsonParser.parseString(normalized.jsonContent).asJsonObject
        assertTrue(resultObj.entrySet().size > 0)
        
        // Check if it converted to API format (lookup by string ID "1")
        val node1 = resultObj.getAsJsonObject("1")
        assertEquals("CLIPTextEncode", node1.get("class_type").asString)
        assertEquals("masterpiece", node1.getAsJsonObject("inputs").get("text").asString)
    }

    @Test
    fun `test normalization handles malformed json gracefully`() {
        val json = "invalid json"
        val normalized = service.normalize("Test", json, WorkflowSource.LOCAL_IMPORT)
        
        assertEquals("Test", normalized.name)
        assertTrue(normalized.baseModels.isEmpty())
    }
}
