package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.WorkflowEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowListViewTest {

    private fun wf(id: Long, name: String, createdAt: Long, lastUsedAt: Long? = null) =
        WorkflowEntity(id = id, name = name, jsonContent = "{}", createdAt = createdAt, lastUsedAt = lastUsedAt)

    private val a = wf(1, "Flux portrait", createdAt = 100, lastUsedAt = 500)
    private val b = wf(2, "anime upscale", createdAt = 300)
    private val c = wf(3, "Z-Image Turbo", createdAt = 200, lastUsedAt = 250)
    private val all = listOf(a, b, c)
    private val models = mapOf(3L to listOf(ModelRef("diffusion_models", "z_image_turbo-Q8_0")))

    private fun ids(list: List<WorkflowEntity>) = list.map { it.id }

    @Test
    fun `an empty query keeps every workflow`() {
        assertEquals(3, WorkflowListView.apply(all, models, "  ", WorkflowSort.NEWEST).size)
    }

    @Test
    fun `the query matches names and models, ignoring case`() {
        assertEquals(listOf(1L), ids(WorkflowListView.apply(all, models, "FLUX", WorkflowSort.NAME)))
        assertEquals(listOf(3L), ids(WorkflowListView.apply(all, models, "q8_0", WorkflowSort.NAME)))
    }

    @Test
    fun `last used falls back to the creation time`() {
        // a used at 500, b never used (created 300), c used at 250
        assertEquals(listOf(1L, 2L, 3L), ids(WorkflowListView.apply(all, models, "", WorkflowSort.LAST_USED)))
    }

    @Test
    fun `name and newest orders`() {
        assertEquals(listOf(2L, 1L, 3L), ids(WorkflowListView.apply(all, models, "", WorkflowSort.NAME)))
        assertEquals(listOf(2L, 3L, 1L), ids(WorkflowListView.apply(all, models, "", WorkflowSort.NEWEST)))
    }
}
