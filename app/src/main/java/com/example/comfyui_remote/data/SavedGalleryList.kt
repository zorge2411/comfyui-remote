package com.example.comfyui_remote.data

import java.util.UUID

/**
 * Represents a saved gallery list that can be either a snapshot of current items
 * or a live filter configuration for future syncs.
 */
data class SavedGalleryList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filter: GallerySyncFilter,
    val itemCount: Int,
    val savedItemIds: List<Long> = emptyList(), // For SNAPSHOT type only
    val savedAt: Long = System.currentTimeMillis(),
    val listType: ListType = ListType.LIVE_FILTER
) {
    enum class ListType {
        /**
         * Snapshot: Saves the exact list of media IDs at the time of saving.
         * The list remains static even if new items are added to the server.
         */
        SNAPSHOT,

        /**
         * Live Filter: Saves the filter configuration only.
         * When applied, it re-syncs with the server using the saved filter parameters.
         */
        LIVE_FILTER
    }

    /**
     * Get a human-readable age string for when this list was saved.
     * @return A string like "2 hours ago", "3 days ago", etc.
     */
    fun getAgeString(): String {
        val now = System.currentTimeMillis()
        val diff = now - savedAt

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            days < 30 -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
            else -> "${days / 30} month${if (days / 30 > 1) "s" else ""} ago"
        }
    }

    /**
     * Get a description of the list type.
     * @return A string describing whether this is a snapshot or live filter
     */
    fun getTypeDescription(): String {
        return when (listType) {
            ListType.SNAPSHOT -> "Snapshot ($itemCount items)"
            ListType.LIVE_FILTER -> "Live Filter"
        }
    }

    /**
     * Check if this list is a snapshot.
     */
    fun isSnapshot(): Boolean = listType == ListType.SNAPSHOT

    /**
     * Check if this list is a live filter.
     */
    fun isLiveFilter(): Boolean = listType == ListType.LIVE_FILTER

    companion object {
        /**
         * Create a snapshot list from a list of media IDs.
         */
        fun createSnapshot(
            name: String,
            filter: GallerySyncFilter,
            itemIds: List<Long>
        ): SavedGalleryList {
            return SavedGalleryList(
                name = name,
                filter = filter,
                itemCount = itemIds.size,
                savedItemIds = itemIds,
                listType = ListType.SNAPSHOT
            )
        }

        /**
         * Create a live filter list from a filter configuration.
         */
        fun createLiveFilter(
            name: String,
            filter: GallerySyncFilter,
            itemCount: Int
        ): SavedGalleryList {
            return SavedGalleryList(
                name = name,
                filter = filter,
                itemCount = itemCount,
                listType = ListType.LIVE_FILTER
            )
        }
    }
}
