package com.example.comfyui_remote.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Missing models found from the prompt and /object_info, for workflows without download links. */
class PromptModelsTest {

    private val objectInfo = JsonParser.parseString(
        """
        {
          "UNETLoader": {"input": {"required": {
            "unet_name": [["flux1-dev.safetensors", "sub/krea2_raw_bf16.safetensors"]],
            "weight_dtype": [["default", "fp8_e4m3fn"]]}}},
          "CLIPLoader": {"input": {"required": {
            "clip_name": [[]], "type": [["krea2", "flux"]]}}},
          "VAELoader": {"input": {"required": {"vae_name": [["ae.safetensors", "pixel_space"]]}}},
          "LoraLoaderModelOnly": {"input": {"required": {
            "lora_name": ["COMBO", {"options": ["a.safetensors"]}], "strength_model": ["FLOAT", {}]}}},
          "OddLoader": {"input": {"required": {"weights": [["x.safetensors"]]}}}
        }
        """
    ).asJsonObject

    private val prompt = """
        {
          "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "krea2_raw_bf16.safetensors", "weight_dtype": "default"}},
          "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl_4b_fp8_scaled.safetensors", "type": "krea2"}},
          "3": {"class_type": "VAELoader", "inputs": {"vae_name": "pixel_space"}},
          "4": {"class_type": "LoraLoaderModelOnly", "inputs": {"lora_name": "turbo.safetensors", "model": ["1", 0]}},
          "5": {"class_type": "OddLoader", "inputs": {"weights": "y.safetensors"}},
          "6": {"class_type": "NotInstalled", "inputs": {"ckpt_name": "z.safetensors"}}
        }
    """

    @Test
    fun `reports model values the server doesn't offer, with their folders`() {
        val missing = ModelSources.missingInPrompt(prompt, objectInfo)
        assertEquals(
            listOf(
                "qwen3vl_4b_fp8_scaled.safetensors" to "text_encoders",
                "turbo.safetensors" to "loras",
                "y.safetensors" to null
            ),
            missing.map { it.name to it.directory }
        )
    }

    @Test
    fun `a model in a subfolder, a non-file value and a missing node type are not reported`() {
        val names = ModelSources.missingInPrompt(prompt, objectInfo).map { it.name }
        assertTrue("krea2_raw_bf16.safetensors" !in names)
        assertTrue("pixel_space" !in names)
        assertTrue("z.safetensors" !in names)
    }

    @Test
    fun `the form's current choice replaces the prompt value`() {
        val names = ModelSources.missingInPrompt(prompt, objectInfo, mapOf("4/lora_name" to "a.safetensors")).map { it.name }
        assertTrue("turbo.safetensors" !in names)
    }

    @Test
    fun `an unknown input's folder is guessed from the server's file lists`() {
        val folders = mapOf("checkpoints" to listOf("c.safetensors"), "vae" to listOf("x.safetensors", "ae.safetensors"))
        assertEquals("vae", ModelSources.guessFolder(listOf("x.safetensors"), folders))
        assertNull(ModelSources.guessFolder(listOf("nothing.safetensors"), folders))
        assertNull(ModelSources.guessFolder(emptyList(), folders))
    }

    @Test
    fun `folder names follow the loader input`() {
        assertEquals("checkpoints", ModelSources.folderFor("CheckpointLoaderSimple", "ckpt_name"))
        assertEquals("diffusion_models", ModelSources.folderFor("OTUNetLoaderW8A8", "unet_name"))
        assertEquals("text_encoders", ModelSources.folderFor("DualCLIPLoader", "clip_name2"))
        assertEquals("clip_vision", ModelSources.folderFor("CLIPVisionLoader", "clip_name"))
        assertEquals("upscale_models", ModelSources.folderFor("UpscaleModelLoader", "model_name"))
        assertEquals("loras", ModelSources.folderFor("easy loraStack", "lora_1_name"))
        assertNull(ModelSources.folderFor("OddLoader", "weights"))
    }

    @Test
    fun `pasted links become download links on allowed hosts only`() {
        assertEquals(
            "https://huggingface.co/org/repo/resolve/main/dir/m.safetensors",
            ModelSources.normalizeUrl(" https://huggingface.co/org/repo/blob/main/dir/m.safetensors ")
        )
        assertEquals(
            "https://huggingface.co/org/repo/resolve/main/m.safetensors?download=true",
            ModelSources.normalizeUrl("https://huggingface.co/org/repo/resolve/main/m.safetensors?download=true")
        )
        assertEquals(
            "https://github.com/o/r/raw/main/m.pth",
            ModelSources.normalizeUrl("https://github.com/o/r/blob/main/m.pth")
        )
        assertNull(ModelSources.normalizeUrl("http://huggingface.co/org/repo/resolve/main/m.safetensors"))
        assertNull(ModelSources.normalizeUrl("https://civitai.com/api/download/models/1"))
        assertNull(ModelSources.normalizeUrl("not a link"))
    }

    @Test
    fun `a saved link replaces an earlier one for the same model`() {
        val old = ModelSource("m.safetensors", "https://huggingface.co/a", "loras")
        val other = ModelSource("n.safetensors", "https://huggingface.co/b", "vae")
        val new = old.copy(url = "https://huggingface.co/c")
        assertEquals(listOf(other, new), ModelSources.withSource(listOf(old, other), new))
        assertTrue(!ModelSource("m", "", "loras").hasLink)
    }
}
