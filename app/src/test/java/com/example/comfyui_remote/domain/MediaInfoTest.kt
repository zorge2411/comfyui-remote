package com.example.comfyui_remote.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInfoTest {

    private val parser = WorkflowParser()

    // Z-Image-Turbo shape, as stored from the server's /history (API prompt)
    private val prompt = """
        {
          "3": {"class_type": "KSampler", "_meta": {"title": "KSampler"}, "inputs": {
            "seed": 12345, "steps": 8, "cfg": 1.0, "sampler_name": "res_multistep", "scheduler": "simple",
            "denoise": 1.0, "model": ["11", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["13", 0]}},
          "6": {"class_type": "CLIPTextEncode", "_meta": {"title": "Prompt"}, "inputs": {"text": "a red fox in snow", "clip": ["18", 0]}},
          "7": {"class_type": "CLIPTextEncode", "_meta": {"title": "Negative"}, "inputs": {"text": "blurry", "clip": ["18", 0]}},
          "11": {"class_type": "UNETLoader", "_meta": {"title": "Load Diffusion Model"}, "inputs": {"unet_name": "z/z_image_turbo_bf16.safetensors", "weight_dtype": "default"}},
          "13": {"class_type": "EmptySD3LatentImage", "_meta": {"title": "Empty Latent"}, "inputs": {"width": 1024, "height": 768, "batch_size": 1}},
          "18": {"class_type": "CLIPLoader", "_meta": {"title": "Load CLIP"}, "inputs": {"clip_name": "qwen_3_4b.safetensors", "type": "lumina2"}},
          "9": {"class_type": "SaveImage", "_meta": {"title": "Save Image"}, "inputs": {"filename_prefix": "z", "images": ["8", 0]}}
        }
    """

    @Test
    fun `prompt, negative and main settings come from the stored prompt`() {
        val info = MediaInfo.from(prompt, parser)
        assertEquals("a red fox in snow", info.prompt)
        assertEquals("blurry", info.negative)
        val settings = info.settings.toMap()
        assertEquals("12345", settings["Seed"])
        assertEquals("1024", settings["Width"])
        assertEquals("768", settings["Height"])
        assertEquals("8", settings["Steps"])
        assertEquals("1", settings["CFG"])
        assertEquals("z_image_turbo_bf16.safetensors", settings["Model"])
    }

    @Test
    fun `no or unreadable prompt gives nothing`() {
        assertTrue(MediaInfo.from(null, parser).isEmpty)
        assertTrue(MediaInfo.from("", parser).isEmpty)
        assertTrue(MediaInfo.from("not json", parser).isEmpty)
        assertNull(MediaInfo.from("{}", parser).prompt)
    }
}
