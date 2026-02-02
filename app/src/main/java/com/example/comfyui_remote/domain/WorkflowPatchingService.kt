package com.example.comfyui_remote.domain

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object WorkflowPatchingService {

    private val gson = Gson()

    /**
     * Patches the workflow JSON with the uploaded image filenames.
     *
     * @param json The original workflow JSON (API format or Graph format).
     * @param imageInputs A map of Node ID to Uploaded Filename.
     * @return The patched JSON string.
     */
    fun patchWorkflow(json: String, imageInputs: Map<String, String>): String {
        if (imageInputs.isEmpty()) return json

        return try {
            val root = JsonParser.parseString(json)
            
            // Check if it's API Format (Map<NodeID, Node>) or Graph Format ({ "nodes": [], "links": [] })
            if (root.isJsonObject) {
                val rootObj = root.asJsonObject
                
                // Handle Graph Format (has "nodes" array)
                if (rootObj.has("nodes") && rootObj.get("nodes").isJsonArray) {
                    patchGraphFormat(rootObj, imageInputs)
                } 
                // Handle API Format (Map<String, NodeObject>) - Less likely for raw execution but possible if pre-converted
                else {
                    patchApiFormat(rootObj, imageInputs)
                }
                
                gson.toJson(root)
            } else {
                json
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return original if patching fails to avoid crashing
            json 
        }
    }

    private fun patchGraphFormat(root: JsonObject, imageInputs: Map<String, String>) {
        val nodes = root.getAsJsonArray("nodes")
        nodes.forEach { nodeElement ->
            if (nodeElement.isJsonObject) {
                val node = nodeElement.asJsonObject
                if (node.has("id")) {
                    val id = node.get("id").asString // IDs are equivalent as strings
                    // Also check for int vs string ID mismatch if needed, but usually safe as string
                    
                    if (imageInputs.containsKey(id)) {
                        val filename = imageInputs[id]
                        
                        // LoadImage nodes usually store the filename in widgets_values[0]
                        if (node.has("widgets_values")) {
                            val widgets = node.get("widgets_values")
                            if (widgets.isJsonArray) {
                                val widgetArray = widgets.asJsonArray
                                if (widgetArray.size() > 0) {
                                    // Replace the first widget value with the new filename
                                    // We need to keep the existing array type if possible, stripping surrounding quotes is handled by Gson but let's be careful
                                    widgetArray.set(0, gson.toJsonTree(filename))
                                } else {
                                    // If empty, add it
                                    widgetArray.add(filename)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun patchApiFormat(root: JsonObject, imageInputs: Map<String, String>) {
        imageInputs.forEach { (nodeId, filename) ->
            if (root.has(nodeId)) {
                val node = root.getAsJsonObject(nodeId)
                if (node.has("inputs")) {
                    val inputs = node.getAsJsonObject("inputs")
                    // In API format, standard LoadImage input key is "image"
                    if (inputs.has("image")) {
                        inputs.addProperty("image", filename)
                    }
                }
            }
        }
    }
}
