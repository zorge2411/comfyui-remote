package com.example.comfyui_remote

import com.example.comfyui_remote.data.GallerySyncFilter
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GallerySyncFilter data class.
 */
class GallerySyncFilterTest {

    @Test
    fun `filter isActive when any field set returns true`() {
        val filter = GallerySyncFilter(
            startDate = System.currentTimeMillis(),
            endDate = null,
            maxItems = 100,
            workflowNameFilter = null,
            mediaType = null,
            serverFilter = null
        )
        assertTrue("Filter should be active when startDate is set", filter.isActive())
    }

    @Test
    fun `filter isActive when all null returns false`() {
        val filter = GallerySyncFilter.default()
        assertFalse("Filter should not be active when all fields are null/default", filter.isActive())
    }

    @Test
    fun `filter isActive when workflowNameFilter is set returns true`() {
        val filter = GallerySyncFilter(
            workflowNameFilter = "Test Workflow"
        )
        assertTrue("Filter should be active when workflowNameFilter is set", filter.isActive())
    }

    @Test
    fun `filter isActive when mediaType is set returns true`() {
        val filter = GallerySyncFilter(
            mediaType = GallerySyncFilter.MediaType.IMAGE
        )
        assertTrue("Filter should be active when mediaType is set", filter.isActive())
    }

    @Test
    fun `filter isActive when serverFilter is set returns true`() {
        val filter = GallerySyncFilter(
            serverFilter = "192.168.1.100:8188"
        )
        assertTrue("Filter should be active when serverFilter is set", filter.isActive())
    }

    @Test
    fun `filter with endDate before startDate is invalid`() {
        val now = System.currentTimeMillis()
        val filter = GallerySyncFilter(
            startDate = now,
            endDate = now - 1000 // 1 second before start
        )
        assertFalse("Filter should be invalid when endDate is before startDate", filter.isValid())
    }

    @Test
    fun `filter with valid date range is valid`() {
        val now = System.currentTimeMillis()
        val filter = GallerySyncFilter(
            startDate = now - 86400000, // 1 day ago
            endDate = now
        )
        assertTrue("Filter should be valid with correct date range", filter.isValid())
    }

    @Test
    fun `filter with maxItems less than 1 is invalid`() {
        val filter = GallerySyncFilter(
            maxItems = 0
        )
        assertFalse("Filter should be invalid when maxItems is less than 1", filter.isValid())
    }

    @Test
    fun `filter with maxItems greater than 10000 is invalid`() {
        val filter = GallerySyncFilter(
            maxItems = 10001
        )
        assertFalse("Filter should be invalid when maxItems is greater than 10000", filter.isValid())
    }

    @Test
    fun `filter with valid maxItems is valid`() {
        val filter = GallerySyncFilter(
            maxItems = 500
        )
        assertTrue("Filter should be valid with maxItems in valid range", filter.isValid())
    }

    @Test
    fun `filter getSummary returns No filters when all null`() {
        val filter = GallerySyncFilter.default()
        assertEquals("Summary should be 'No filters' for default filter", "No filters", filter.getSummary())
    }

    @Test
    fun `filter getSummary includes date range when set`() {
        val now = System.currentTimeMillis()
        val filter = GallerySyncFilter(
            startDate = now - 86400000,
            endDate = now
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include 'Date range'", summary.contains("Date range"))
    }

    @Test
    fun `filter getSummary includes workflow name when set`() {
        val filter = GallerySyncFilter(
            workflowNameFilter = "Test Workflow"
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include workflow name", summary.contains("Workflow: Test Workflow"))
    }

    @Test
    fun `filter getSummary includes media type when set`() {
        val filter = GallerySyncFilter(
            mediaType = GallerySyncFilter.MediaType.IMAGE
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include media type", summary.contains("Type: IMAGE"))
    }

    @Test
    fun `filter getSummary includes server when set`() {
        val filter = GallerySyncFilter(
            serverFilter = "192.168.1.100:8188"
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include server", summary.contains("Server: 192.168.1.100:8188"))
    }

    @Test
    fun `filter getSummary includes max items when not default`() {
        val filter = GallerySyncFilter(
            maxItems = 500
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include max items", summary.contains("Max: 500"))
    }

    @Test
    fun `filter getSummary combines multiple filters`() {
        val now = System.currentTimeMillis()
        val filter = GallerySyncFilter(
            startDate = now - 86400000,
            endDate = now,
            workflowNameFilter = "Test Workflow",
            fileNameFilter = "temp",
            mediaType = GallerySyncFilter.MediaType.IMAGE
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include date range", summary.contains("Date range"))
        assertTrue("Summary should include workflow name", summary.contains("Workflow: Test Workflow"))
        assertTrue("Summary should include filename", summary.contains("File: temp"))
        assertTrue("Summary should include media type", summary.contains("Type: IMAGE"))
    }

    @Test
    fun `filter isActive when fileNameFilter is set returns true`() {
        val filter = GallerySyncFilter(
            fileNameFilter = "test_file"
        )
        assertTrue("Filter should be active when fileNameFilter is set", filter.isActive())
    }

    @Test
    fun `filter getSummary includes filename when set`() {
        val filter = GallerySyncFilter(
            fileNameFilter = "test_file"
        )
        val summary = filter.getSummary()
        assertTrue("Summary should include filename", summary.contains("File: test_file"))
    }

    @Test
    fun `test matches helper with simple strings`() {
        assertTrue(GallerySyncFilter.matches("test_file.png", "test"))
        assertTrue(GallerySyncFilter.matches("test_file.png", "FILE"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "other"))
    }

    @Test
    fun `test matches helper with wildcards`() {
        assertTrue(GallerySyncFilter.matches("test_file.png", "test*"))
        assertTrue(GallerySyncFilter.matches("test_file.png", "*.png"))
        assertTrue(GallerySyncFilter.matches("test_file.png", "*file*"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "*.jpg"))
    }

    @Test
    fun `test matches helper with exclusions`() {
        assertTrue(GallerySyncFilter.matches("test_file.png", "-*.jpg"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "-*.png"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "-*file*"))
    }

    @Test
    fun `test matches helper with multiple terms`() {
        assertTrue(GallerySyncFilter.matches("test_file.png", "other;test*"))
        assertTrue(GallerySyncFilter.matches("test_file.png", "*.jpg;*.png"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "*.jpg;*.gif"))
    }

    @Test
    fun `test matches helper with inclusions and exclusions combined`() {
        // Must match an inclusion AND NOT match any exclusion
        assertTrue(GallerySyncFilter.matches("test_file.png", "test*;-*.jpg"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "test*;-*.png"))
        
        // Only exclusions: return true if not excluded
        assertTrue(GallerySyncFilter.matches("test_file.png", "-*.jpg;-*.gif"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "-*.jpg;-test*"))
    }

    @Test
    fun `test matches helper with explicit plus sign`() {
        assertTrue(GallerySyncFilter.matches("test_file.png", "+test*"))
        assertFalse(GallerySyncFilter.matches("test_file.png", "+other*"))
    }
}
