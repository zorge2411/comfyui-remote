package com.example.comfyui_remote

import com.example.comfyui_remote.domain.InputField
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class InputFieldSerializationTest {

    @Test
    fun testSerializationConflict() {
        val input = InputField.IntInput(
            nodeId = "10",
            fieldName = "seed",
            value = 42,
            nodeTitle = "KSampler"
        )

        // This would crash with IllegalArgumentException before the fix
        val gson = Gson()
        val json = gson.toJson(input)
        
        println("Serialized JSON: $json")
        
        // Basic verification
        assert(json.contains("\"label\":\"Number\""))
        assert(json.contains("\"value\":42"))
        assert(json.contains("\"fieldName\":\"seed\""))
    }
    
    @Test
    fun testPolymorphicDeserialization() {
        val json = """
            [
                {
                    "label": "Number",
                    "nodeId": "10",
                    "fieldName": "seed",
                    "value": 12345,
                    "nodeTitle": "KSampler"
                },
                {
                    "label": "Text",
                    "nodeId": "20",
                    "fieldName": "prompt",
                    "value": "a cat",
                    "nodeTitle": "CLIP Text Encode"
                }
            ]
        """.trimIndent()
        
        val gson = GsonBuilder()
            .registerTypeAdapter(InputField::class.java, InputFieldDeserializer())
            .create()
            
        val listType = object : com.google.gson.reflect.TypeToken<List<InputField>>() {}.type
        val list = gson.fromJson<List<InputField>>(json, listType)
        
        assertEquals(2, list.size)
        
        val first = list[0] as InputField.IntInput
        assertEquals(12345, first.value)
        assertEquals("seed", first.fieldName)
        
        val second = list[1] as InputField.StringInput
        assertEquals("a cat", second.value)
    }
}
