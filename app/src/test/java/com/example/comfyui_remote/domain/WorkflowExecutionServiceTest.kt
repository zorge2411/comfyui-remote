package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ImageRepository
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowExecutionServiceTest {

    private val service = WorkflowExecutionService(ImageRepository(), WorkflowExecutor())

    @Test
    fun `buildPrompt patches uploaded images and injects form values`() {
        val workflow = """
            {
              "5": {"class_type": "LoadImage", "inputs": {"image": "example.png"}},
              "6": {"class_type": "CLIPTextEncode", "inputs": {"text": "old prompt", "clip": ["4", 1]}},
              "7": {"class_type": "KSampler", "inputs": {"steps": 20}}
            }
        """.trimIndent()

        val prompt = JsonParser.parseString(
            service.buildPrompt(
                workflow,
                mapOf("5" to "uploaded_123.png"),
                listOf(
                    InputField.StringInput("6", "text", "a cat", "Prompt"),
                    InputField.IntInput("7", "steps", 30, "Sampler")
                )
            )
        ).asJsonObject

        assertEquals("uploaded_123.png", prompt.getAsJsonObject("5").getAsJsonObject("inputs").get("image").asString)
        assertEquals("a cat", prompt.getAsJsonObject("6").getAsJsonObject("inputs").get("text").asString)
        assertEquals("4", prompt.getAsJsonObject("6").getAsJsonObject("inputs").getAsJsonArray("clip")[0].asString)
        assertEquals(30, prompt.getAsJsonObject("7").getAsJsonObject("inputs").get("steps").asInt)
    }
}
