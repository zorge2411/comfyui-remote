package com.example.comfyui_remote.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOutputsTest {
    private fun outputs(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun readsTextFromDisplayNodesAndIgnoresMedia() {
        val text = TextOutputs.from(outputs("""
            {"124": {"text": ["Style: live-action\nScene overview: ..."]},
             "92": {"images": [{"filename": "a.png"}]},
             "130": {"text": ["second", "  "]}}
        """))
        assertEquals("Style: live-action\nScene overview: ...\n\nsecond", text)
    }

    @Test
    fun nullWhenNoTextOutput() {
        assertNull(TextOutputs.from(outputs("""{"92": {"images": []}}""")))
        assertNull(TextOutputs.from(outputs("""{"1": {"text": [3, true]}}""")))
        assertNull(TextOutputs.from(null))
    }

    @Test
    fun detectsDisplayNodesInPrompt() {
        assertTrue(TextOutputs.hasDisplayNode("""{"124": {"class_type": "Display Any (rgthree)", "inputs": {}}}"""))
        assertTrue(TextOutputs.hasDisplayNode("""{"5": {"class_type": "PreviewAny", "inputs": {}}}"""))
        assertFalse(TextOutputs.hasDisplayNode("""{"1": {"class_type": "SaveVideo", "inputs": {}}}"""))
        assertFalse(TextOutputs.hasDisplayNode("not json"))
        assertFalse(TextOutputs.hasDisplayNode(null))
    }
}
