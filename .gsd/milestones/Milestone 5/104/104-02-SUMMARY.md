# Plan 104.2 Summary: Gallery Grid, Filter Sheet and Saved Lists

**Completed:** 2026-09-28 (code). The device check runs with 104.3.

## Delivered

**Grid (`GalleryScreen.kt`, rewritten)**
- **Top bar:** `AppTopBar("Gallery")` with Filter (a badge dot when a filter or sort is set), Saved lists, and More. More holds "Reload from server" (confirmed; removed items stay removed) and "Restore removed items (N)" (confirmed; shown only when N > 0). The date-picker icon and the old date banner are gone.
- **Selection:**
  - the bar shows "N selected", with Clear, Select all, Share and More (Save to device, Remove from gallery);
  - the selection is in `rememberSaveable` (a `LongArray`), so it survives rotation;
  - `BackHandler` leaves selection mode;
  - Remove is a destructive `ConfirmDialog`: "They're removed from this app's gallery. The files stay on the server."
- **Share and save:**
  - `ShareUtils.downloadAndShareMultiple` sends `ACTION_SEND_MULTIPLE` (a single item falls back to `ACTION_SEND`);
  - Save runs `StorageUtils.saveMediaToFolder` per item and reports "Saved n of m";
  - with no save folder, it says to choose one in Settings;
  - `downloadFile` now fails on HTTP errors instead of saving the error body.
- **Active filter row:** removable `InputChip`s (formatted date range, workflow, file, type, sort) and Clear.
- **Grid layout:** `Dimens` spacing, and 88 dp bottom padding so the FAB never covers the last row.
- **Cells:** a surfaceVariant placeholder; a selected border and check badge in primary roles; the video badge is a 32 dp PlayArrow on `scrim` (50%) with an `inverseOnSurface` icon, so no colour literals.
- **Cell descriptions:** "Image from <workflow>, <date>", with ", selected" when selected.
- **States:**
  - "No images yet";
  - "Nothing matches the filter", with Clear filter;
  - a `StatusBanner(Error)` for `syncError`, with Dismiss and Retry.
- **Kept:** pull to refresh (sync), the Add image FAB with its picker and camera (the dialog in TextButtons; the camera failure is a snackbar, not a Toast), the thumbnail preloading (logging removed) and the shared-element transition.

**Filter sheet (`GalleryFilterDialog.kt` → `GalleryFilterSheet`)**
- A `ModalBottomSheet` with `SectionHeader` sections:
  - Date: Any time, Today, Last 7 days, Last 30 days, and a custom range via `DateRangePickerDialog`;
  - Name: workflow and file name, with a one-line wildcard hint;
  - Type: All, Images, Videos;
  - Sort: Newest, Oldest, Name.
- Buttons: Reset, "Save as list…" (saves the edited filter) and a filled Apply.
- The max-items slider, the server field, the help icons and the sync-with-filter action are removed.
- Shared helpers: `formatDay`, `dateRangeLabel`, `filterParts`, `filterSummary`.

**Saved lists**
- `SavedListsDrawer`:
  - `AppCard` rows showing name, filter summary and age;
  - an overflow with Rename, Duplicate and Delete;
  - Delete uses a destructive `ConfirmDialog` (no filled red button);
  - `EmptyState` when there are none;
  - the type chip is gone.
- `SaveListDialog`: a name and a summary of the filter being saved; Save saves exactly that filter.

## Verification

- `assembleDebug` OK.
- `testDebugUnitTest`: 227 tests, 0 failures.
- No `Color.White/Black/Red/Green` in the gallery files.
