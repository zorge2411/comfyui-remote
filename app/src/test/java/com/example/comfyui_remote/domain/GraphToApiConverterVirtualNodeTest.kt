package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Phase 90: frontend-only nodes are resolved like the ComfyUI frontend and never sent. */
class GraphToApiConverterVirtualNodeTest {

    private val objectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "VaeLoader": { "input": { "required": {} }, "output": ["VAE"] },
          "Decode": { "input": { "required": { "vae": ["VAE", {}] } }, "output": ["IMAGE"] },
          "Sampler": { "input": { "required": {
            "seed": ["INT", {"control_after_generate": true}],
            "steps": ["INT", {"default": 20}],
            "sampler_name": [["euler", "dpmpp_2m"], {}]
          } }, "output": ["LATENT"] },
          "IntNode": { "input": { "required": { "value": ["INT", {}] } }, "output": ["INT"] },
          "Preview": { "input": { "required": { "source": ["*", {}] } }, "output": [] }
        }
    """.trimIndent()).asJsonObject)

    private fun convert(graph: String): GraphToApiConverter.ConversionResult =
        GraphToApiConverter.convert(graph.trimIndent(), objectInfo)

    private fun api(graph: String): JsonObject = JsonParser.parseString(convert(graph).json).asJsonObject

    private fun inputs(api: JsonObject, id: String) = api.getAsJsonObject(id).getAsJsonObject("inputs")

    private fun classTypes(api: JsonObject) = api.entrySet().map { it.value.asJsonObject.get("class_type").asString }

    private fun primitive(id: Int, widgets: String, extra: String = "") =
        """{"id": $id, "type": "PrimitiveNode", "mode": 0, "inputs": [], "outputs": [{"name": "INT", "type": "INT", "widget": {"name": "steps"}}],
            "widgets_values": $widgets $extra}"""

    private fun sampler(id: Int, stepsLink: Int?, seedLink: Int? = null, widgets: String = "[1, \"fixed\", 20, \"euler\"]") =
        """{"id": $id, "type": "Sampler", "mode": 0, "inputs": [
             {"name": "seed", "type": "INT", "widget": {"name": "seed"}, "link": ${seedLink ?: "null"}},
             {"name": "steps", "type": "INT", "widget": {"name": "steps"}, "link": ${stepsLink ?: "null"}}],
           "widgets_values": $widgets}"""

    @Test
    fun `PrimitiveNode value becomes the target literal and is not sent`() {
        val out = api("""{"nodes": [ ${primitive(1, "[30]")}, ${sampler(2, 10)} ], "links": [[10, 1, 0, 2, 1, "INT"]]}""")
        val i = inputs(out, "2")
        assertEquals(30, i.get("steps").asInt)
        assertEquals("euler", i.get("sampler_name").asString) // the linked widget's slot was still consumed
        assertFalse(classTypes(out).contains("PrimitiveNode"))
    }

    @Test
    fun `one PrimitiveNode feeds several targets`() {
        val out = api("""
            {"nodes": [ ${primitive(1, "[12]")}, ${sampler(2, 10)}, ${sampler(3, 11)} ],
             "links": [[10, 1, 0, 2, 1, "INT"], [11, 1, 0, 3, 1, "INT"]]}
        """)
        assertEquals(12, inputs(out, "2").get("steps").asInt)
        assertEquals(12, inputs(out, "3").get("steps").asInt)
    }

    @Test
    fun `PrimitiveNode with a control value sends its value`() {
        val out = api("""{"nodes": [ ${primitive(1, "[42, \"randomize\"]")}, ${sampler(2, null, 10)} ], "links": [[10, 1, 0, 2, 0, "INT"]]}""")
        val i = inputs(out, "2")
        assertEquals(42, i.get("seed").asInt)
        assertEquals(20, i.get("steps").asInt)
    }

    private fun reroute(id: Int, link: Int?) =
        """{"id": $id, "type": "Reroute", "mode": 0, "inputs": [{"name": "", "type": "*", "link": ${link ?: "null"}}],
            "outputs": [{"name": "", "type": "*"}]}"""

    @Test
    fun `PrimitiveNode behind a Reroute still becomes a literal`() {
        val out = api("""
            {"nodes": [ ${primitive(1, "[8]")}, ${reroute(5, 10)}, ${sampler(2, 11)} ],
             "links": [[10, 1, 0, 5, 0, "INT"], [11, 5, 0, 2, 1, "INT"]]}
        """)
        assertEquals(8, inputs(out, "2").get("steps").asInt)
        assertFalse(classTypes(out).contains("Reroute"))
    }

    private val vae = """{"id": 1, "type": "VaeLoader", "mode": 0, "inputs": [], "outputs": [{"name": "VAE", "type": "VAE"}], "widgets_values": []}"""

    private fun decode(id: Int, link: Int) =
        """{"id": $id, "type": "Decode", "mode": 0, "inputs": [{"name": "vae", "type": "VAE", "link": $link}], "widgets_values": []}"""

    @Test
    fun `Reroute passes through and an unconnected Reroute drops the link`() {
        val connected = api("""{"nodes": [ $vae, ${reroute(5, 10)}, ${decode(2, 11)} ], "links": [[10, 1, 0, 5, 0, "VAE"], [11, 5, 0, 2, 0, "VAE"]]}""")
        assertEquals("1", inputs(connected, "2").getAsJsonArray("vae")[0].asString)

        val loose = api("""{"nodes": [ ${reroute(5, null)}, ${decode(2, 11)} ], "links": [[11, 5, 0, 2, 0, "VAE"]]}""")
        assertFalse(inputs(loose, "2").has("vae"))
    }

    private fun setNode(id: Int, name: String, link: Int) =
        """{"id": $id, "type": "SetNode", "mode": 0, "inputs": [{"name": "VAE", "type": "VAE", "link": $link}],
            "outputs": [{"name": "*", "type": "*"}], "widgets_values": ["$name"]}"""

    private fun getNode(id: Int, name: String) =
        """{"id": $id, "type": "GetNode", "mode": 0, "inputs": [], "outputs": [{"name": "VAE", "type": "VAE"}], "widgets_values": ["$name"]}"""

    @Test
    fun `GetNode resolves through the SetNode with the same name`() {
        val result = convert("""
            {"nodes": [ $vae, ${setNode(3, "vae", 10)}, ${getNode(4, "vae")}, ${decode(2, 11)} ],
             "links": [[10, 1, 0, 3, 0, "VAE"], [11, 4, 0, 2, 0, "VAE"]]}
        """)
        val out = JsonParser.parseString(result.json).asJsonObject
        assertEquals("1", inputs(out, "2").getAsJsonArray("vae")[0].asString)
        assertFalse(classTypes(out).any { it == "SetNode" || it == "GetNode" })
        assertTrue(result.missingNodes.isEmpty())
    }

    @Test
    fun `GetNode without a matching SetNode drops the link`() {
        val out = api("""
            {"nodes": [ $vae, ${setNode(3, "vae", 10)}, ${getNode(4, "other")}, ${decode(2, 11)} ],
             "links": [[10, 1, 0, 3, 0, "VAE"], [11, 4, 0, 2, 0, "VAE"]]}
        """)
        assertFalse(inputs(out, "2").has("vae"))
    }

    @Test
    fun `notes are never sent or reported missing`() {
        val result = convert("""
            {"nodes": [ $vae,
              {"id": 7, "type": "Note", "mode": 0, "inputs": [], "outputs": [], "widgets_values": ["remember this"]},
              {"id": 8, "type": "MarkdownNote", "mode": 0, "inputs": [], "outputs": [], "widgets_values": ["# Title"]} ],
             "links": []}
        """)
        val out = JsonParser.parseString(result.json).asJsonObject
        assertEquals(listOf("VaeLoader"), classTypes(out))
        assertTrue(result.missingNodes.isEmpty())
    }

    @Test
    fun `socket fed by a promoted widget without a saved value gets the widget's value`() {
        // image_ernie_image shape: one subgraph input feeds IntNode.value (a widget) and Preview.source (a socket)
        val sg = """
            {"id": "SG", "name": "SG",
             "inputs": [{"id": "a", "name": "value", "type": "INT", "linkIds": [901, 902]}],
             "outputs": [],
             "nodes": [
               {"id": 20, "type": "IntNode", "inputs": [{"name": "value", "type": "INT", "widget": {"name": "value"}, "link": 901}],
                "outputs": [{"name": "INT", "type": "INT"}], "widgets_values": [5]},
               {"id": 21, "type": "Preview", "inputs": [{"name": "source", "type": "*", "link": 902}], "widgets_values": []}],
             "links": [
               {"id": 901, "origin_id": -10, "origin_slot": 0, "target_id": 20, "target_slot": 0, "type": "INT"},
               {"id": 902, "origin_id": -10, "origin_slot": 0, "target_id": 21, "target_slot": 0, "type": "INT"}]}
        """
        val out = api("""
            {"definitions": {"subgraphs": [$sg]},
             "nodes": [{"id": 2, "type": "SG", "mode": 0, "inputs": [], "widgets_values": [],
                        "properties": {"proxyWidgets": [["20", "value"]]}}],
             "links": []}
        """)
        val preview = out.entrySet().single { it.value.asJsonObject.get("class_type").asString == "Preview" }
        assertEquals(5, preview.value.asJsonObject.getAsJsonObject("inputs").get("source").asInt)
    }
}
