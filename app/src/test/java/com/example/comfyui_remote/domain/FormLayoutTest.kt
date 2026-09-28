package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.InputField.FloatInput
import com.example.comfyui_remote.domain.InputField.IntInput
import com.example.comfyui_remote.domain.InputField.SeedInput
import com.example.comfyui_remote.domain.InputField.SelectionInput
import com.example.comfyui_remote.domain.InputField.StringInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormLayoutTest {

    // The shape of the Z-Image-Turbo template after parsing (Phase 95 device check)
    private val zImage = listOf(
        StringInput("9", "filename_prefix", "z-image-turbo", "Save Image"),
        SelectionInput("63", "clip_name", "qwen_3_4b.safetensors", listOf("qwen_3_4b.safetensors"), "Load CLIP"),
        SelectionInput("63", "type", "lumina2", listOf("lumina2"), "Load CLIP"),
        SelectionInput("64", "vae_name", "ae.safetensors", listOf("ae.safetensors"), "Load VAE"),
        SelectionInput("67", "unet_name", "z_image_turbo_bf16.safetensors", listOf("z_image_turbo_bf16.safetensors"), "Load Diffusion Model"),
        StringInput("68", "text", "a harbour at dusk", "CLIP Text Encode"),
        IntInput("69", "width", 1024, "Empty Latent"),
        IntInput("69", "height", 1024, "Empty Latent"),
        IntInput("69", "batch_size", 1, "Empty Latent"),
        FloatInput("70", "shift", 3f, "ModelSamplingAuraFlow"),
        SeedInput("71", "seed", 0, "KSampler"),
        IntInput("71", "steps", 8, "KSampler"),
        FloatInput("71", "cfg", 1f, "KSampler"),
        SelectionInput("71", "sampler_name", "res_multistep", listOf("euler", "res_multistep"), "KSampler"),
        SelectionInput("71", "scheduler", "simple", listOf("simple"), "KSampler"),
        FloatInput("71", "denoise", 1f, "KSampler")
    )

    @Test
    fun `z-image-turbo puts the prompt and key settings first`() {
        val model = FormLayout.build(zImage, positiveNodeId = "68", negativeNodeId = null)
        assertEquals("68/text", model.prompt?.key)
        assertEquals("Prompt", model.prompt?.label)
        assertNull(model.negative)
        assertEquals(
            listOf("Seed", "Width", "Height", "Steps", "CFG", "Sampler", "Scheduler", "Denoise", "Batch size", "Model"),
            model.main.map { it.label }
        )
        assertEquals(listOf("9", "63", "64", "70"), model.groups.map { it.nodeId })
        assertEquals("Load CLIP", model.groups[1].title)
        assertEquals(listOf("Clip name", "Type"), model.groups[1].fields.map { it.label })
        // every input appears exactly once
        val shown = listOfNotNull(model.prompt, model.negative) + model.main + model.groups.flatMap { it.fields }
        assertEquals(zImage.size, shown.size)
        assertEquals(zImage.map { it.key }.toSet(), shown.map { it.key }.toSet())
    }

    @Test
    fun `ambiguous roles stay with their nodes`() {
        val twoSamplers = zImage + listOf(
            SeedInput("80", "seed", 1, "KSampler (refine)"),
            IntInput("80", "steps", 4, "KSampler (refine)")
        )
        val model = FormLayout.build(twoSamplers, "68", null)
        val mainLabels = model.main.map { it.label }
        assert("Seed" !in mainLabels && "Steps" !in mainLabels) { mainLabels.toString() }
        assert("CFG" in mainLabels) { mainLabels.toString() } // still unique
        assertEquals(listOf("Seed", "Steps"), model.groups.single { it.nodeId == "71" }.fields.map { it.label })
        assertEquals(listOf("Seed", "Steps"), model.groups.single { it.nodeId == "80" }.fields.map { it.label })
    }

    @Test
    fun `without prompt ids nothing is hoisted`() {
        val model = FormLayout.build(zImage, null, null)
        assertNull(model.prompt)
        assert(model.groups.any { it.nodeId == "68" })
    }

    @Test
    fun `negative prompt comes from the sampler's negative link`() {
        val api = """
            {
              "1": {"class_type": "CLIPTextEncode", "inputs": {"text": "a cat", "clip": ["4", 1]}, "_meta": {"title": "Positive"}},
              "2": {"class_type": "CLIPTextEncode", "inputs": {"text": "blurry", "clip": ["4", 1]}, "_meta": {"title": "Negative"}},
              "3": {"class_type": "KSampler", "inputs": {"seed": 5, "steps": 20, "positive": ["1", 0], "negative": ["2", 0], "model": ["4", 0]}},
              "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "sd.safetensors"}}
            }
        """.trimIndent()
        val parser = WorkflowParser()
        val inputs = parser.parse(api)
        val model = FormLayout.build(inputs, parser.findPositivePromptNodeId(api), parser.findNegativePromptNodeId(api))
        assertEquals("1/text", model.prompt?.key)
        assertEquals("2/text", model.negative?.key)
        assertEquals("Negative prompt", model.negative?.label)
        assertEquals(listOf("Seed", "Steps", "Checkpoint"), model.main.map { it.label })
    }

    @Test
    fun `humanize field names`() {
        assertEquals("Filename prefix", FormLayout.humanize("filename_prefix"))
        assertEquals("Format › codec", FormLayout.humanize("format.codec"))
        assertEquals("Cfg", FormLayout.humanize("cfg"))
    }
}
