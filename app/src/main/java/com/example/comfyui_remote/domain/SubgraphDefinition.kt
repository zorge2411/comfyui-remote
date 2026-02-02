package com.example.comfyui_remote.domain

import com.google.gson.JsonObject

/**
 * Represents the definition of a Subgraph (Group Node) as parsed from the workflow JSON.
 * Found in definitions.subgraphs array.
 */
data class SubgraphDefinition(
    val id: String,  // UUID of the subgraph type
    val name: String,
    val inputs: List<SubgraphInput>,
    val outputs: List<SubgraphOutput>,
    val nodes: List<JsonObject>,  // Internal nodes (raw JSON)
    val links: List<com.google.gson.JsonArray>   // Internal links (raw JSON arrays)
)

data class SubgraphInput(
    val id: String, // UUID
    val name: String,
    val type: String,
    val linkIds: List<Int> // Links from the inputs array
)

data class SubgraphOutput(
    val id: String, // UUID
    val name: String,
    val type: String,
    val linkIds: List<Int> // Links from the outputs array
)
