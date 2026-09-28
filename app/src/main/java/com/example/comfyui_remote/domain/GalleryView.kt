package com.example.comfyui_remote.domain

import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.data.GeneratedMediaListing
import java.time.Instant
import java.time.ZoneId

/**
 * What the gallery grid shows (Phase 104): the stored media narrowed and ordered by the filter, on the
 * phone. Filtering never changes what is stored or synced.
 */
object GalleryView {

    fun apply(
        listings: List<GeneratedMediaListing>,
        filter: GallerySyncFilter,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<GeneratedMediaListing> {
        // The end date is a day: include all of it
        val endExclusive = filter.endDate?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val kept = listings.filter { item ->
            (filter.startDate == null || item.timestamp >= filter.startDate) &&
                (endExclusive == null || item.timestamp < endExclusive) &&
                (filter.mediaType == null || item.mediaType.equals(filter.mediaType.name, ignoreCase = true)) &&
                GallerySyncFilter.matches(item.workflowName, filter.workflowNameFilter) &&
                GallerySyncFilter.matches(item.fileName, filter.fileNameFilter)
        }
        return when (filter.sortOrder) {
            GallerySyncFilter.SortOrder.NEWEST_FIRST -> kept.sortedByDescending { it.timestamp }
            GallerySyncFilter.SortOrder.OLDEST_FIRST -> kept.sortedBy { it.timestamp }
            GallerySyncFilter.SortOrder.NAME_ASC -> kept.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, GeneratedMediaListing::workflowName)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, GeneratedMediaListing::fileName)
            )
        }
    }
}
