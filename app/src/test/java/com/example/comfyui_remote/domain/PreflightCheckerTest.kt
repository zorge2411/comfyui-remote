package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.ApiPromptValidator.Options
import com.example.comfyui_remote.domain.PreflightIssueKind.CONVERSION
import com.example.comfyui_remote.domain.PreflightIssueKind.INVALID_VALUE
import com.example.comfyui_remote.domain.PreflightIssueKind.MISSING_FILE
import com.example.comfyui_remote.domain.PreflightIssueKind.MISSING_INPUT
import com.example.comfyui_remote.domain.PreflightIssueKind.MISSING_NODE_TYPE
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightCheckerTest {

    private val objectInfo = obj("""
        {
          "CheckpointLoaderSimple": { "input": { "required": {
              "ckpt_name": [["sd15.safetensors", "sdxl.safetensors"], {}] } },
            "output": ["MODEL", "CLIP", "VAE"] },
          "LoadImage": { "input": { "required": { "image": [["a.png"], {"image_upload": true}] } },
            "output": ["IMAGE", "MASK"] },
          "KSampler": { "input": { "required": {
              "model": ["MODEL", {}], "sampler_name": [["euler", "dpmpp_2m"], {}] } },
            "output": ["LATENT"] },
          "VAEDecode": { "input": { "required": { "samples": ["LATENT", {}], "vae": ["VAE", {}] } },
            "output": ["IMAGE"] }
        }
    """)

    private fun obj(s: String): JsonObject = JsonParser.parseString(s.trimIndent()).asJsonObject

    private fun loader(ckpt: String, title: String? = "Load Checkpoint") =
        """{"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "$ckpt"}""" +
            (if (title != null) """, "_meta": {"title": "$title"}}""" else "}")

    private fun sampler(extra: String = "") =
        """{"class_type": "KSampler", "inputs": {"model": ["1", 0], "sampler_name": "euler"$extra}}"""

    private fun check(prompt: String, options: Options) = PreflightChecker.check(prompt, objectInfo, options)

    private fun kinds(r: PreflightResult) = r.nodes.flatMap { n -> n.issues.map { it.kind } }

    @Test
    fun `missing model is reported with the available files, corpus mode ignores it`() {
        val prompt = """{"1": ${loader("flux.safetensors")}, "2": ${sampler()}}"""
        val r = check(prompt, Options.FORM)
        assertEquals(listOf(MISSING_FILE), kinds(r))
        val msg = r.nodes.single().issues.single().message
        assertTrue(msg, msg.contains("flux.safetensors isn't on the server"))
        assertTrue(msg, msg.contains("sd15.safetensors, sdxl.safetensors"))

        assertTrue(check(prompt, Options.CORPUS).isEmpty)
    }

    @Test
    fun `upload inputs are checked at queue time except just-uploaded files, and skipped on the form`() {
        val prompt = """{"5": {"class_type": "LoadImage", "inputs": {"image": "uploaded_123.png"}}}"""
        assertEquals(listOf(MISSING_FILE), kinds(check(prompt, Options.queue(emptyList()))))
        assertTrue(check(prompt, Options.queue(listOf("uploaded_123.png"))).isEmpty)
        assertTrue(check(prompt, Options.FORM).isEmpty)
    }

    @Test
    fun `unknown class type sorts first even with a larger id`() {
        val prompt = """{"1": ${loader("flux.safetensors")}, "2": ${sampler()}, "30": {"class_type": "FancyNode", "inputs": {}}}"""
        val r = check(prompt, Options.FORM)
        assertEquals(listOf("30", "1"), r.nodes.map { it.nodeId })
        assertEquals(MISSING_NODE_TYPE, r.nodes[0].issues.single().kind)
        assertEquals("Node type FancyNode isn't installed on the server", r.nodes[0].issues.single().message)
    }

    @Test
    fun `missing required input`() {
        val prompt = """{"1": ${loader("sd15.safetensors")}, "2": {"class_type": "KSampler", "inputs": {"sampler_name": "euler"}}}"""
        val r = check(prompt, Options.FORM)
        assertEquals(listOf(MISSING_INPUT), kinds(r))
        assertEquals("Required input model has no value or link", r.nodes.single().issues.single().message)
    }

    @Test
    fun `invalid non-file combo value`() {
        val prompt = """{"1": ${loader("sd15.safetensors")}, "2": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "sampler_name": "Euler A"}}}"""
        val r = check(prompt, Options.FORM)
        assertEquals(listOf(INVALID_VALUE), kinds(r))
        assertEquals("Euler A isn't a valid option for sampler_name", r.nodes.single().issues.single().message)
    }

    @Test
    fun `dangling link and type mismatch on one node give a single conversion issue`() {
        // samples links to missing node 9 (C2); vae takes the loader's CLIP output (C3).
        val prompt = """{"1": ${loader("sd15.safetensors")},
            "2": {"class_type": "VAEDecode", "inputs": {"samples": ["9", 0], "vae": ["1", 1]}}}"""
        val raw = ApiPromptValidator.validate(obj(prompt), objectInfo).map { it.check }
        assertEquals(listOf("C2", "C3"), raw)
        val r = check(prompt, Options.FORM)
        assertEquals(listOf(CONVERSION), kinds(r))
        assertEquals("VAEDecode", r.nodes.single().heading)
    }

    @Test
    fun `headings use the title when present`() {
        val titled = check("""{"1": ${loader("x.safetensors")}}""", Options.FORM)
        assertEquals("Load Checkpoint (CheckpointLoaderSimple)", titled.nodes.single().heading)
        val untitled = check("""{"1": ${loader("x.safetensors", title = null)}}""", Options.FORM)
        assertEquals("CheckpointLoaderSimple", untitled.nodes.single().heading)
    }

    @Test
    fun `summary counts kinds in display order, clean prompt is empty`() {
        val prompt = """{"1": ${loader("flux.safetensors")},
            "2": {"class_type": "KSampler", "inputs": {"sampler_name": "Euler A"}},
            "3": {"class_type": "FancyNode", "inputs": {}}, "4": {"class_type": "OtherNode", "inputs": {}}}"""
        assertEquals(
            "2 missing node types, 1 missing model or file, 1 invalid value, 1 missing input",
            check(prompt, Options.FORM).summary()
        )
        assertTrue(check("""{"1": ${loader("sd15.safetensors")}, "2": ${sampler()}}""", Options.FORM).isEmpty)
    }

    @Test
    fun `malformed prompt gives an empty result`() {
        assertTrue(check("not json {", Options.FORM).isEmpty)
        assertTrue(check("[1, 2]", Options.FORM).isEmpty)
    }

    @Test
    fun `no graph means no C6`() {
        val api = obj("""{"1": ${loader("sd15.safetensors")}}""")
        val graph = obj("""{"nodes": [{"id": 1, "mode": 4}]}""")
        assertEquals(listOf("C6"), ApiPromptValidator.validate(api, objectInfo, graph).map { it.check })
        assertTrue(ApiPromptValidator.validate(api, objectInfo).isEmpty())
        assertTrue(check(api.toString(), Options.FORM).isEmpty)
    }
}
