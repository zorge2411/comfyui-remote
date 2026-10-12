package com.example.comfyui_remote.domain

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Phase 102: COLOR and BOUNDING_BOX fields in the form. */
class ColorAndBoxFieldTest {

    private val metadata: JsonObject = JsonParser.parseString("""
        {
          "Crop": {"input": {"required": {
            "image": ["IMAGE", {}],
            "crop_region": ["BOUNDING_BOX", {"default": {"x": 0, "y": 0, "width": 512, "height": 512}, "socketless": true}],
            "background": ["COLOR", {"default": "#ffffff", "socketless": true}],
            "bboxes": ["BOUNDING_BOX", {"forceInput": true}]
          }}}
        }
    """.trimIndent()).asJsonObject

    private val prompt = """
        {"1": {"class_type": "Crop", "inputs": {"image": ["9", 0],
               "crop_region": {"x": 5, "y": 6, "width": 70, "height": 80},
               "background": "#336699", "bboxes": ["8", 0]}}}
    """.trimIndent()

    private val parser = WorkflowParser()

    @Test fun `hex validation and normalisation`() {
        for (ok in listOf("#fff", "#FFFFFF", "#336699", " #abc ")) assertTrue(ok, ColorHex.isValid(ok))
        for (bad in listOf("", "fff", "#ff", "#ffff", "#gggggg", "red", "#1234567")) assertFalse(bad, ColorHex.isValid(bad))
        assertEquals("#aabbcc", ColorHex.normalize("#ABC"))
        assertEquals(0x336699, ColorHex.toRgb("#336699"))
        assertNull(ColorHex.normalize("nope"))
    }

    @Test fun `parser makes a colour field and a bounding box field, links stay hidden`() {
        val fields = parser.parse(prompt, metadata)
        assertEquals(InputField.ColorInput("1", "background", "#336699", "Crop"), fields.single { it.fieldName == "background" })
        assertEquals(InputField.BoundingBoxInput("1", "crop_region", 5, 6, 70, 80, "Crop"), fields.single { it.fieldName == "crop_region" })
        assertTrue(fields.none { it.fieldName == "bboxes" || it.fieldName == "image" })
    }

    @Test fun `without the node list a colour stays a text field and an object is not editable`() {
        val fields = parser.parse(prompt, null)
        assertTrue(fields.single { it.fieldName == "background" } is InputField.StringInput)
        assertTrue(fields.none { it.fieldName == "crop_region" })
    }

    @Test fun `executor writes edited values and ignores an invalid colour`() {
        val fields = parser.parse(prompt, metadata).map {
            when (it) {
                is InputField.ColorInput -> it.copy(value = "#ABC")
                is InputField.BoundingBoxInput -> it.copy(x = 1, height = 99)
                else -> it
            }
        }
        val inputs = JsonParser.parseString(WorkflowExecutor().injectValues(prompt, fields)).asJsonObject
            .getAsJsonObject("1").getAsJsonObject("inputs")
        assertEquals("#aabbcc", inputs.get("background").asString)
        val box = inputs.getAsJsonObject("crop_region")
        assertEquals(listOf(1, 6, 70, 99), listOf("x", "y", "width", "height").map { box.get(it).asInt })

        val invalid = parser.parse(prompt, metadata).map { if (it is InputField.ColorInput) it.copy(value = "red") else it }
        val kept = JsonParser.parseString(WorkflowExecutor().injectValues(prompt, invalid)).asJsonObject
            .getAsJsonObject("1").getAsJsonObject("inputs")
        assertEquals("#336699", kept.get("background").asString)
    }

    @Test fun `the new labels are distinct`() {
        assertEquals("Color", InputField.ColorInput("1", "c", "#fff", "t").label)
        assertEquals("Bounding Box", InputField.BoundingBoxInput("1", "b", 0, 0, 1, 1, "t").label)
    }
}
