package com.example.comfyui_remote.domain

import org.junit.Assert.assertEquals
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
              },
              "6": {
                "inputs": {
                  "text": "a cute cat",
                  "clip": ["4", 1]
                },
                "class_type": "CLIPTextEncode"
              }
            }
        """.trimIndent()

        val normalized = service.normalize("Test", json, WorkflowSource.LOCAL_IMPORT)
        
        assertEquals("Test", normalized.name)
        assertTrue(normalized.baseModels.contains("v1-5-pruned-emaonly.ckpt"))
        assertEquals(WorkflowSource.LOCAL_IMPORT, normalized.source)
    }

    @Test
    fun `test normalization handles multiple models and different extensions`() {
        val json = """
            {
              "1": {
                "inputs": { "ckpt_name": "model1.safetensors" }
              },
              "2": {
                "inputs": { "model": "model2.ckpt" }
              }
            }
        """.trimIndent()

        val normalized = service.normalize("Test", json, WorkflowSource.LOCAL_IMPORT)
        
        assertEquals(2, normalized.baseModels.size)
        assertTrue(normalized.baseModels.contains("model1.safetensors"))
        assertTrue(normalized.baseModels.contains("model2.ckpt"))
    }

    @Test
    fun `test normalization handles malformed json gracefully`() {
        val json = "invalid json"
        val normalized = service.normalize("Test", json, WorkflowSource.LOCAL_IMPORT)
        
        assertEquals("Test", normalized.name)
        assertTrue(normalized.baseModels.isEmpty())
    }
}
