package com.example.comfyui_remote.domain

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader

/**
 * Whether a server `/history` response lists anything (Phase 104). ComfyUI keeps its history in memory,
 * so it is empty after a restart; "Reload from server" must not clear the gallery then.
 */
object HistoryCheck {

    /** Reads only as far as the first entry. Anything that isn't a JSON object counts as no entries. */
    fun hasEntries(json: Reader): Boolean = try {
        val reader = JsonReader(json)
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            false
        } else {
            reader.beginObject()
            reader.hasNext()
        }
    } catch (e: Exception) {
        false
    }
}
