package com.example.comfyui_remote.domain

import org.junit.Assert.*
import org.junit.Test

class WorkflowTemplateIndexTest {

    private val index = """
        [
          {"moduleName": "default", "category": "Foundation", "title": "Image", "type": "image", "templates": [
            {"name": "image_z_image_turbo", "title": "Z-Image-Turbo: Text to Image", "mediaType": "image",
             "mediaSubtype": "webp", "description": "Efficient image generation.", "tags": ["Image", "Text to Image"],
             "models": ["Z-Image-Turbo"], "openSource": true},
            {"name": "api_google_gemini", "title": "Gemini", "mediaSubtype": "webp", "description": "Chat with Gemini",
             "tags": ["LLM"], "models": ["Gemini"], "openSource": false},
            {"title": "no name, skipped"}
          ]},
          {"category": "Foundation", "title": "Audio", "templates": [
            {"name": "audio_stable_audio_example", "title": "Stable Audio", "mediaSubtype": "mp3", "tags": ["Audio"], "models": []}
          ]},
          {"category": "Applied", "title": "Empty", "templates": []}
        ]
    """.trimIndent()

    @Test
    fun `parses categories and templates, skipping nameless entries and empty categories`() {
        val categories = WorkflowTemplateIndex.parse(index)
        assertEquals(listOf("Image", "Audio"), categories.map { it.title })
        assertEquals(listOf("image_z_image_turbo", "api_google_gemini"), categories[0].templates.map { it.name })
        assertEquals("Foundation", categories[0].group)
        assertFalse(categories[0].templates[1].openSource)
        assertTrue("openSource defaults to true", categories[1].templates[0].openSource)
    }

    @Test
    fun `thumbnail and workflow paths follow the ComfyUI frontend`() {
        val t = WorkflowTemplateIndex.parse(index)[1].templates[0]
        assertEquals("http://192.168.1.5:8188/templates/audio_stable_audio_example-1.mp3", t.thumbnailUrl("http://192.168.1.5:8188/"))
        assertEquals("templates/audio_stable_audio_example.json", t.workflowPath)
    }

    @Test
    fun `filter matches all query words across title tags and models, optionally within a category`() {
        val categories = WorkflowTemplateIndex.parse(index)
        assertEquals(listOf("image_z_image_turbo"), WorkflowTemplateIndex.filter(categories, "text image").map { it.name })
        assertEquals(listOf("api_google_gemini"), WorkflowTemplateIndex.filter(categories, "gemini").map { it.name })
        assertEquals(3, WorkflowTemplateIndex.filter(categories, "").size)
        assertEquals(listOf("audio_stable_audio_example"), WorkflowTemplateIndex.filter(categories, "", "Audio").map { it.name })
    }

    @Test
    fun `non-array index yields no categories`() {
        assertTrue(WorkflowTemplateIndex.parse("""{"error": "not found"}""").isEmpty())
    }
}
