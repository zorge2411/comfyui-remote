package com.example.comfyui_remote.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject

// The history endpoint returns a Map<String, HistoryItem>
// key is the prompt_id
data class HistoryItem(
    val prompt: List<Any>?, // ComfyUI returns prompt in a weird format sometimes, but usually it's [index, id, prompt_obj, extra...] or just an object.
    // Actually, checking ComfyUI source:
    // history[prompt_id] = { "prompt": prompt, "outputs": outputs, "status": status }
    // The 'prompt' here is the one submitted.
    // Let's use JsonObject (raw) for the prompt to be safe and just dump it to string.
    val outputs: JsonObject?,
    // The prompt that was executed.
    // Warning: existing history might have a tuple or list structure in some versions?
    // Let's grab the raw JsonElement first.
    val prompt_internal: JsonElement? = null 
)

// We will parse it manually in VM or use a looser structure.
// Let's just strictly TYPE what we need. 
// We need the "prompt" part which defines the workflow.
