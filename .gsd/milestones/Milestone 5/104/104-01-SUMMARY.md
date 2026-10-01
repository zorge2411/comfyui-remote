# Plan 104.1 Summary: Filter, Remove and Metadata

**Completed:** 2026-09-28.

## Delivered

- **Local filter:**
  - `domain/GalleryView.apply(listings, filter, zone)` filters by an inclusive date range (the end runs to the end of that day), workflow and file name (the existing `GallerySyncFilter.matches` wildcards) and media type, then sorts newest, oldest or by name.
  - `MainViewModel.galleryMedia` combines stored media and the filter.
  - `setGalleryFilter` replaces `syncGalleryWithFilter`: no wipe, no filtered sync. Sync no longer skips items by workflow, file name or type.
  - `saveCurrentGalleryList(name, filter)` saves the given filter as a live list.
  - `applySavedFilter` applies the filter for both list types (D-03).
- **`GallerySyncFilter`:**
  - `isActive()` ignores `serverFilter`;
  - new `isSorted()`;
  - `getSummary()` drops server and max items and adds the sort.
  - The fields stay, so older saved lists load.
- **Remove from gallery (D-02):**
  - `GeneratedMediaEntity.hidden`, with Room 13 → 14 (`MIGRATION_13_14`).
  - DAO: the listings and `getAll` exclude hidden rows; new `hide`, `unhideAll`, `hiddenCount` and `deleteVisible`. `getAllPromptIds` still includes hidden rows, so sync skips them.
  - View model: `removeFromGallery(ids)` replaces `deleteMedia`, plus `restoreRemovedMedia()` and `removedMediaCount`. `clearAndRefreshHistory` deletes only visible rows.
- **Metadata:** `domain/MediaInfo.from(promptJson, parser, metadata)` returns the prompt, negative and main settings via `FormLayout` (labels as in the form); `MainViewModel.mediaInfo(id)`.
- **Sync error:** `syncError` is set when `syncHistory` fails and cleared when a sync starts, plus `clearSyncError()`.
- **Interim UI changes:** the old gallery, viewer and save-dialog calls now point at the new functions until 104.2 and 104.3 replace those screens.

## Deviations

- **`MediaInfo.from` signature:** it takes a `WorkflowParser` rather than a parse lambda. The parser is a plain class, so tests use it directly.
- **Tests changed in `GallerySyncFilterTest`:**
  - "isActive when serverFilter is set" now asserts the opposite (server is ignored);
  - the server and max-items summary tests are replaced by one asserting both are left out;
  - added tests for the sort alone, and for the summary including the sort.
- **Regression during execution:** a scripted block replacement removed about 150 lines of the view model that followed the saved-list code (saved-list rename, delete and duplicate, `ExecutionProgress`, theme and connection functions). The build caught it, and the lines were restored verbatim from `HEAD`. The final diff was checked to contain only the intended removals.

## Verification

- `testDebugUnitTest`: 227 tests, 0 failures (new: `GalleryViewTest` 5, `MediaInfoTest` 2, plus the `GallerySyncFilterTest` changes).
- `assembleDebug` OK.
- The migration is checked on the device in 104.3.
