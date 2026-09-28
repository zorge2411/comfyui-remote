package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.data.GeneratedMediaListing
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GalleryViewTest {

    private val zone = ZoneId.of("Europe/Copenhagen")

    private fun at(day: Int, hour: Int) =
        LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun item(id: Long, workflow: String, file: String, time: Long, type: String = "IMAGE") =
        GeneratedMediaListing(id, workflow, file, null, "h", 8188, time, type, "output")

    private val items = listOf(
        item(1, "Krea Turbo", "krea_0001.png", at(20, 10)),
        item(2, "Z-Image", "z_0001.png", at(25, 9)),
        item(3, "MiniMax video", "clip_0001.mp4", at(27, 23), "VIDEO"),
        item(4, "anima", "anima_0001.png", at(28, 8))
    )

    private fun ids(filter: GallerySyncFilter) = GalleryView.apply(items, filter, zone).map { it.id }

    @Test
    fun `no filter shows everything newest first`() {
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(GallerySyncFilter.default()))
    }

    @Test
    fun `the end day is included in full`() {
        // End date picked as the start of 27 Sep: the 23:00 video that day is included
        assertEquals(listOf(3L, 2L), ids(GallerySyncFilter(startDate = at(25, 0), endDate = at(27, 0))))
    }

    @Test
    fun `workflow and file names use the wildcard rules`() {
        assertEquals(listOf(1L), ids(GallerySyncFilter(workflowNameFilter = "krea*")))
        assertEquals(listOf(4L, 2L, 1L), ids(GallerySyncFilter(fileNameFilter = "*.png")))
        assertEquals(listOf(4L, 3L, 2L), ids(GallerySyncFilter(workflowNameFilter = "-krea*")))
    }

    @Test
    fun `media type narrows to videos`() {
        assertEquals(listOf(3L), ids(GallerySyncFilter(mediaType = GallerySyncFilter.MediaType.VIDEO)))
    }

    @Test
    fun `sorts oldest first and by name`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(GallerySyncFilter(sortOrder = GallerySyncFilter.SortOrder.OLDEST_FIRST)))
        assertEquals(listOf(4L, 1L, 3L, 2L), ids(GallerySyncFilter(sortOrder = GallerySyncFilter.SortOrder.NAME_ASC)))
    }
}
