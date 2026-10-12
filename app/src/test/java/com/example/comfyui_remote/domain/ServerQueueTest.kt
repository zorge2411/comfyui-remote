package com.example.comfyui_remote.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerQueueTest {

    private fun parse(json: String, known: Map<String, String> = emptyMap()) =
        ServerQueue.parse(JsonParser.parseString(json).asJsonObject, known)

    @Test
    fun `running first, then pending in queue order`() {
        val jobs = parse(
            """{
              "queue_running": [[5, "run", {}, {}, ["9"]]],
              "queue_pending": [[8, "p8", {}, {}, ["9"]], [6, "p6", {}, {}, ["9"]]]
            }"""
        )
        assertEquals(listOf("run", "p6", "p8"), jobs.map { it.promptId })
        assertEquals(listOf(true, false, false), jobs.map { it.running })
        assertEquals(listOf(5L, 6L, 8L), jobs.map { it.number })
    }

    @Test
    fun `the name comes from extra_pnginfo`() {
        val jobs = parse(
            """{"queue_running": [], "queue_pending": [
              [1, "a", {}, {"extra_pnginfo": {"workflow": {"extra": {"name": "Z-Image"}}}}, []]
            ]}"""
        )
        assertEquals("Z-Image", jobs.single().workflowName)
    }

    @Test
    fun `otherwise the name the app remembered is used`() {
        val jobs = parse(
            """{"queue_running": [[1, "a", {}, {"client_id": "x"}, []]], "queue_pending": [[2, "b", {}, {}, []]]}""",
            mapOf("a" to "My workflow")
        )
        assertEquals("My workflow", jobs[0].workflowName)
        assertNull(jobs[1].workflowName)
    }

    @Test
    fun `an empty queue`() {
        assertTrue(parse("""{"queue_running": [], "queue_pending": []}""").isEmpty())
    }

    @Test
    fun `missing keys and malformed entries are skipped`() {
        assertTrue(parse("{}").isEmpty())
        val jobs = parse(
            """{"queue_running": "nope", "queue_pending": [
              "text", [], [3], [4, 42, {}, {}], [5, "ok", {}, "no extra", []]
            ]}"""
        )
        assertEquals(listOf("ok"), jobs.map { it.promptId })
        assertNull(jobs.single().workflowName)
    }
}
