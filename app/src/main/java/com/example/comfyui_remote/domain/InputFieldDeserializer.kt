package com.example.comfyui_remote.domain

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Rebuilds [InputField] subtypes from JSON by their `label` type tag. Used for queued items and, since
 * Phase 101, for each workflow's remembered form values, so the tags in [InputField] must not change.
 */
class InputFieldDeserializer : JsonDeserializer<InputField> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): InputField {
        val jsonObject = json.asJsonObject
        val label = jsonObject.get("label")?.asString ?: throw JsonParseException("Missing label")

        return when (label) {
            "Text" -> context.deserialize(jsonObject, InputField.StringInput::class.java)
            "Number" -> context.deserialize(jsonObject, InputField.IntInput::class.java)
            "Seed" -> context.deserialize(jsonObject, InputField.SeedInput::class.java)
            "Number (Float)" -> context.deserialize(jsonObject, InputField.FloatInput::class.java)
            "Model" -> context.deserialize(jsonObject, InputField.ModelInput::class.java)
            "Selection" -> context.deserialize(jsonObject, InputField.SelectionInput::class.java)
            "Image" -> context.deserialize(jsonObject, InputField.ImageInput::class.java)
            else -> throw JsonParseException("Unknown InputField label: $label")
        }
    }
}
