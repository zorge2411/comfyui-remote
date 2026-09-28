package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

/**
 * A workflow's remembered form values (Phase 101), stored as JSON in the queue's format. They are restored
 * onto freshly parsed inputs by field, so values of nodes or fields that no longer exist are dropped.
 */
object FormValues {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(InputField::class.java, InputFieldDeserializer())
        .create()
    private val listType = object : TypeToken<List<InputField>>() {}.type

    // Serialized by runtime type, as the queue does: with the declared List<InputField> type Gson would write
    // only the base class (the label tag) and lose every value.
    fun encode(inputs: List<InputField>): String = Gson().toJson(ArrayList(inputs))

    fun decode(text: String?): List<InputField> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<InputField>>(text, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Takes each saved value whose field (nodeId, fieldName) and type still match. A selection keeps its saved
     * value only while the option is still offered; an image keeps the server file name, not the local preview.
     */
    fun overlay(parsed: List<InputField>, saved: List<InputField>): List<InputField> {
        if (saved.isEmpty()) return parsed
        val byKey = saved.associateBy { it.key }
        return parsed.map { field ->
            val old = byKey[field.key] ?: return@map field
            when {
                field is InputField.StringInput && old is InputField.StringInput -> field.copy(value = old.value)
                field is InputField.IntInput && old is InputField.IntInput -> field.copy(value = old.value)
                field is InputField.FloatInput && old is InputField.FloatInput -> field.copy(value = old.value)
                field is InputField.SeedInput && old is InputField.SeedInput -> field.copy(value = old.value, fixed = old.fixed)
                field is InputField.SelectionInput && old is InputField.SelectionInput ->
                    if (old.value in field.options) field.copy(value = old.value) else field
                field is InputField.ImageInput && old is InputField.ImageInput ->
                    if (old.value != null) field.copy(value = old.value) else field
                else -> field
            }
        }
    }
}
