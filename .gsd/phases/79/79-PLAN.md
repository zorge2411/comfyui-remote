# Phase: Gallery Sync Filtering with Saved Lists

## Goal Description

Implement manual gallery sync filtering with the ability to save filtered lists on the device. Users can define custom filters (date range, max items, workflow name, media type) and save either the current filtered results as a snapshot or the filter configuration for future re-sync.

**Research Findings** (see RESEARCH_gallery_sync_filtering.md):

- Current `syncHistory()` supports date range and max items filtering
- Data model: `GeneratedMediaEntity` with `workflowName`, `timestamp`, `mediaType`, `serverHost`, `serverPort`
- Recommended storage: DataStore with JSON serialization for saved lists
- Two save modes: Snapshot (media IDs) and Live Filter (filter params)

## User Review Required

**UI Design Decision**: Confirm preferred UI approach for saved lists display:

- Option A: Bottom sheet overlay from gallery screen
- Option B: Separate "Saved Lists" tab in bottom navigation
- Option C: Long-press menu on gallery items with "Save to List"

## Proposed Changes

### 1. Core Filter State & Data Classes

**Create** `app/src/main/java/com/example/comfyui_remote/data/GallerySyncFilter.kt`:

- Data class with `startDate`, `endDate`, `maxItems`, `workflowNameFilter`, `mediaType`, `serverFilter`, `sortOrder`
- `isActive()` method to check if any filters applied
- Validation logic (e.g., startDate < endDate)

**Create** `app/src/main/java/com/example/comfyui_remote/data/SavedGalleryList.kt`:

- Data class with `id`, `name`, `filter`, `itemCount`, `savedItemIds` (for snapshots), `savedAt`, `listType` (SNAPSHOT vs LIVE_FILTER)

### 2. Enhanced Sync Logic

**Modify** `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`:

- Add `GallerySyncFilter` StateFlow for current filter state
- Add `savedGalleryLists` StateFlow for persisted lists
- Extend `syncHistory()` to accept full `GallerySyncFilter` parameter
- Add `syncGalleryWithFilter()` method
- Add `saveCurrentGalleryList(name, listType)` method
- Add `applySavedFilter(listId)` method
- Add `deleteSavedGalleryList(listId)` method

### 3. DataStore Persistence

**Create** `app/src/main/java/com/example/comfyui_remote/data/SavedGalleryListRepository.kt`:

- DataStore dependency for JSON serialization
- `saveList(list: SavedGalleryList)`
- `getAllLists(): Flow<List<SavedGalleryList>>`
- `deleteList(id: String)`
- `getListById(id: String): SavedGalleryList?`

### 4. Filter Dialog UI

**Create** `app/src/main/java/com/example/comfyui_remote/ui/GalleryFilterDialog.kt`:

- Full-screen dialog with sections:
  - Date range picker (reuse existing date picker components)
  - Max items slider (10-5000 range)
  - Workflow name autocomplete (populate from existing workflows)
  - Media type chips (All, Images, Videos)
  - Server selection (if multiple servers configured)
- "Preview Filter" button to show item count before syncing
- "Sync with Filter" primary action button
- "Save as List" secondary action button

**Modify** `app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt`:

- Add filter button to top bar (next to refresh)
- Add filter indicator when active filters applied
- Launch `GalleryFilterDialog` on filter button click

### 5. Save List Dialog

**Create** `app/src/main/java/com/example/comfyui_remote/ui/SaveListDialog.kt`:

- List name input field
- "Save Mode" toggle: "Current Items (Snapshot)" vs "Live Filter"
- Item count preview
- Validation: Name required, max length 50 chars

### 6. Saved Lists UI

**Create** `app/src/main/java/com/example/comfyui_remote/ui/SavedListsDrawer.kt` (or BottomSheet):

- List of saved lists with swipe-to-delete
- Each item shows: name, item count, filter summary, age indicator
- Tap to apply filter/sync
- Long-press for options: Rename, Delete, Duplicate
- Empty state with "Create your first list" CTA

**Modify** `app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt`:

- Add saved lists drawer trigger button
- Integrate drawer into gallery scaffold

### 7. Filtered Display Enhancement

**Modify** `app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt`:

- Add active filter chips below top bar
- Show "N items filtered" indicator
- Clear all filters button in filter chip bar

### 8. Settings Integration (Optional)

**Modify** `app/src/main/java/com/example/comfyui_remote/ui/SettingsScreen.kt`:

- Add "Manage Saved Lists" section
- Bulk delete saved lists
- Export/import saved lists (JSON)

## Verification Plan

### Automated Tests

**Create** `app/src/test/java/com/example/comfyui_remote/GallerySyncFilterTest.kt`:

```kotlin
// Filter validation tests
@Test fun `filter isActive when any field set returns true`
@Test fun `filter isActive when all null returns false`
@Test fun `filter with endDate before startDate is invalid`

// ViewModel tests
@Test fun `syncGalleryWithFilter applies all filter parameters`
@Test fun `saveCurrentGalleryList creates snapshot with media IDs`
@Test fun `saveCurrentGalleryList creates live filter without IDs`
@Test fun `applySavedFilter restores filter state and triggers sync`
@Test fun `deleteSavedGalleryList removes from repository`
```

**Create** `app/src/test/java/com/example/comfyui_remote/SavedGalleryListRepositoryTest.kt`:

```kotlin
@Test fun `saveList persists to DataStore`
@Test fun `getAllLists returns flows updates on change`
@Test fun `deleteList removes from storage`
@Test fun `getListById returns correct list`
```

### Manual Testing Steps

1. **Filter Application**:
   - Open Gallery → Tap Filter button → Set date range → Sync
   - Verify only items in date range displayed
   - Verify "N items" indicator matches count

2. **Save Snapshot**:
   - Apply filters → Save as "My Snapshot" (Snapshot mode)
   - Clear filters → Open Saved Lists → Tap saved list
   - Verify exact same items displayed (even if new items added to server)

3. **Save Live Filter**:
   - Apply filters → Save as "My Filter" (Live Filter mode)
   - Wait for new server items → Open Saved Lists → Tap saved list
   - Verify new matching items included in results

4. **Delete List**:
   - Create saved list → Swipe to delete → Confirm
   - Verify list removed from UI and storage

### Build Verification

```bash
./gradlew :app:build
./gradlew :app:testDebugUnitTest
```

## Success Criteria

- [ ] Users can define custom sync filters via dialog UI
- [ ] Filter state persists across configuration changes
- [ ] Users can save current filtered results as named snapshot lists
- [ ] Users can save filter configurations as named live filter lists
- [ ] Saved lists are persisted to device storage (survive app restart)
- [ ] Users can view, apply, and delete saved lists via UI
- [ ] Active filters display as dismissible chips in gallery
- [ ] All existing gallery functionality remains intact
- [ ] Unit tests cover filter logic and repository operations
- [ ] No ANRs or memory leaks during filter sync operations
