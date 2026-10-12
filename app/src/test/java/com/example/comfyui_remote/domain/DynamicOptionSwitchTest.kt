package com.example.comfyui_remote.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DynamicOptionSwitchTest {

    private fun obj(s: String): JsonObject = JsonParser.parseString(s.trimIndent()).asJsonObject

    // A synthetic node: container → (shared "codec" combo, per-option extras), nested "mode" dynamic combo
    private val info = obj("""
        {
          "Vid": {"input": {"required": {
            "prefix": ["STRING", {"default": "video/x"}],
            "format": ["COMFY_DYNAMICCOMBO_V3", {"options": [
              {"key": "a", "inputs": {"required": {
                "codec": ["COMBO", {"options": ["auto", "h264", "av1"]}],
                "mode": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                  {"key": "fast", "inputs": {"required": {}}},
                  {"key": "slow", "inputs": {"required": {"crf": ["FLOAT", {"default": 23.0, "min": 0.0, "max": 51.0}]}}}]}]}}},
              {"key": "b", "inputs": {"required": {
                "codec": ["COMBO", {"options": ["auto", "vp9"]}],
                "mode": ["COMFY_DYNAMICCOMBO_V3", {"options": [
                  {"key": "fast", "inputs": {"required": {}}},
                  {"key": "slow", "inputs": {"required": {"crf": ["FLOAT", {"default": 30.0, "min": 0.0, "max": 40.0}]}}}]}],
                "label": ["STRING", {"default": "hello"}]}}},
              {"key": "needs-link", "inputs": {"required": {"mask": ["MASK", {}]}}}]}]
          }}},
          "Save": {"input": {"required": {"images": ["IMAGE", {}]}}, "output": [], "output_node": true}
        }
    """)

    private val prompt = """
        {"1": {"class_type": "Vid", "inputs": {"prefix": "p", "format": "a", "format.codec": "h264",
               "format.mode": "slow", "format.mode.crf": 45.0}}}
    """.trimIndent()

    private fun inputsOf(json: String) = JsonParser.parseString(json).asJsonObject.getAsJsonObject("1").getAsJsonObject("inputs")

    @Test fun `options needing a required socket are not offered, the current one always is`() {
        val inputs = inputsOf(prompt)
        assertEquals(listOf("a", "b"), DynamicOptionSwitch.switchableKeys(info, "Vid", "format", inputs))
        inputs.addProperty("format", "needs-link")
        assertEquals(listOf("a", "b", "needs-link"), DynamicOptionSwitch.switchableKeys(info, "Vid", "format", inputs))
    }

    @Test fun `not a dynamic combo gives null`() {
        assertNull(DynamicOptionSwitch.switchableKeys(info, "Vid", "prefix", inputsOf(prompt)))
        assertNull(DynamicOptionSwitch.switchableKeys(info, "Nope", "format", inputsOf(prompt)))
    }

    @Test fun `switching keeps shared values that are still valid and defaults the rest`() {
        val r = DynamicOptionSwitch.switch(prompt, "1", "format", "b", info)!!
        val i = inputsOf(r.json)
        assertEquals("b", i.get("format").asString)
        // h264 isn't a codec of option b: default (first) value; the nested combo and its value carry over
        assertEquals("auto", i.get("format.codec").asString)
        assertEquals("slow", i.get("format.mode").asString)
        // 45 is above option b's max of 40: spec default
        assertEquals(30.0, i.get("format.mode.crf").asDouble, 0.0)
        assertEquals("hello", i.get("format.label").asString)
        assertEquals("p", i.get("prefix").asString)
        assertTrue(r.droppedLinks.isEmpty())
    }

    @Test fun `a valid shared value is carried`() {
        val r = DynamicOptionSwitch.switch(prompt.replace("45.0", "10.0"), "1", "format", "b", info)!!
        assertEquals(10.0, inputsOf(r.json).get("format.mode.crf").asDouble, 0.0)
    }

    @Test fun `old sub-inputs of the previous option are removed and order stays adjacent`() {
        val withExtra = prompt.replace("\"prefix\": \"p\"", "\"prefix\": \"p\", \"format.stale\": 1")
        val back = DynamicOptionSwitch.switch(DynamicOptionSwitch.switch(withExtra, "1", "format", "b", info)!!.json, "1", "format", "a", info)!!
        val keys = inputsOf(back.json).keySet().toList()
        assertEquals(listOf("prefix", "format", "format.codec", "format.mode", "format.mode.crf"), keys)
    }

    @Test fun `fast mode drops crf`() {
        val r = DynamicOptionSwitch.switch(prompt.replace("\"slow\"", "\"fast\"").replace(", \"format.mode.crf\": 45.0", ""), "1", "format", "b", info)!!
        assertFalse(inputsOf(r.json).has("format.mode.crf"))
    }

    @Test fun `links of the old option are dropped and reported`() {
        val linked = prompt.replace("\"format.mode.crf\": 45.0", "\"format.mode.crf\": 45.0, \"format.src\": [\"9\", 0]")
        val r = DynamicOptionSwitch.switch(linked, "1", "format", "b", info)!!
        assertEquals(listOf("src"), r.droppedLinks)
        assertFalse(inputsOf(r.json).has("format.src"))
    }

    @Test fun `unknown node, option or one needing a socket gives null`() {
        assertNull(DynamicOptionSwitch.switch(prompt, "9", "format", "b", info))
        assertNull(DynamicOptionSwitch.switch(prompt, "1", "format", "zzz", info))
        assertNull(DynamicOptionSwitch.switch(prompt, "1", "format", "needs-link", info))
        assertNull(DynamicOptionSwitch.switch("{nope", "1", "format", "b", info))
        assertNull(DynamicOptionSwitch.switch(prompt, "1", "prefix", "b", info))
    }

    @Test fun `nested combo can be switched by its dotted path`() {
        val r = DynamicOptionSwitch.switch(prompt, "1", "format.mode", "fast", info)!!
        val i = inputsOf(r.json)
        assertEquals("fast", i.get("format.mode").asString)
        assertFalse(i.has("format.mode.crf"))
        assertEquals("h264", i.get("format.codec").asString)
    }

    @Test fun `keepImages carries the picked image over`() {
        val old = listOf<InputField>(InputField.ImageInput("3", "image", "up.png", "content://x", "Load"))
        val new = listOf<InputField>(InputField.ImageInput("3", "image", "default.png", null, "Load"), InputField.StringInput("1", "t", "v", "T"))
        val merged = DynamicOptionSwitch.keepImages(old, new)
        assertEquals("up.png", (merged[0] as InputField.ImageInput).value)
        assertEquals("content://x", (merged[0] as InputField.ImageInput).localUri)
        assertEquals(new[1], merged[1])
    }

    @Test fun `every switchable option of every dynamic combo in the stock snapshot yields a valid prompt`() {
        val snapshot = JsonParser.parseString(
            File(javaClass.classLoader!!.getResource("workflow-corpus/object_info.json")!!.toURI()).readText()
        ).asJsonObject
        var switched = 0
        for ((classType, def) in snapshot.entrySet()) {
            val input = def.asJsonObject.getAsJsonObject("input") ?: continue
            for (section in listOf("required", "optional")) {
                val group = input.getAsJsonObject(section) ?: continue
                for ((name, spec) in group.entrySet()) {
                    val arr = spec.asJsonArray
                    if (arr.get(0).let { !it.isJsonPrimitive || it.asString != "COMFY_DYNAMICCOMBO_V3" }) continue
                    val options = arr.get(1).asJsonObject.getAsJsonArray("options").map { it.asJsonObject.get("key").asString }
                    // Start from the first option built by switching from a stub that has only the key
                    val start = options.first()
                    val stub = JsonObject().apply {
                        add("1", JsonObject().apply {
                            addProperty("class_type", classType)
                            add("inputs", JsonObject().apply { addProperty(name, start) })
                        })
                    }.toString()
                    val base = DynamicOptionSwitch.switch(stub, "1", name, start, snapshot) ?: continue
                    for (key in DynamicOptionSwitch.switchableKeys(snapshot, classType, name, inputsOf(base.json)).orEmpty()) {
                        val r = DynamicOptionSwitch.switch(base.json, "1", name, key, snapshot)
                            ?: throw AssertionError("$classType.$name -> $key returned null")
                        val issues = PromptValidator.validate(JsonParser.parseString(r.json).asJsonObject, snapshot, reachableOnly = false)
                            .filter { it.nodeId == "1" && it.inputName?.startsWith("$name.") == true }
                        assertTrue("$classType.$name -> $key: $issues", issues.isEmpty())
                        switched++
                    }
                }
            }
        }
        assertTrue("expected to switch some options, got $switched", switched > 10)
    }
}
