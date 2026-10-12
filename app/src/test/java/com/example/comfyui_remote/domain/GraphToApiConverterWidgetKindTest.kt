package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 102: widget kinds with their own saved shapes (COLOR, BOUNDING_BOX, CURVE, VIDEO_EDIT) keep their
 * widgets_values slot, so the widgets around them stay aligned and the values come through as saved.
 */
class GraphToApiConverterWidgetKindTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "Kinds": {"input": {"required": {
            "width": ["INT", {"default": 512}],
            "color": ["COLOR", {"default": "#ffffff", "socketless": true}],
            "region": ["BOUNDING_BOX", {"default": {"x": 0, "y": 0, "width": 512, "height": 512}, "socketless": true}],
            "curve": ["CURVE", {"default": {"points": [[0, 0], [1, 1]], "interpolation": "monotone_cubic"}, "socketless": true}],
            "trim": ["VIDEO_EDIT", {"default": {"trim": {"start_time": 0.0, "duration": 0.0}}, "socketless": true}],
            "label": ["STRING", {}]
          }}, "output": ["IMAGE"]},
          "Forced": {"input": {"required": {
            "bboxes": ["BOUNDING_BOX", {"forceInput": true, "socketless": true}],
            "label": ["STRING", {}]
          }}, "output": []},
          "Src": {"input": {"required": {}}, "output": ["BOUNDING_BOX"]}
        }
    """.trimIndent()).asJsonObject)

    private fun inputs(graph: String, id: String) =
        JsonParser.parseString(GraphToApiConverter.convert(graph.trimIndent(), objectInfo).json).asJsonObject
            .getAsJsonObject(id).getAsJsonObject("inputs")

    @Test fun `custom widget values pass through and neighbours stay aligned`() {
        val i = inputs("""
            {"nodes": [{"id": 1, "type": "Kinds", "mode": 0, "inputs": [],
              "widgets_values": [640, "#336699", {"x": 5, "y": 6, "width": 70, "height": 80},
                                 {"points": [[0, 0], [0.5, 0.7], [1, 1]], "interpolation": "linear"},
                                 {"trim": {"start_time": 1.5, "duration": 3.0}}, "after"]}],
             "links": []}
        """, "1")
        assertEquals(640, i.get("width").asInt)
        assertEquals("#336699", i.get("color").asString)
        assertEquals(70, i.getAsJsonObject("region").get("width").asInt)
        assertEquals("linear", i.getAsJsonObject("curve").get("interpolation").asString)
        assertEquals(3.0, i.getAsJsonObject("trim").getAsJsonObject("trim").get("duration").asDouble, 0.0)
        assertEquals("after", i.get("label").asString)
    }

    @Test fun `a widget added after saving gets the spec default`() {
        val i = inputs("""
            {"nodes": [{"id": 1, "type": "Kinds", "mode": 0, "inputs": [],
              "widgets_values": [640, "#336699"], "widgets_values_named": {"width": 640, "color": "#336699"}}],
             "links": []}
        """, "1")
        assertEquals("#336699", i.get("color").asString)
    }

    @Test fun `a linked forceInput bounding box is a link and consumes no widget slot`() {
        val i = inputs("""
            {"nodes": [{"id": 1, "type": "Src", "mode": 0, "inputs": [], "outputs": [{"name": "o", "type": "BOUNDING_BOX", "links": [10]}]},
                       {"id": 2, "type": "Forced", "mode": 0,
                        "inputs": [{"name": "bboxes", "type": "BOUNDING_BOX", "link": 10}], "widgets_values": ["text"]}],
             "links": [[10, 1, 0, 2, 0, "BOUNDING_BOX"]]}
        """, "2")
        assertEquals("1", i.getAsJsonArray("bboxes")[0].asString)
        assertEquals("text", i.get("label").asString)
    }
}
