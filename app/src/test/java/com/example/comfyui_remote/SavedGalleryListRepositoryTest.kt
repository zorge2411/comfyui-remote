package com.example.comfyui_remote

import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.data.SavedGalleryList
import org.junit.Assert.*
import org.junit.Test

class SavedGalleryListRepositoryTest {
    @Test
    fun `SavedGalleryList createSnapshot creates correct list type`() {
        val filter = GallerySyncFilter.default()
        val itemIds = listOf(1L, 2L, 3L)
        
        val list = SavedGalleryList.createSnapshot(
            name = "Test Snapshot",
            filter = filter,
            itemIds = itemIds
        )
        
        assertEquals("List name should match", "Test Snapshot", list.name)
        assertEquals("List type should be SNAPSHOT", SavedGalleryList.ListType.SNAPSHOT, list.listType)
        assertEquals("Item count should match", 3, list.itemCount)
        assertEquals("Saved item IDs should match", itemIds, list.savedItemIds)
        assertTrue("Filter should be set", list.filter == filter)
    }

    @Test
    fun `SavedGalleryList createLiveFilter creates correct list type`() {
        val filter = GallerySyncFilter(
            mediaType = GallerySyncFilter.MediaType.IMAGE
        )
        
        val list = SavedGalleryList.createLiveFilter(
            name = "Test Filter",
            filter = filter,
            itemCount = 100
        )
        
        assertEquals("List name should match", "Test Filter", list.name)
        assertEquals("List type should be LIVE_FILTER", SavedGalleryList.ListType.LIVE_FILTER, list.listType)
        assertEquals("Item count should match", 100, list.itemCount)
        assertTrue("Saved item IDs should be empty", list.savedItemIds.isEmpty())
        assertTrue("Filter should be set", list.filter == filter)
    }

    @Test
    fun `SavedGalleryList isSnapshot returns correct value`() {
        val snapshot = SavedGalleryList.createSnapshot(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemIds = listOf(1L)
        )
        
        val liveFilter = SavedGalleryList.createLiveFilter(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 1
        )
        
        assertTrue("Snapshot list should return true for isSnapshot()", snapshot.isSnapshot())
        assertFalse("Live filter list should return false for isSnapshot()", liveFilter.isSnapshot())
    }

    @Test
    fun `SavedGalleryList isLiveFilter returns correct value`() {
        val snapshot = SavedGalleryList.createSnapshot(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemIds = listOf(1L)
        )
        
        val liveFilter = SavedGalleryList.createLiveFilter(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 1
        )
        
        assertFalse("Snapshot list should return false for isLiveFilter()", snapshot.isLiveFilter())
        assertTrue("Live filter list should return true for isLiveFilter()", liveFilter.isLiveFilter())
    }

    @Test
    fun `SavedGalleryList getAgeString returns Just now for recent list`() {
        val list = SavedGalleryList(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 0,
            savedAt = System.currentTimeMillis()
        )
        
        val ageString = list.getAgeString()
        assertTrue("Age should be 'Just now' for recent list", ageString == "Just now")
    }

    @Test
    fun `SavedGalleryList getAgeString returns minutes ago for old list`() {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        val list = SavedGalleryList(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 0,
            savedAt = fiveMinutesAgo
        )
        
        val ageString = list.getAgeString()
        assertTrue("Age should contain '5 minute'", ageString.contains("5 minute"))
    }

    @Test
    fun `SavedGalleryList getAgeString returns hours ago for old list`() {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000)
        val list = SavedGalleryList(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 0,
            savedAt = twoHoursAgo
        )
        
        val ageString = list.getAgeString()
        assertTrue("Age should contain '2 hour'", ageString.contains("2 hour"))
    }

    @Test
    fun `SavedGalleryList getAgeString returns days ago for old list`() {
        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000)
        val list = SavedGalleryList(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 0,
            savedAt = threeDaysAgo
        )
        
        val ageString = list.getAgeString()
        assertTrue("Age should contain '3 day'", ageString.contains("3 day"))
    }

    @Test
    fun `SavedGalleryList getTypeDescription returns correct description for snapshot`() {
        val list = SavedGalleryList.createSnapshot(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemIds = listOf(1L, 2L, 3L)
        )
        
        val description = list.getTypeDescription()
        assertEquals("Description should match snapshot format", "Snapshot (3 items)", description)
    }

    @Test
    fun `SavedGalleryList getTypeDescription returns correct description for live filter`() {
        val list = SavedGalleryList.createLiveFilter(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemCount = 100
        )
        
        val description = list.getTypeDescription()
        assertEquals("Description should match live filter format", "Live Filter", description)
    }

    @Test
    fun `SavedGalleryList generates unique IDs`() {
        val list1 = SavedGalleryList(
            name = "Test 1",
            filter = GallerySyncFilter.default(),
            itemCount = 0
        )
        
        val list2 = SavedGalleryList(
            name = "Test 2",
            filter = GallerySyncFilter.default(),
            itemCount = 0
        )
        
        assertNotEquals("IDs should be unique", list1.id, list2.id)
    }

    @Test
    fun `SavedGalleryList copy creates new instance with same values`() {
        val original = SavedGalleryList.createSnapshot(
            name = "Test",
            filter = GallerySyncFilter.default(),
            itemIds = listOf(1L, 2L)
        )
        
        val copy = original.copy(name = "Renamed")
        
        assertEquals("Copied list should have new name", "Renamed", copy.name)
        assertEquals("Copied list should have same ID", original.id, copy.id)
        assertEquals("Copied list should have same filter", original.filter, copy.filter)
        assertEquals("Copied list should have same item count", original.itemCount, copy.itemCount)
    }
}
