package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class WorkflowExecutor {

    fun injectValues(originalJson: String, inputs: List<InputField>): String {
        return try {
            val jsonObject = JsonParser.parseString(originalJson).asJsonObject

            inputs.forEach { input ->
                val node = jsonObject.getAsJsonObject(input.nodeId)
                if (node != null) {
                    val inputsObj = node.getAsJsonObject("inputs")
                    if (inputsObj != null) {
                        when (input) {
                            is InputField.StringInput -> {
                                if (input.fieldName == "text" || inputsObj.has(input.fieldName)) {
                                    inputsObj.addProperty(input.fieldName, input.value)
                                }
                            }
                            is InputField.IntInput -> {
                                if (inputsObj.has(input.fieldName)) {
                                    inputsObj.addProperty(input.fieldName, input.value)
                                }
                            }
                            is InputField.SeedInput -> {
                                if (inputsObj.has(input.fieldName)) {
                                    inputsObj.addProperty(input.fieldName, input.value)
                                }
                            }
                        }
                    }
                }
            }
            Gson().toJson(jsonObject)
        } catch (e: Exception) {
            e.printStackTrace()
            originalJson // Return original on failure
        }
    }
}
