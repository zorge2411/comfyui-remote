package com.example.comfyui_remote.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDownloadsTest {

    private fun job(id: String?, name: String, status: String, done: Long = 0, total: Long? = null, position: Int? = null) =
        ModelDownload(id, name, "vae", total, done, status, null, position)

    @Test
    fun `parses the list route and the queue event`() {
        val array = """[{"id":"a","filename":"x.safetensors","directory":"vae","total":null,"done":0,"status":"queued","error":null,"position":1}]"""
        val fromArray = ModelDownloads.parseList(JsonParser.parseString(array))
        val fromEvent = ModelDownloads.parseList(JsonParser.parseString("""{"jobs":$array}"""))
        assertEquals(fromArray, fromEvent)
        assertEquals(job("a", "x.safetensors", "queued", position = 1), fromArray.single())
    }

    @Test
    fun `parse tolerates missing fields from extension v1`() {
        val parsed = ModelDownloads.parse(JsonParser.parseString("""{"id":"a","filename":"x","directory":"vae","done":5,"status":"downloading"}""").asJsonObject)
        assertEquals(job("a", "x", "downloading", done = 5), parsed)
        assertEquals(emptyList<ModelDownload>(), ModelDownloads.parseList(null))
    }

    @Test
    fun `upsert replaces by id, then the unconfirmed row, else appends`() {
        val pending = job(null, "x", "queued")
        val confirmed = job("a", "x", "queued", position = 1)
        val list = ModelDownloads.upsert(listOf(pending), confirmed)
        assertEquals(listOf(confirmed), list)
        val progressed = confirmed.copy(status = "downloading", done = 10)
        assertEquals(listOf(progressed), ModelDownloads.upsert(list, progressed))
        assertEquals(2, ModelDownloads.upsert(list, job("b", "y", "queued")).size)
    }

    @Test
    fun `summary counts across the batch`() {
        val list = listOf(
            job("a", "big.safetensors", "downloading", done = 40, total = 100),
            job("b", "next.safetensors", "queued", position = 1),
            job("c", "old.safetensors", "done")
        )
        val s = ModelDownloads.summary(list, finishedInBatch = 1)!!
        assertEquals("Downloading 2 of 3", s.title)
        assertEquals("big.safetensors, 40%", s.text)
        assertEquals(40, s.percent)
        assertNull(ModelDownloads.summary(listOf(job("c", "old", "done")), 1))
        assertEquals("next.safetensors", ModelDownloads.summary(listOf(job("b", "next.safetensors", "queued")), 0)!!.text)
    }

    @Test
    fun `finished summary wording`() {
        assertEquals("1 model downloaded", ModelDownloads.finishedSummary(listOf(job("a", "x", "done"))))
        assertEquals(
            "2 models downloaded, 1 failed, 1 cancelled",
            ModelDownloads.finishedSummary(listOf(job("a", "x", "done"), job("b", "y", "done"), job("c", "z", "error"), job("d", "w", "cancelled")))
        )
    }
}
