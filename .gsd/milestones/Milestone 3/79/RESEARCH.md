---
phase: gallery-sync-filtering
level: 2
researched_at: 2026-02-08
---

# Manual Gallery Sync Filtering with Saved Lists - Research

## Questions Investigated

1. How does the current gallery sync mechanism work?
2. What filtering capabilities already exist?
3. How can we add manual sync with custom filters?
4. How can we persist filtered lists on the device?

## Findings

### Current Gallery Sync Architecture

The gallery sync is implemented in [`MainViewModel.syncHistory()`](app/src/main/java/com/example/comfyui_remote/MainViewModel.kt:960-1135):

- Fetches history from ComfyUI server via `GET /history?max_items={n}`
- Parses JSON streaming response using Gson JsonReader for memory efficiency
- Filters by existing prompt IDs (skips duplicates)
- Supports date range filtering via `startDate` and `endDate` parameters
- Inserts new items into local Room database via [`GeneratedMediaDao`](app/src/main/java/com/example/comfyui_remote/data/GeneratedMediaDao.kt:1-37)

**Key Data Flow:**

```
Server (ComfyUI History API)
    ↓
MediaRepository.getAllPromptIds() - Check existing
    ↓
Parse & Filter (date range, duplicates)
    ↓
GeneratedMediaDao.insert() - Batch insert
    ↓
Flow<List<GeneratedMediaListing>> to UI
```

### Existing Filter Capabilities

**Date Range Filtering** (already implemented):

- Parameters in `syncHistory(startDate, endDate, maxItemsOverride)`
- Uses timestamp extraction from `prompt[1]` in history JSON
- UI accessible via date picker icon in Gallery top bar

**Max Items Limit**:

- User preference: `userPreferencesRepository.maxSyncItems` (default: 100)
- Override available via `maxItemsOverride` parameter
- Date filtering increases minimum to 1000 items

### Data Model for Gallery Items

[`GeneratedMediaEntity`](app/src/main/java/com/example/comfyui_remote/data/GeneratedMediaEntity.kt:1-24) contains filterable fields:

- `workflowName: String` - Workflow that generated the item
- `timestamp: Long` - Creation time
- `mediaType: String` - "IMAGE" or "VIDEO"
- `serverType: String` - "output" or "input"
- `promptId: String?` - ComfyUI execution ID
- `serverHost: String, serverPort: Int` - Source server

### Current UI State Management

Gallery screen state in [`GalleryScreen.kt`](app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt:64-80):

- `mediaList: Flow<List<GeneratedMediaListing>>` - All gallery items
- `isSyncing: StateFlow<Boolean>` - Sync in progress indicator
- `selectedIds: MutableList<Long>` - Multi-selection for bulk delete
- `historyStartDate/historyEndDate: StateFlow<Long?>` - Date filter state

**Refresh Mechanism:**

- Pull-to-refresh triggers `syncHistory()` with current date range
- "Clear and Refresh" button calls `clearAndRefreshHistory()` which deletes all and re-syncs

## Recommendation: Implementation Approach

### 1. Enhanced Filter Data Class

Add comprehensive filter state to MainViewModel:

```kotlin
data class GallerySyncFilter(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val maxItems: Int = 100,
    val workflowNameFilter: String? = null,
    val mediaType: String? = null,  // "IMAGE" or "VIDEO"
    val serverFilter: String? = null,  // "host:port" format
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST
) {
    enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST, NAME_ASC }
    
    fun isActive(): Boolean = 
        startDate != null || endDate != null || 
        !workflowNameFilter.isNullOrBlank() || 
        mediaType != null || serverFilter != null
}
```

### 2. Saved Lists Architecture

**Option A: DataStore (Recommended for simple lists)**

```kotlin
// Store as JSON list in DataStore
data class SavedGalleryList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filter: GallerySyncFilter,
    val itemCount: Int,
    val previewThumbnailUrl: String?,  // First item thumbnail
    val savedAt: Long = System.currentTimeMillis()
)
```

**Option B: Room Database (For advanced features)**

- New entity `SavedGalleryListEntity` with filter JSON blob
- DAO for CRUD operations
- Migration strategy for future filter additions

### 3. Filter UI Components

**Filter Dialog:**

- Date range picker (reuse existing date components)
- Max items slider (10-5000 range)
- Workflow name autocomplete (from existing workflow names)
- Media type chips (All, Images, Videos)
- Server selection dropdown

**Save List Dialog:**

- List name input
- Preview of item count
- Option to capture current items vs. save filter for future

**Saved Lists UI:**

- Bottom sheet or drawer showing saved lists
- Quick apply button
- Delete/edit actions
- Item count and last synced badge

### 4. Sync Method Enhancement

Extend `syncHistory()` to support all filter types:

```kotlin
fun syncHistoryWithFilter(filter: GallerySyncFilter) {
    // Server-side: max_items param
    // Client-side: workflow name, media type, server filters
    // Database: date range via timestamp comparison
}
```

### 5. Two Save Modes

**Mode A: Save Current Results (Snapshot)**

- Store list of media IDs
- Display even if items deleted from main gallery
- Show "N items (M deleted)" status

**Mode B: Save Filter Configuration**

- Store filter parameters only
- Re-apply sync when loaded
- Show "Last synced: X ago" status

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Storage for saved lists | DataStore JSON | Simple, no migration needed, fits filter data |
| Save mode | Support both snapshot and filter | User flexibility - some want fixed lists, others live filters |
| Filter application timing | Server fetch + client filter | Server API limited, client filtering adds flexibility |
| UI placement | Filter dialog + Saved lists drawer | Consistent with existing date picker pattern |

## Patterns to Follow

- Use existing `PullToRefreshBox` pattern for sync operations
- Follow `MutableStateFlow/StateFlow` pattern for filter state
- Reuse date picker components from `DatePickerDialog.kt`
- Maintain `isSyncing` state during filter sync operations
- Use `rememberSaveable` for dialog state across recompositions

## Anti-Patterns to Avoid

- Don't block UI during filter sync - use existing loading overlay
- Don't store full media entities in saved lists - only IDs or filters
- Don't auto-sync on filter change - require explicit user action
- Don't lose user's filter state on rotation - use `rememberSaveable`

## Dependencies Identified

| Package | Version | Purpose |
|---------|---------|---------|
| androidx.datastore | 1.0.0+ | Persist saved lists |
| kotlinx.serialization.json | 1.6.0+ | Serialize filter objects |
| java.util.UUID | Built-in | Generate list IDs |

## Risks

- **Large list performance**: Saving 1000+ item snapshots may slow loading. Mitigation: Lazy load thumbnails, paginate saved list display.
- **Filter complexity**: Too many options may confuse users. Mitigation: Progressive disclosure (basic filters visible, advanced in expando).
- **Data consistency**: Saved snapshots may reference deleted items. Mitigation: Graceful handling of missing items, show deletion status.

## Ready for Planning

- [x] Questions answered
- [x] Approach selected
- [x] Dependencies identified
- [x] UI patterns established
- [x] Risks mitigated

## Implementation Phases

1. **Core Filter State**: Add GallerySyncFilter data class and ViewModel state
2. **Enhanced Sync**: Extend syncHistory() to support all filter parameters
3. **Filter Dialog UI**: Build filter configuration dialog
4. **Save List Feature**: Add DataStore persistence and save/load dialogs
5. **Saved Lists UI**: Create saved lists management interface
