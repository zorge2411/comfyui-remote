package com.example.comfyui_remote.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowModelsTest {

    @Test
    fun `finds checkpoints, gguf models and loras with their folders`() {
        val models = WorkflowModels.of("""
            {
              "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "SDXL/sd_xl_base.safetensors"}},
              "2": {"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": "z_image_turbo-Q8_0.gguf"}},
              "3": {"class_type": "LoraLoader", "inputs": {"lora_name": "detail.safetensors", "strength_model": 1.0, "model": ["1", 0]}},
              "4": {"class_type": "CLIPTextEncode", "inputs": {"text": "a photo of a cat"}}
            }
        """)
        assertEquals(
            listOf(
                ModelRef("checkpoints", "sd_xl_base"),
                ModelRef("diffusion_models", "z_image_turbo-Q8_0"),
                ModelRef("loras", "detail")
            ),
            models
        )
    }

    @Test
    fun `the same file used twice is listed once`() {
        val models = WorkflowModels.of("""
            {
              "1": {"class_type": "VAELoader", "inputs": {"vae_name": "ae.safetensors"}},
              "2": {"class_type": "VAELoader", "inputs": {"vae_name": "ae.safetensors"}}
            }
        """)
        assertEquals(listOf(ModelRef("vae", "ae")), models)
    }

    @Test
    fun `garbage gives no models`() {
        assertTrue(WorkflowModels.of("not json").isEmpty())
        assertTrue(WorkflowModels.of("[]").isEmpty())
    }
}
