package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class WorkflowParser {

    fun parse(jsonContent: String, metadata: JsonObject? = null): List<InputField> {
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
                        // Iterate through all keys in inputsObj
                        inputsObj.entrySet().forEach { (fieldName, fieldValue) ->
                            // Skip if it's a connection (JsonArray like ["4", 0])
                            if (!fieldValue.isJsonPrimitive) return@forEach
                            
                            val primitive = fieldValue.asJsonPrimitive
                            
                            // Guess or determine Type
                            val inputField = when {
                                fieldName.contains("seed", ignoreCase = true) -> {
                                    InputField.SeedInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = try { primitive.asLong } catch(e: Exception) { 0L },
                                        nodeTitle = title
                                    )
                                }
                                fieldName == "ckpt_name" || fieldName == "model" -> {
                                    InputField.ModelInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asString,
                                        nodeTitle = title
                                    )
                                }
                                primitive.isNumber -> {
                                    // If it's a long number or named 'seed', we already caught it.
                                    // Otherwise, IntInput.
                                    InputField.IntInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asInt,
                                        nodeTitle = title
                                    )
                                }
                                primitive.isString -> {
                                    InputField.StringInput(
                                        nodeId = nodeId,
                                        fieldName = fieldName,
                                        value = primitive.asString,
                                        nodeTitle = title
                                    )
                                }
                                else -> null
                            }
                            
                            inputField?.let { inputs.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return inputs
    }

    fun parseAllNodes(jsonContent: String): List<NodeInfo> {
        val nodes = mutableListOf<NodeInfo>()
        try {
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
            jsonObject.entrySet().forEach { (nodeId, element) ->
                if (element.isJsonObject) {
                    val node = element.asJsonObject
                    val classType = node.get("class_type")?.asString ?: "Unknown"
                    val meta = node.get("_meta")?.asJsonObject
                    val title = meta?.get("title")?.asString ?: classType
                    nodes.add(NodeInfo(nodeId, title, classType))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return nodes
    }
}

data class NodeInfo(
    val id: String,
    val title: String,
    val classType: String
)
