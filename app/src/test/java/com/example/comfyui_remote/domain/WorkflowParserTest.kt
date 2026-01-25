package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowParserTest {

    private val parser = WorkflowParser()

    @Test
    fun parse_detectsLoadImageNode() {
        val json = """
            {
              "10": {
                "inputs": {
                  "image": "example.png",
                  "upload": "image"
                },
                "class_type": "LoadImage",
                "_meta": {
                  "title": "Load Image"
                }
              },
              "3": {
                "inputs": {
                  "seed": 12345,
                  "steps": 20,
                  "cfg": 8.0,
                  "sampler_name": "euler",
                  "scheduler": "normal",
                  "denoise": 1.0,
                  "model": ["4", 0],
                  "positive": ["6", 0],
                  "negative": ["7", 0],
                  "latent_image": ["5", 0]
                },
                "class_type": "KSampler",
                "_meta": {
                  "title": "KSampler"
                }
              }
            }
        """.trimIndent()

        val results = parser.parse(json)

        val imageInput = results.find { it is InputField.ImageInput } as? InputField.ImageInput
        
        assertTrue("Should detect ImageInput", imageInput != null)
        assertEquals("10", imageInput?.nodeId)
        assertEquals("image", imageInput?.fieldName)
        assertEquals("example.png", imageInput?.value)
        assertEquals("Load Image", imageInput?.nodeTitle)
    }
    @Test
    fun parse_detectsComboAsSelectionInput() {
        val json = """
            {
              "3": {
                "inputs": {
                  "sampler_name": "euler"
                },
                "class_type": "KSampler",
                "_meta": {
                  "title": "KSampler"
                }
              }
            }
        """.trimIndent()
        
        val metadataJson = """
            {
              "KSampler": {
                "input": {
                  "required": {
                    "sampler_name": [["euler", "euler_ancestral", "heun"], {}]
                  }
                }
              }
            }
        """.trimIndent()
        val metadata = com.google.gson.JsonParser.parseString(metadataJson).asJsonObject

        val results = parser.parse(json, metadata)

        val selectionInput = results.find { it is InputField.SelectionInput } as? InputField.SelectionInput
        
        assertTrue("Should detect SelectionInput", selectionInput != null)
        assertEquals("3", selectionInput?.nodeId)
        assertEquals("sampler_name", selectionInput?.fieldName)
        assertEquals("euler", selectionInput?.value)
        assertEquals(listOf("euler", "euler_ancestral", "heun"), selectionInput?.options)
    }
}

