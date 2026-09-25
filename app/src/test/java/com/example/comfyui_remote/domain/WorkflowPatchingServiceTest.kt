package com.example.comfyui_remote.domain

import org.junit.Assert.*
import org.junit.Test

class WorkflowPatchingServiceTest {

    @Test
    fun testPatchGraphFormat() {
        val json = """
            {
               "nodes": [
                   {
                       "id": "10",
                       "type": "LoadImage",
                       "widgets_values": ["default.png", "image"]
                   },
                   {
                       "id": "12",
                       "type": "KSampler",
                       "widgets_values": [20, 4.5]
                   }
               ]
            }
        """.trimIndent()
        
        val inputs = mapOf("10" to "uploaded_image.png")
        val patched = WorkflowPatchingService.patchWorkflow(json, inputs)
        
        assertTrue(patched.contains("uploaded_image.png"))
        assertFalse(patched.contains("default.png"))
        // Check KSampler untouched
        assertTrue(patched.contains("KSampler"))
    }
    
    @Test
    fun testPatchApiFormat() {
        val json = """
            {
               "10": {
                   "class_type": "LoadImage",
                   "inputs": {
                       "image": "default.png"
                   }
               },
               "12": {
                   "class_type": "KSampler"
               }
            }
        """.trimIndent()
        
        val inputs = mapOf("10" to "uploaded_image.png")
        val patched = WorkflowPatchingService.patchWorkflow(json, inputs)
        
        assertTrue(patched.contains("uploaded_image.png"))
        assertFalse(patched.contains("default.png"))
    }
}
