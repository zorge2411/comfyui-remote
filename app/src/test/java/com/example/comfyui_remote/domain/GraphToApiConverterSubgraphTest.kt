package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GraphToApiConverterSubgraphTest {

    @Test
    fun parseSubgraphDefinitions_ValidJson_ReturnsDefinitions() {
        val graphJson = """
            {
                "definitions": {
                    "subgraphs": [
                        {
                            "id": "TestSubgraph",
                            "name": "My Custom Subgraph",
                            "inputs": [
                                { "id": "input_1", "name": "clip", "type": "CLIP", "linkIds": [101] }
                            ],
                            "outputs": [
                                { "id": "output_1", "name": "image", "type": "IMAGE", "linkIds": [102] }
                            ],
                            "nodes": [
                                { "id": 1, "type": "CLIPTextEncode", "inputs": [{"name": "clip", "type": "CLIP", "link": 101}] },
                                { "id": 2, "type": "SaveImage", "inputs": [{"name": "images", "type": "IMAGE", "link": 102}] }
                            ],
                            "links": [
                                { "id": 101, "origin_id": -10, "origin_slot": 0, "target_id": 1, "target_slot": 0 },
                                { "id": 102, "origin_id": 2, "origin_slot": 0, "target_id": -20, "target_slot": 0 }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()
        
        val graph = JsonParser.parseString(graphJson).asJsonObject
        val definitions = GraphToApiConverter.parseSubgraphDefinitions(graph)
        
        assertEquals(1, definitions.size)
        assertTrue(definitions.containsKey("TestSubgraph"))
        val def = definitions["TestSubgraph"]!!
        assertEquals("My Custom Subgraph", def.name)
        assertEquals(1, def.inputs.size)
        assertEquals("clip", def.inputs[0].name)
        assertEquals(1, def.outputs.size)
        assertEquals("image", def.outputs[0].name)
        assertEquals(2, def.nodes.size)
        assertEquals(2, def.links.size)
    }

    @Test
    fun expandGraph_SimpleSubgraph_ExpandsCorrectly() {
        // 1. Definition for a simple "Inverter" subgraph that takes an image and saves it
        val graphJson = """
            {
                "definitions": {
                    "subgraphs": [
                        {
                            "id": "SimpleIngester",
                            "nodes": [
                                { "id": 100, "type": "UpscaleImage", "inputs": [{"name": "image", "link": 1001}] }
                            ],
                            "links": [
                                { "id": 1001, "origin_id": -10, "origin_slot": 0, "target_id": 100, "target_slot": 0 }
                            ]
                        }
                    ]
                },
                "nodes": [
                    { "id": 1, "type": "LoadImage", "widgets_values": ["test.png"] },
                    { 
                        "id": 2, 
                        "type": "SimpleIngester", 
                        "properties": { "proxyWidgets": true },
                        "inputs": [ { "name": "image", "link": 50 } ]
                    }
                ],
                "links": [
                    [50, 1, 0, 2, 0, "IMAGE"]
                ]
            }
        """.trimIndent()

        val graph = JsonParser.parseString(graphJson).asJsonObject
        val definitions = GraphToApiConverter.parseSubgraphDefinitions(graph)
        
        val expandedGraph = GraphToApiConverter.expandGraph(graph, definitions)
        val nodes = expandedGraph.getAsJsonArray("nodes")
        val links = expandedGraph.getAsJsonArray("links")

        // Should have 2 nodes: LoadImage (1) and UpscaleImage (remapped from 100)
        assertEquals(2, nodes.size())
        
        val loadImage = nodes.first { it.asJsonObject.get("id").asInt == 1 }.asJsonObject
        val upscaleImage = nodes.first { it.asJsonObject.get("id").asInt != 1 }.asJsonObject
        
        assertEquals("LoadImage", loadImage.get("type").asString)
        assertEquals("UpscaleImage", upscaleImage.get("type").asString)

        // Check links
        // Original link 50: [1, 0] -> [2, 0]
        // Should be remapped to point to the UpscaleImage node
        val remappedUpscaleId = upscaleImage.get("id").asInt
        
        val finalLink = links.first { it.asJsonArray[1].asInt == 1 }.asJsonArray
        assertEquals(1, finalLink[1].asInt) // Source LoadImage
        assertEquals(0, finalLink[2].asInt) // Source Slot 0
        assertEquals(remappedUpscaleId, finalLink[3].asInt) // Target UpscaleImage
        assertEquals(0, finalLink[4].asInt) // Target Slot 0
    }

    @Test
    fun expandGraph_SubgraphWithoutProxyWidgets_StillExpandsCorrectly() {
        // Same as expandGraph_SimpleSubgraph_ExpandsCorrectly, but the wrapper node has NO
        // "proxyWidgets" property at all (properties is present but empty) — regression test
        // for the bug where isSubgraph detection wrongly required proxyWidgets in addition to
        // a matching definition, causing real subgraph instances to be skipped and fall through
        // to the crude phantom-flattening heuristic instead of proper expansion.
        val graphJson = """
            {
                "definitions": {
                    "subgraphs": [
                        {
                            "id": "SimpleIngester",
                            "nodes": [
                                { "id": 100, "type": "UpscaleImage", "inputs": [{"name": "image", "link": 1001}] }
                            ],
                            "links": [
                                { "id": 1001, "origin_id": -10, "origin_slot": 0, "target_id": 100, "target_slot": 0 }
                            ]
                        }
                    ]
                },
                "nodes": [
                    { "id": 1, "type": "LoadImage", "widgets_values": ["test.png"] },
                    {
                        "id": 2,
                        "type": "SimpleIngester",
                        "properties": {},
                        "inputs": [ { "name": "image", "link": 50 } ]
                    }
                ],
                "links": [
                    [50, 1, 0, 2, 0, "IMAGE"]
                ]
            }
        """.trimIndent()

        val graph = JsonParser.parseString(graphJson).asJsonObject
        val definitions = GraphToApiConverter.parseSubgraphDefinitions(graph)

        val expandedGraph = GraphToApiConverter.expandGraph(graph, definitions)
        val nodes = expandedGraph.getAsJsonArray("nodes")

        // Should have 2 nodes: LoadImage (1) and UpscaleImage (remapped from 100) — proving
        // the wrapper node (id 2, no proxyWidgets) was still expanded, not left as an opaque
        // unexpanded node.
        assertEquals(2, nodes.size())
        assertTrue("SimpleIngester wrapper should be gone", nodes.none { it.asJsonObject.get("id").asInt == 2 })

        val loadImage = nodes.first { it.asJsonObject.get("id").asInt == 1 }.asJsonObject
        val upscaleImage = nodes.first { it.asJsonObject.get("id").asInt != 1 }.asJsonObject

        assertEquals("LoadImage", loadImage.get("type").asString)
        assertEquals("UpscaleImage", upscaleImage.get("type").asString)
    }

    @Test
    fun convert_SubgraphWithoutProxyWidgets_ResolvesToInternalProducer_NotFirstInput() {
        // Reproduces the shape of the real bug (image-to-video subgraph, HTTP 400 type
        // mismatch) with synthetic node/type names — no real workflow content.
        //
        // Subgraph "VideoMaker" has TWO external inputs (image, seed) feeding two DIFFERENT
        // internal nodes. The internal node wired to the subgraph's FIRST input (image ->
        // PassThroughImage) does NOT produce the subgraph's declared output — a SEPARATE
        // internal node (seed -> VideoProducer) does. A downstream consumer (SaveVideoTarget)
        // reads the subgraph's output.
        //
        // Before the fix: if isSubgraph detection failed (no proxyWidgets), convert()'s phantom
        // flattening would blindly resolve the consumer's link to input index 0 of the wrapper
        // (ImageSource, wrong). After the fix: proper expansion correctly resolves it to
        // VideoProducer's output.
        val graphJson = """
            {
                "definitions": {
                    "subgraphs": [
                        {
                            "id": "VideoMaker",
                            "name": "Video Maker",
                            "inputs": [
                                { "id": "in1", "name": "image", "type": "IMAGE", "linkIds": [301] },
                                { "id": "in2", "name": "seed", "type": "INT", "linkIds": [302] }
                            ],
                            "outputs": [
                                { "id": "out1", "name": "VIDEO", "type": "VIDEO", "linkIds": [303] }
                            ],
                            "nodes": [
                                { "id": 10, "type": "PassThroughImage", "inputs": [{"name": "image", "link": 301}] },
                                { "id": 20, "type": "VideoProducer", "inputs": [{"name": "seed", "link": 302}] }
                            ],
                            "links": [
                                { "id": 301, "origin_id": -10, "origin_slot": 0, "target_id": 10, "target_slot": 0, "type": "IMAGE" },
                                { "id": 302, "origin_id": -10, "origin_slot": 1, "target_id": 20, "target_slot": 0, "type": "INT" },
                                { "id": 303, "origin_id": 20, "origin_slot": 0, "target_id": -20, "target_slot": 0, "type": "VIDEO" }
                            ]
                        }
                    ]
                },
                "nodes": [
                    { "id": "1", "type": "ImageSource", "inputs": [] },
                    { "id": "2", "type": "SeedSource", "inputs": [] },
                    { "id": "3", "type": "VideoMaker", "properties": {}, "inputs": [
                        {"name": "image", "type": "IMAGE", "link": 50},
                        {"name": "seed", "type": "INT", "link": 51}
                    ]},
                    { "id": "4", "type": "SaveVideoTarget", "inputs": [
                        {"name": "video", "type": "VIDEO", "link": 52}
                    ]}
                ],
                "links": [
                    [50, 1, 0, 3, 0, "IMAGE"],
                    [51, 2, 0, 3, 1, "INT"],
                    [52, 3, 0, 4, 0, "VIDEO"]
                ]
            }
        """.trimIndent()

        val metaJson = """
            {
                "ImageSource": { "input": {"required":{}}, "output": ["IMAGE"] },
                "SeedSource": { "input": {"required":{}}, "output": ["INT"] },
                "PassThroughImage": { "input": {"required":{"image":["IMAGE"]}}, "output": [] },
                "VideoProducer": { "input": {"required":{"seed":["INT"]}}, "output": ["VIDEO"] },
                "SaveVideoTarget": { "input": {"required":{"video":["VIDEO"]}}, "output": [] }
            }
        """
        val objectInfo = ComfyObjectInfo(JsonParser.parseString(metaJson).asJsonObject)

        val result = GraphToApiConverter.convert(graphJson, objectInfo)
        val json = JsonParser.parseString(result.json).asJsonObject

        assertFalse("Wrapper node 3 (VideoMaker) should be gone (expanded)", json.has("3"))
        assertTrue("Output should contain SaveVideoTarget (node 4)", json.has("4"))

        val videoInput = json.getAsJsonObject("4").getAsJsonObject("inputs").getAsJsonArray("video")
        val resolvedSourceId = videoInput.get(0).asString

        // The resolved source must be the expanded VideoProducer node, not node "1" (ImageSource) —
        // node "1" is what the old blind-first-input heuristic would have wrongly picked.
        assertNotEquals("Must not resolve to ImageSource (the bug)", "1", resolvedSourceId)
        val resolvedNode = json.getAsJsonObject(resolvedSourceId)
        assertEquals("VideoProducer", resolvedNode.get("class_type").asString)
    }

    // --- Phase 93: instance inputs bind to subgraph inputs by name (+type), not by position ---

    private val inputsObjectInfo = ComfyObjectInfo(JsonParser.parseString("""
        {
          "IntSrc": { "input": {"required": {}}, "output": ["INT"] },
          "IntSrc2": { "input": {"required": {}}, "output": ["INT"] },
          "FloatSrc": { "input": {"required": {}}, "output": ["FLOAT"] },
          "TextEnc": { "input": {"required": {"text": ["STRING", {}]}}, "output": ["CONDITIONING"] },
          "Latent": { "input": {"required": {"width": ["INT", {}], "height": ["INT", {}]}}, "output": ["LATENT"] },
          "Scale": { "input": {"required": {"value": ["FLOAT", {}]}}, "output": ["FLOAT"] },
          "Count": { "input": {"required": {"value": ["INT", {}]}}, "output": ["INT"] }
        }
    """.trimIndent()).asJsonObject)

    /** Subgraph "SG": inputs text(STRING)->TextEnc(10).text, width(INT)->Latent(11).width, height(INT)->Latent(11).height. */
    private val textSizeSubgraph = """
        {"id": "SG", "name": "SG",
         "inputs": [
           {"id": "i0", "name": "text", "type": "STRING", "linkIds": [901]},
           {"id": "i1", "name": "width", "type": "INT", "linkIds": [902]},
           {"id": "i2", "name": "height", "type": "INT", "linkIds": [903]}],
         "outputs": [],
         "nodes": [
           {"id": 10, "type": "TextEnc", "inputs": [{"name": "text", "type": "STRING", "widget": {"name": "text"}, "link": 901}], "widgets_values": ["interior text"]},
           {"id": 11, "type": "Latent", "inputs": [
              {"name": "width", "type": "INT", "widget": {"name": "width"}, "link": 902},
              {"name": "height", "type": "INT", "widget": {"name": "height"}, "link": 903}], "widgets_values": [512, 512]}],
         "links": [
           {"id": 901, "origin_id": -10, "origin_slot": 0, "target_id": 10, "target_slot": 0, "type": "STRING"},
           {"id": 902, "origin_id": -10, "origin_slot": 1, "target_id": 11, "target_slot": 0, "type": "INT"},
           {"id": 903, "origin_id": -10, "origin_slot": 2, "target_id": 11, "target_slot": 1, "type": "INT"}]}
    """

    private fun convertApi(graph: String) =
        JsonParser.parseString(GraphToApiConverter.convert(graph.trimIndent(), inputsObjectInfo).json).asJsonObject

    private fun nodeOfType(api: com.google.gson.JsonObject, type: String) =
        api.entrySet().single { it.value.asJsonObject.get("class_type").asString == type }.value.asJsonObject.getAsJsonObject("inputs")

    @Test
    fun `instance listing a subset of inputs links by name`() {
        val api = convertApi("""
            {"definitions": {"subgraphs": [$textSizeSubgraph]},
             "nodes": [
               {"id": 1, "type": "IntSrc", "outputs": [{"name": "INT", "type": "INT"}]},
               {"id": 2, "type": "SG", "inputs": [{"name": "width", "type": "INT", "widget": {"name": "width"}, "link": 50}]}],
             "links": [[50, 1, 0, 2, 0, "INT"]]}
        """)
        assertEquals("1", nodeOfType(api, "Latent").getAsJsonArray("width")[0].asString)
        assertEquals("interior text", nodeOfType(api, "TextEnc").get("text").asString)
    }

    @Test
    fun `reordered instance inputs link by name`() {
        val api = convertApi("""
            {"definitions": {"subgraphs": [$textSizeSubgraph]},
             "nodes": [
               {"id": 1, "type": "IntSrc", "outputs": [{"name": "INT", "type": "INT"}]},
               {"id": 3, "type": "IntSrc2", "outputs": [{"name": "INT", "type": "INT"}]},
               {"id": 2, "type": "SG", "inputs": [
                  {"name": "height", "type": "INT", "widget": {"name": "height"}, "link": 51},
                  {"name": "width", "type": "INT", "widget": {"name": "width"}, "link": 50}]}],
             "links": [[50, 1, 0, 2, 1, "INT"], [51, 3, 0, 2, 0, "INT"]]}
        """)
        val latent = nodeOfType(api, "Latent")
        assertEquals("1", latent.getAsJsonArray("width")[0].asString)
        assertEquals("3", latent.getAsJsonArray("height")[0].asString)
    }

    @Test
    fun `duplicate input names are told apart by type`() {
        val sg = """
            {"id": "SG2", "name": "SG2",
             "inputs": [
               {"id": "a", "name": "value", "type": "FLOAT", "linkIds": [911]},
               {"id": "b", "name": "value", "type": "INT", "linkIds": [912]}],
             "outputs": [],
             "nodes": [
               {"id": 20, "type": "Scale", "inputs": [{"name": "value", "type": "FLOAT", "widget": {"name": "value"}, "link": 911}], "widgets_values": [1.5]},
               {"id": 21, "type": "Count", "inputs": [{"name": "value", "type": "INT", "widget": {"name": "value"}, "link": 912}], "widgets_values": [3]}],
             "links": [
               {"id": 911, "origin_id": -10, "origin_slot": 0, "target_id": 20, "target_slot": 0, "type": "FLOAT"},
               {"id": 912, "origin_id": -10, "origin_slot": 1, "target_id": 21, "target_slot": 0, "type": "INT"}]}
        """
        val api = convertApi("""
            {"definitions": {"subgraphs": [$sg]},
             "nodes": [
               {"id": 1, "type": "IntSrc", "outputs": [{"name": "INT", "type": "INT"}]},
               {"id": 2, "type": "SG2", "inputs": [{"name": "value", "type": "INT", "link": 50}]}],
             "links": [[50, 1, 0, 2, 0, "INT"]]}
        """)
        assertEquals("1", nodeOfType(api, "Count").getAsJsonArray("value")[0].asString)
        assertEquals(1.5, nodeOfType(api, "Scale").get("value").asDouble, 0.0)
    }

    @Test
    fun `instance input missing from the subgraph is ignored`() {
        val api = convertApi("""
            {"definitions": {"subgraphs": [$textSizeSubgraph]},
             "nodes": [
               {"id": 1, "type": "IntSrc", "outputs": [{"name": "INT", "type": "INT"}]},
               {"id": 2, "type": "SG", "inputs": [{"name": "depth", "type": "INT", "link": 50}]}],
             "links": [[50, 1, 0, 2, 0, "INT"]]}
        """)
        val latent = nodeOfType(api, "Latent")
        assertEquals(512, latent.get("width").asInt)
        assertEquals("interior text", nodeOfType(api, "TextEnc").get("text").asString)
    }

    @Test
    fun `wrapper output feeds a subset input of another wrapper by name`() {
        val producer = """
            {"id": "P", "name": "P", "inputs": [],
             "outputs": [{"id": "o0", "name": "INT", "type": "INT", "linkIds": [921]}],
             "nodes": [{"id": 30, "type": "IntSrc", "outputs": [{"name": "INT", "type": "INT"}]}],
             "links": [{"id": 921, "origin_id": 30, "origin_slot": 0, "target_id": -20, "target_slot": 0, "type": "INT"}]}
        """
        val api = convertApi("""
            {"definitions": {"subgraphs": [$textSizeSubgraph, $producer]},
             "nodes": [
               {"id": 1, "type": "P", "outputs": [{"name": "INT", "type": "INT"}]},
               {"id": 2, "type": "SG", "inputs": [{"name": "height", "type": "INT", "widget": {"name": "height"}, "link": 50}]}],
             "links": [[50, 1, 0, 2, 0, "INT"]]}
        """)
        val latent = nodeOfType(api, "Latent")
        val source = latent.getAsJsonArray("height")[0].asString
        assertEquals("IntSrc", api.getAsJsonObject(source).get("class_type").asString)
        assertEquals(512, latent.get("width").asInt)
    }
}
