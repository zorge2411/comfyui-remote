# Phase 104 Context: Gallery and Media Viewer

**Gathered:** 2026-09-28. Milestone 5 (UI/UX Overhaul). Conventions: `.gsd/phases/100/100-UI-SPEC.md`. Audit rows: `100-AUDIT.md` (Gallery, MediaDetail).

## User decisions (2026-09-28)

1. **Filters: simplify and fix.** One filter sheet (date range, workflow, file name, media type, sort) applied **locally** to the grid, without wiping it. Saved lists become named filters. The server filter and the Snapshot list type are dropped.
2. **Delete becomes "Remove from gallery":** confirmed, and remembered so a sync never brings the item back. Server files are untouched.
3. **Viewer additions (all four):**
   - Open in form (restore the image's settings);
   - prompt and seed in Info;
   - a full-screen viewer (no tab bar, black, light status icons);
   - Share and Save to device in the grid's selection mode.
4. **History tab:** left for Phase 102. 104 only brings restore to the viewer.

## Code survey (read-only, 2026-09-28)

Paths are relative to `app/src/main/java/com/example/comfyui_remote/`.

### `ui/GalleryScreen.kt` (618 lines)

- **Top bar:** a plain `TopAppBar` titled "Gallery", with four icons (`:185-212`): Bookmark (saved lists), FilterList, DateRange and Refresh. The spec allows three.
- **Refresh:** opens "Clear and Refresh?" (`:161-179`), which calls `clearAndRefreshHistory()`.
- **Selection bar:** a primaryContainer bar showing "N Selected", with Close and Delete (`:136-156`). **Delete is not confirmed.**
- **Grid:** `LazyVerticalGrid(Fixed(3))`, 4 dp padding (`:401`), with no bottom padding, so the FAB covers the last row.
- **Cells:** 1:1 `Card`s. The selected state is a 3 dp border with a check badge (`:581`). The video badge uses `Color.White` on `Color.Black` (`:601`).
- **Selection state:** a `mutableStateListOf` in `remember` (`:71`), so it's lost on rotation. There's no BackHandler.
- **FAB:** Add, which opens an "Add Image" dialog (picker or camera) that calls `uploadManualImage` (`:217-258`).
- **Banners:** a filter banner (`:263`) and a date banner that prints raw epoch millis (`:302`). Neither is a `StatusBanner`.
- **States:** `EmptyState` without an action (`:326`). Sync errors are only logged (`MainViewModel.kt:1609`).
- **Debug code:** heavy `GALLERY_DEBUG`/`COIL_DEBUG` logging and a hand-rolled Coil preload (`:343-399`).

### Filters

**`ui/GalleryFilterDialog.kt` (367 lines):** a scrolling AlertDialog.
- **Fields:** date range, where the end is start-of-day, so that day is excluded (`:97`); a max-items slider; workflow and file-name fields with 24 dp help icons; media type and sort chips; a server field.
- **Buttons:** "Save as List", "Sync with Filter" and Cancel.
- **Dead code:** `filter` (`:289`).

**`MainViewModel.syncGalleryWithFilter` (`:101`):**
- it runs `deleteAll()` and then a filtered sync;
- sync skips non-matching items by workflow and file name (`:1491-1545`);
- `sortOrder` and `serverFilter` are never applied;
- the DAO always sorts `timestamp DESC`.

**Saved lists:**
- `applySavedFilter` (`:163`): a Snapshot list sets the filter but never restricts the grid.
- `saveCurrentGalleryList` (`:125`) saves the VM filter, so `SaveListDialog` ignores its `filter` argument.
- `SavedListsDrawer`: a ModalBottomSheet of raw `Card`s, a no-op type chip, and a delete confirmed with a **filled red Button** (`:342`), which spec §6 forbids.

### `ui/MediaDetailScreen.kt` (497 lines)

- **Route and pager:** route `media_detail/{mediaId}` (`MainActivity.kt:312`), with a `HorizontalPager` over `allMedia`.
- **Gestures:** swipe-down to dismiss (images only) and a custom `ZoomableImage` (1–5x, double-tap).
- **Video:** ExoPlayer with `PlayerView`.
- **Top bar:** translucent black, with Back and the workflow name as title; four actions: Share, Delete (confirmed), Info and More (Set as wallpaper, even for videos; Save to device).
- **Info sheet:** file, workflow, type, server, date and subfolder. No prompt or seed.
- **No restore action.**
- **Insets:** inside the NavHost, which the outer Scaffold pads (`MainActivity.kt:253`), so the **tab bar shows in the viewer**. Status-bar icons follow the theme, so they're dark on black in the light theme.
- **URLs:** `buildMediaUrl` (`:95`) duplicates `MediaUtils.constructUrl`, and neither URL-encodes.

### Data

- **`GeneratedMediaEntity`:** `id`, `workflowName`, `fileName`, `subfolder`, `serverHost`, `serverPort`, `timestamp`, `mediaType`, `promptJson`, `promptId` and `serverType`, with a unique index on `(promptId, fileName)`.
- **`GeneratedMediaListing`:** the same fields without `promptJson` and `promptId`. It is what `viewModel.allMedia` exposes.
- **Sync:**
  - `syncHistory` inserts with IGNORE;
  - it skips executions whose `promptId` is already present (`getAllPromptIds`);
  - `clearAndRefreshHistory` runs `deleteAll()`.
- **Delete** (`deleteMedia`, `:1729`) removes local rows only.
- **Restore** exists in History: `loadHistory(listing)` (`:526`) builds a temporary workflow from `promptJson` and opens the form.
- **Tests:** `GallerySyncFilterTest` and `SavedGalleryListRepositoryTest`. Sync, delete and UI are not tested.

### Phase 100 components available (`ui/components/`)

`AppTopBar`, `SectionHeader`, `AppCard`, `StatusBanner`, `ConfirmDialog(destructive)`, `EmptyState` (with an optional action), `ErrorCard`, `LoadingIndicator`, `DateRangePickerDialog` and `Dimens` (`xs`–`xl`, `screenPadding`, `minTouch`).

## Decisions

- **D-01:** Filtering is a pure, tested domain function (`GalleryView.apply`) over `GeneratedMediaListing`. Sync fetches the same way it does without a filter, and never deletes rows because of a filter.
- **D-02:** Removed items keep their row with `hidden = 1` (Room 13 → 14).
  - The listing query excludes them.
  - Because the row and its `promptId` stay, sync skips them, and the unique index blocks re-inserts.
  - "Reload from server" deletes only visible rows.
  - "Restore removed items" unhides them all.
- **D-03:** Existing saved lists still load. A Snapshot list is read as its filter; `savedItemIds`, `maxItems` and `serverFilter` are ignored. There's no stored-data migration.
- **D-04:** The Info metadata reuses Phase 101 parsing: `parseWorkflowInputs` + `FormLayout.build` + `WorkflowParser.findPositive/NegativePromptNodeId`. Labels therefore match the form.
- **D-05:** The viewer's actions go in a labelled bottom row: Share, Save, Open in form, Info, Remove. The top bar has only Back and the title; a single tap toggles both bars; "Set as wallpaper" (images only) sits in an overflow.
- **D-06:** One media URL builder, `MediaUtils.constructUrl`, which URL-encodes the filename and subfolder.

## Out of scope

- The History screen (Phase 102).
- Deleting files on the server (ComfyUI has no API for it).
- Grid column settings.
