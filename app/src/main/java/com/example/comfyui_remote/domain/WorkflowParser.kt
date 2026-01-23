package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class WorkflowParser {

    fun parse(jsonContent: String): List<InputField> {
        val inputs = mutableListOf<InputField>()
        try {
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject

            jsonObject.entrySet().forEach { (nodeId, element) ->
                if (element.isJsonObject) {
                    val node = element.asJsonObject
                    val classType = node.get("class_type")?.asString
                    val inputsObj = node.get("inputs")?.asJsonObject
                    val meta = node.get("_meta")?.asJsonObject
                    val title = meta?.get("title")?.asString ?: classType ?: "Unknown Node"

                    if (classType != null && inputsObj != null) {
                        when (classType) {
                            "CLIPTextEncode" -> {
                                if (inputsObj.has("text")) {
                                    inputs.add(
                                        InputField.StringInput(
                                            nodeId = nodeId,
                                            fieldName = "text",
                                            value = inputsObj.get("text").asString,
                                            nodeTitle = title
                                        )
                                    )

                                }
                            }
                            "CheckpointLoaderSimple" -> {
                                if (inputsObj.has("ckpt_name")) {
                                    inputs.add(
                                        InputField.ModelInput(
                                            nodeId = nodeId,
                                            fieldName = "ckpt_name",
                                            value = inputsObj.get("ckpt_name").asString,
                                            nodeTitle = title
                                        )
                                    )
                                }
                            }
                            "KSampler" -> {
                                if (inputsObj.has("seed")) {
                                    inputs.add(
                                        InputField.SeedInput(
                                            nodeId = nodeId,
                                            fieldName = "seed",
                                            value = inputsObj.get("seed").asLong,
                                            nodeTitle = title
                                        )
                                    )
                                }
                                if (inputsObj.has("steps")) {
                                    inputs.add(
                                        InputField.IntInput(
                                            nodeId = nodeId,
                                            fieldName = "steps",
                                            value = inputsObj.get("steps").asInt,
                                            nodeTitle = title
                                        )
                                    )
                                }
                                if (inputsObj.has("cfg")) {
                                     // CFG is typically Float, but for MVP sticking to Int or simple Double
                                     // Let's assume user might want to edit it. Adding as 'Int' for now or need FloatInput
                                }
                            }
                            "EmptyLatentImage" -> {
                                if (inputsObj.has("width")) {
                                    inputs.add(
                                        InputField.IntInput(
                                            nodeId = nodeId,
                                            fieldName = "width",
                                            value = inputsObj.get("width").asInt,
                                            nodeTitle = title
                                        )
                                    )
                                }
                                if (inputsObj.has("height")) {
                                    inputs.add(
                                        InputField.IntInput(
                                            nodeId = nodeId,
                                            fieldName = "height",
                                            value = inputsObj.get("height").asInt,
                                            nodeTitle = title
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return inputs
    }
}
