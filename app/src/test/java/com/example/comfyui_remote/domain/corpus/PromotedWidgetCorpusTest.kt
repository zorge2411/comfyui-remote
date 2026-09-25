package com.example.comfyui_remote.domain.corpus

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.example.comfyui_remote.domain.GraphToApiConverter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * image_mage_flow_t2i_int8 stores a different prompt and seed on its subgraph instance than inside the
 * subgraph; ComfyUI sends the instance's values (Phase 93), so the converter must too.
 */
class PromotedWidgetCorpusTest {

    private fun resource(path: String) =
        File(javaClass.classLoader!!.getResource("workflow-corpus/$path")!!.toURI()).readText()

    @Test
    fun `mage flow sends the prompt and seed shown on the subgraph instance`() {
        val graphText = resource("workflows/image_mage_flow_t2i_int8.json")
        val graph = JsonParser.parseString(graphText).asJsonObject
        val instance = graph.getAsJsonArray("nodes").map { it.asJsonObject }.single { it.get("id").asInt == 12 }
        val shown = instance.getAsJsonArray("widgets_values")

        val objectInfo = ComfyObjectInfo(JsonParser.parseString(resource("object_info.json")).asJsonObject)
        val api = JsonParser.parseString(GraphToApiConverter.convert(graphText, objectInfo).json).asJsonObject

        fun inputsOf(type: String): JsonObject = api.entrySet()
            .single { it.value.asJsonObject.get("class_type").asString == type }
            .value.asJsonObject.getAsJsonObject("inputs")

        assertEquals(shown[0].asString, inputsOf("TextEncodeMageFlowEdit").get("prompt").asString)
        assertEquals(shown[1].asLong, inputsOf("KSampler").get("seed").asLong)
    }
}
