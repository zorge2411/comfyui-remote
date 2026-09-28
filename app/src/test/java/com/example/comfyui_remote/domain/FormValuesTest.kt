package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.InputField.FloatInput
import com.example.comfyui_remote.domain.InputField.ImageInput
import com.example.comfyui_remote.domain.InputField.IntInput
import com.example.comfyui_remote.domain.InputField.SeedInput
import com.example.comfyui_remote.domain.InputField.SelectionInput
import com.example.comfyui_remote.domain.InputField.StringInput
import org.junit.Assert.assertEquals
import org.junit.Test

class FormValuesTest {

    private val parsed = listOf(
        StringInput("6", "text", "a cat", "Prompt"),
        IntInput("3", "steps", 20, "KSampler"),
        FloatInput("3", "cfg", 7f, "KSampler"),
        SeedInput("3", "seed", 0, "KSampler"),
        SelectionInput("3", "sampler_name", "euler", listOf("euler", "dpmpp_2m"), "KSampler"),
        ImageInput("10", "image", "example.png", null, "Load Image")
    )

    @Test
    fun `saved values are restored by field`() {
        val saved = listOf(
            StringInput("6", "text", "a dog", "Prompt"),
            IntInput("3", "steps", 30, "KSampler"),
            FloatInput("3", "cfg", 4.5f, "KSampler"),
            SeedInput("3", "seed", 1234, "KSampler", fixed = true),
            SelectionInput("3", "sampler_name", "dpmpp_2m", listOf("euler", "dpmpp_2m"), "KSampler"),
            ImageInput("10", "image", "mine.png", "content://local", "Load Image")
        )
        val result = FormValues.overlay(parsed, FormValues.decode(FormValues.encode(saved)))
        assertEquals("a dog", (result[0] as StringInput).value)
        assertEquals(30, (result[1] as IntInput).value)
        assertEquals(4.5f, (result[2] as FloatInput).value)
        assertEquals(SeedInput("3", "seed", 1234, "KSampler", fixed = true), result[3])
        assertEquals("dpmpp_2m", (result[4] as SelectionInput).value)
        assertEquals(ImageInput("10", "image", "mine.png", null, "Load Image"), result[5])
    }

    @Test
    fun `mismatched types, unknown nodes and options no longer offered are ignored`() {
        val saved = listOf(
            StringInput("3", "steps", "thirty", "KSampler"),        // type changed
            IntInput("99", "steps", 5, "Gone"),                       // node no longer there
            SelectionInput("3", "sampler_name", "lcm", listOf("lcm"), "KSampler") // option removed
        )
        assertEquals(parsed, FormValues.overlay(parsed, saved))
    }

    @Test
    fun `nothing saved or unreadable JSON leaves the workflow values`() {
        assertEquals(parsed, FormValues.overlay(parsed, FormValues.decode(null)))
        assertEquals(parsed, FormValues.overlay(parsed, FormValues.decode("not json")))
    }
}
