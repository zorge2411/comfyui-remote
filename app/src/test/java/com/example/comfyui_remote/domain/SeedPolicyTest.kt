package com.example.comfyui_remote.domain

import com.example.comfyui_remote.domain.InputField.IntInput
import com.example.comfyui_remote.domain.InputField.SeedInput
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeedPolicyTest {

    private val random = generateSequence(1000L) { it + 1 }.iterator()

    @Test
    fun `seeds are random unless fixed`() {
        val inputs = listOf(
            SeedInput("3", "seed", 42, "KSampler"),
            SeedInput("5", "noise_seed", 7, "Sampler", fixed = true),
            IntInput("3", "steps", 20, "KSampler")
        )
        val (resolved, used) = SeedPolicy.resolve(inputs) { random.next() }
        assertEquals(1000L, (resolved[0] as SeedInput).value)
        assertEquals(7L, (resolved[1] as SeedInput).value)
        assertEquals(inputs[2], resolved[2])
        assertEquals(mapOf("3/seed" to 1000L, "5/noise_seed" to 7L), used)
    }

    @Test
    fun `fixed false is random too`() {
        val (resolved, _) = SeedPolicy.resolve(listOf(SeedInput("3", "seed", 42, "K", fixed = false))) { 9L }
        assertEquals(9L, (resolved.single() as SeedInput).value)
    }

    @Test
    fun `queued JSON without the fixed flag stays random`() {
        val gson = GsonBuilder().registerTypeAdapter(InputField::class.java, InputFieldDeserializer()).create()
        val type = object : TypeToken<List<InputField>>() {}.type
        val old = """[{"nodeId":"3","fieldName":"seed","value":42,"nodeTitle":"KSampler","label":"Seed"}]"""
        val restored: List<InputField> = gson.fromJson(old, type)
        assertNull((restored.single() as SeedInput).fixed)
        assertEquals(9L, (SeedPolicy.resolve(restored) { 9L }.first.single() as SeedInput).value)

        val fixed = gson.toJson(listOf(SeedInput("3", "seed", 42, "KSampler", fixed = true)))
        val back: List<InputField> = gson.fromJson(fixed, type)
        assertEquals(true, (back.single() as SeedInput).fixed)
        assertEquals(42L, (SeedPolicy.resolve(back) { 9L }.first.single() as SeedInput).value)
    }
}
