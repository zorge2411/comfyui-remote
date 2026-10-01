# Plan 104.3 Summary: Full-Screen Viewer and Device Check

**Completed:** 2026-09-28. Device check passed, with three fixes made during it (below).

## Delivered

**`b6ef5c4`: the viewer**
- **`MainActivity`:**
  - the tab bar is hidden on `media_detail/*`;
  - the outer Scaffold's padding moved from the `NavHost` onto each destination (`PaddedScreen`), except the viewer, so the gallery doesn't jump during the transition.
- **`MediaDetailScreen`** (rewritten above the kept `VideoPlayer` and `ZoomableImage`):
  - black and edge to edge;
  - `LightSystemBarIcons` sets light status and navigation icons, and restores the theme's setting on exit;
  - a single tap toggles the top bar and the action row;
  - the top bar has Back and the title, plus "Set as wallpaper" in an overflow, for images only;
  - a labelled bottom row: Share, Save, Open in form (disabled without a stored prompt), Info and Remove (destructive `ConfirmDialog`);
  - the Info sheet shows the prompt and negative (6 lines, Show more, Copy), the settings from `MediaInfo` (Copy seed), and file details;
  - the pager follows the gallery's filtered list when the item is in it, and otherwise all media (for example, when opened from the form).
- **URLs:** `domain/MediaUrls.view` builds every `/view` URL with an encoded filename, subfolder and type. It's used by the gallery (`constructUrl`), the viewer, the form's image preview, and the view model's latest-result and restore URLs.
- **`ShareUtils.downloadAndShare(context, url, filename)`:** it takes the name (it can't be read from an encoded URL any more) and reports failure.

## Fixes from the device check

1. **Reload from server emptied the gallery (`72912fb`):**
   - ComfyUI keeps `/history` in memory, and the server had restarted for the helper update, so Reload deleted all 6 visible entries and loaded nothing.
   - Reload now checks the history first (`domain/HistoryCheck`, 3 tests). If it's empty, or the server can't be reached, nothing is deleted and the gallery banner explains why.
   - The confirmation now says that entries the server no longer lists disappear.
   - History's "Clear and Refresh" uses the same function, so it's protected too.
2. **The landscape grid showed one row of three huge cells (`bdffb8b`):** now `GridCells.Adaptive(112.dp)`, which gives 3 columns in portrait and about 7 in landscape. Thumbnails are sized from the short screen side.
3. **Open in form restored the seed as Random (`bdffb8b`):**
   - the restored workflow's `savedInputs` marks its seeds Fixed, so Generate reproduces the item's settings;
   - this also applies to History's Tap to Restore.

## Device check (2026-09-28, Fairphone 6)

1. **Database 13 → 14:** 7 of 7 media and 13 of 13 workflows kept; `hidden` added. ✅
2. **Grid:**
   - the bar has Filter, Saved lists and More, and the FAB doesn't cover the items;
   - long-press gives "1 selected", Select all gives "7 selected", and Back leaves selection mode;
   - Remove 1 is confirmed ("The files stay on the server"); the item stays hidden after pull to refresh;
   - "Restore removed items (1)" brings it back.
   - Reload with an empty server history now keeps the gallery and shows the banner. ✅ (after fix 1)
3. **Filter:**
   - Today and Videos give the chips "Today" and "Videos", the filtered-empty state, and Clear filter;
   - "Save as list" (Today) appears in Saved lists; applying it restores the "Today" chip;
   - Delete is confirmed with a destructive text button, then "No saved lists". ✅
4. **Viewer:**
   - no tab bar; black, edge to edge; light status icons in light theme;
   - tap hides and shows the bars;
   - Info shows the prompt, Seed, Width and Height, with Copy;
   - Open in form gives the same seed (Fixed after fix 3) and prompt;
   - Share opens the system chooser (cancelled);
   - dark theme is correct in the grid and viewer;
   - after leaving the viewer in light theme, the status icons are dark again. ✅
5. **Selection Share** (1 item, through `downloadAndShareMultiple`) opens the chooser (cancelled). ✅
6. **Landscape:** the grid (after fix 2) and the viewer are usable; the selection survives rotation to portrait. ✅

## Not verified on the device

- **Sharing 2+ items at once** (`ACTION_SEND_MULTIPLE`): only one item was visible.
- **The video badge, and no wallpaper option on videos:** the gallery has no videos.
- **The sync-error banner for an unreachable server:** only the empty-history message was seen.

## Data note

- **What was lost:** the Reload test (before fix 1) deleted the app's 6 visible gallery entries. The files are still in the server's output folder.
- **The prepared restore:** a merged database with the 6 entries put back (checked: 7 entries, 13 workflows, integrity OK) is in the session scratchpad.
- **Why it isn't applied:** pushing it into the app's private storage was blocked by a permission check, and is left for the user to decide.

## Verification

- `testDebugUnitTest`: 233 tests, 0 failures.
- `installDebug` OK.
