package com.example.comfyui_remote.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelSourceExtractorTest {

    private fun resource(path: String) =
        File(javaClass.classLoader!!.getResource("workflow-corpus/$path")!!.toURI()).readText()

    @Test
    fun `flux schnell lists its checkpoint`() {
        val sources = ModelSources.extract(resource("workflows/flux_schnell.json"))
        assertEquals(1, sources.size)
        assertEquals("flux1-schnell-fp8.safetensors", sources[0].name)
        assertEquals("checkpoints", sources[0].directory)
        assertTrue(sources[0].url.startsWith("https://huggingface.co/"))
    }

    @Test
    fun `models inside subgraph definitions are found`() {
        val names = ModelSources.extract(resource("workflows/image_mage_flow_t2i_int8.json")).map { it.name }
        assertEquals(
            listOf("mage_flow_int8_convrot.safetensors", "qwen3vl_4b_bf16.safetensors", "mage_flow_vae_bf16.safetensors"),
            names
        )
    }

    @Test
    fun `top-level list, duplicates and incomplete entries`() {
        val graph = """
            {"nodes":[
              {"id":1,"properties":{"models":[
                {"name":"a.safetensors","url":"https://huggingface.co/x/a.safetensors","directory":"vae"},
                {"name":"b.safetensors","directory":"vae"}
              ]}},
              {"id":2,"properties":{"models":[
                {"name":"a.safetensors","url":"https://huggingface.co/y/a.safetensors","directory":"vae"}
              ]}}
            ],
            "models":[{"name":"c.safetensors","url":"https://huggingface.co/x/c.safetensors","directory":"loras"}]}
        """
        val sources = ModelSources.extract(graph)
        assertEquals(listOf("vae/a.safetensors", "loras/c.safetensors"), sources.map { "${it.directory}/${it.name}" })
        assertEquals("https://huggingface.co/x/a.safetensors", sources[0].url)
    }

    @Test
    fun `invalid json gives no sources`() {
        assertEquals(emptyList<ModelSource>(), ModelSources.extract("not json"))
        assertEquals(emptyList<ModelSource>(), ModelSources.fromJson("not json"))
        assertEquals(emptyList<ModelSource>(), ModelSources.fromJson(null))
    }

    @Test
    fun `usedBy keeps only models the prompt uses`() {
        val sources = listOf(
            ModelSource("used.safetensors", "https://huggingface.co/u", "checkpoints"),
            ModelSource("unused.safetensors", "https://huggingface.co/n", "loras")
        )
        val prompt = """{"1":{"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"sub\\used.safetensors","x":3}}}"""
        assertEquals(listOf("used.safetensors"), ModelSources.usedBy(sources, prompt).map { it.name })
    }

    @Test
    fun `missing matches subfolder entries and skips unknown folders`() {
        val sources = listOf(
            ModelSource("have.safetensors", "u1", "checkpoints"),
            ModelSource("lack.safetensors", "u2", "checkpoints"),
            ModelSource("unknown.safetensors", "u3", "vae")
        )
        val available = mapOf("checkpoints" to listOf("flux/have.safetensors", "other.safetensors"))
        assertEquals(listOf("lack.safetensors"), ModelSources.missing(sources, available).map { it.name })
    }

    @Test
    fun `json round trip`() {
        val sources = listOf(ModelSource("a.safetensors", "https://huggingface.co/a", "vae"))
        assertEquals(sources, ModelSources.fromJson(ModelSources.toJson(sources)))
    }
}
