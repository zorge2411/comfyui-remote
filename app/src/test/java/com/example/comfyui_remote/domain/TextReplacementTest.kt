package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.example.comfyui_remote.domain.TextReplacement.NodeRef
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Phase 102: the frontend's applyTextReplacements (utils/searchAndReplace.ts). */
class TextReplacementTest {

    private val now = LocalDateTime.of(2026, 3, 7, 9, 5, 4)
    private val nodes = listOf(
        NodeRef("KSampler", "Main Sampler", mapOf("steps" to "20", "sampler_name" to "euler")),
        NodeRef(null, "Loader", mapOf("ckpt_name" to "models/sd:xl*v1.safetensors", "empty" to null))
    )

    private fun apply(text: String) = TextReplacement.apply(text, nodes, now)

    @Test fun `date tokens are padded to the token length`() {
        assertEquals("2026-03-07", apply("%date:yyyy-MM-dd%"))
        assertEquals("3/7 9:5:4", apply("%date:M/d h:m:s%"))
        assertEquals("09-05-04", apply("%date:hh-mm-ss%"))
        assertEquals("26", apply("%date:yy%"))
    }

    @Test fun `only the part before the first dot of a date format is used`() {
        assertEquals("2026", apply("%date:yyyy.MM.dd%"))
    }

    @Test fun `widget value by S&R name, then by title`() {
        assertEquals("steps=20", apply("steps=%KSampler.steps%"))
        assertEquals("euler", apply("%Main Sampler.sampler_name%".replace("Main Sampler", "KSampler")))
        assertEquals("20", apply("%Main Sampler.steps%"))
    }

    @Test fun `unsafe characters in a value become underscores, null is empty`() {
        assertEquals("models_sd_xl_v1.safetensors", apply("%Loader.ckpt_name%"))
        assertEquals("[]", apply("[%Loader.empty%]"))
    }

    @Test fun `unknown patterns are left as they are`() {
        for (t in listOf("%width%", "%height%", "%Nope.steps%", "%KSampler.nope%", "%a.b.c%", "100%", "%%")) {
            assertEquals(t, apply(t))
        }
    }

    @Test fun `several patterns in one text`() {
        assertEquals("a_2026_20_b", apply("a_%date:yyyy%_%KSampler.steps%_b"))
    }

    // --- Through the converter ---

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "KSampler": {"input": {"required": {"steps": ["INT", {"default": 20}]}}, "output": ["LATENT"]},
          "Text": {"input": {"required": {"text": ["STRING", {"multiline": true}]}}, "output": []}
        }
    """.trimIndent()).asJsonObject)

    private fun graph(replace: Boolean, text: String) = """
        {"nodes": [
          {"id": 1, "type": "KSampler", "mode": 0, "properties": {"Node name for S&R": "KSampler"}, "inputs": [{"name": "steps", "type": "INT", "widget": {"name": "steps"}}], "widgets_values": [35]},
          {"id": 2, "type": "PrimitiveNode", "mode": 0, "properties": {"Run widget replace on values": $replace},
           "outputs": [{"name": "STRING", "type": "STRING", "widget": {"name": "text"}, "links": [10]}], "widgets_values": ["$text"]},
          {"id": 3, "type": "Text", "mode": 0, "inputs": [{"name": "text", "type": "STRING", "widget": {"name": "text"}, "link": 10}], "widgets_values": [""]}],
         "links": [[10, 2, 0, 3, 0, "STRING"]]}
    """.trimIndent()

    private fun text(graph: String): String =
        JsonParser.parseString(GraphToApiConverter.convert(graph, objectInfo, now).json).asJsonObject
            .getAsJsonObject("3").getAsJsonObject("inputs").get("text").asString

    @Test fun `converter applies the replacement when the property is on`() {
        assertEquals("day 2026-03-07 steps 35", text(graph(true, "day %date:yyyy-MM-dd% steps %KSampler.steps%")))
    }

    @Test fun `converter leaves the text alone when the property is off`() {
        assertEquals("day %date:yyyy-MM-dd%", text(graph(false, "day %date:yyyy-MM-dd%")))
    }
}
