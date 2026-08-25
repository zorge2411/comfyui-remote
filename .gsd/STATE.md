# Mission Control State

## Current Position

- **Phase**: 85 (Fix Subgraph Flattening Output-Link Bug)
- **Status**: ✅ Done (targeted bug fixed; `installDebug` pending device reconnect)
- **Session Goal**: Diagnosed and fixed the subgraph isSubgraph-detection bug against real live-server data; surfaced a second, unrelated gap now tracked as Phase 86

## Achievements

- [x] Implemented Phase 85 (Fix Subgraph Flattening Output-Link Bug)
  - [x] Fixed `isSubgraph` detection in both `expandGraphOnce` and `convert()`'s pre-scan to key off `definitions.containsKey(type)` instead of requiring `properties.proxyWidgets`
  - [x] Added 2 permanent synthetic regression tests to `GraphToApiConverterSubgraphTest.kt` (now tracked in git for the first time)
  - [x] Verified against the real failing workflow via a throwaway test + live `/prompt` POST — original `video`/`IMAGE` type-mismatch error confirmed gone (resolves to `CreateVideo`, not `LoadImage`); throwaway test and scratch files deleted before commit, nothing sensitive touched the repo
  - [x] Full `testDebugUnitTest` suite green, `assembleDebug` succeeds
  - [~] `installDebug` not run this session — no device connected
- [x] Implemented Phase 81 (Image-to-Image Workflow Node Support) — code portion
  - [x] Fixed race condition: Generate/Queue now disabled while an image upload is in flight (`DynamicFormScreen.kt`)
  - [x] Fixed silent upload failure: now reverts state and surfaces an error via new `MainViewModel.reportError()`
  - [x] `assembleDebug`/`testDebugUnitTest`/`installDebug` all verified
  - [~] Live test only exercised `video_minimax_h3_i2v.json` (image-to-video, subgraph), which hit a pre-existing unrelated bug — the plain-`LoadImage` scenario this phase actually targets is still unconfirmed
- [x] Implemented Phase 80 (Improve Android Icon)
  - [x] Replaced `ic_launcher_foreground.xml` node-graph motif with a 6-blade aperture/lens vector, same blue background
  - [x] Added `ic_launcher_monochrome.xml`, wired into `ic_launcher.xml` and `ic_launcher_round.xml` for Android 13+ themed icons
  - [x] Removed unused legacy per-density PNG mipmaps (dead weight on minSdk 26+, confirmed via grep before deletion)
  - [x] `assembleDebug`/`installDebug` verified on physical device (Fairphone 6); icon geometry visually confirmed via rendered SVG preview
- [x] Implemented Phase 79 (Manual Gallery Sync Filtering with Saved Lists)
  - [x] Added `GallerySyncFilter.kt` and `SavedGalleryList.kt` data classes
  - [x] Added `SavedGalleryListRepository.kt` (DataStore-backed)
  - [x] Added `GalleryFilterDialog.kt`, `SaveListDialog.kt`, `SavedListsDrawer.kt`
  - [x] Wired filter/save/apply/delete/rename/duplicate into `MainViewModel` and `GalleryScreen`
  - [x] Unit tests: `GallerySyncFilterTest` (25/25), `SavedGalleryListRepositoryTest` (12/12)
  - [x] Verified `assembleDebug` build success
- [x] Implemented Phase 78 (Clear Gallery and History View to Match Server)
- [x] Implemented Phase 77
  - [x] Updated `MainViewModel.syncHistory` to support `maxItemsOverride`.
  - [x] Added `FilterChip` quick-sync row to `GalleryScreen`.
  - [x] Implemented presets for 50, 500, Today, and Deep Sync (2000).
- [x] Implemented Phase 76 (Configurable Sync History Limit)
  - [x] Added `max_sync_items` preference (default 100).
  - [x] Added UI slider in Settings (Range 100-2000).
  - [x] Updated sync logic to respect user preference.
- [x] Debugged Metadata Logs (Confirmed harmless).
- [x] Implemented Phase 75 (MP4 Video Support)
  - [x] Added `coil-video` for thumbnail frame extraction.
  - [x] Updated `MainViewModel` sync logic to recognize `gifs` and `videos` node output keys.
  - [x] Refactored `ShareUtils` to support sharing any media file directly via binary stream (binary-safe).
  - [x] Updated `StorageUtils` with comprehensive video/image MIME types.
  - [x] Enabled loop playback in the full-screen video player.
- [x] Verified Phase 70 (Subgraph Expansion) - New unit tests passed (2/2)
- [x] Verified Phase 73 (Batch Generation) - Implementation confirmed
- [x] Verified Phase 74 (Local Queue) - Navigation gap closed, Logic confirmed
- [x] Cleaned up `ROADMAP.md` (Removed duplicate/corrupted lines)

## Context & Decisions

- **Phase 80**: Replaced the node-graph icon with a 6-blade aperture/lens motif (camera/image-generation theme) rather than refining the old motif. Kept the existing blue background and flat white style. Deleted the legacy per-density PNG mipmaps instead of regenerating them — `minSdk = 26` makes the adaptive-icon XML authoritative on every supported device, and no SVG rasterizer was available in this environment to regenerate accurate replacements anyway.
- **Phase 79**: Filters (date range, max items, workflow name, media type) are captured in `GallerySyncFilter`; saved lists distinguish between a static `SNAPSHOT` (frozen result set) and a `LIVE_FILTER` (re-applies the filter on sync) via `SavedGalleryList.ListType`.
- **Phase 77**: Integrated `FilterChip` presets in the Gallery top section. This provides low-friction access to different sync depths and ranges without menu diving.
- **Phase 76**: User requested configurable sync limit. Implemented as a slider in settings.
- **Phase 75**: Discovered that video workflows (like AnimateDiff) often output to `gifs` or `videos` keys on the server. Updated sync logic to handle these keys. Refactored sharing to be binary-safe (no more Bitmap-only sharing).
- **Phase 70**: Logic was found pre-existing in `GraphToApiConverter`. Verified correctness with a new test suite.

## Recent Updates

- **2026-02-08**: Added### Phase 78: Clear Gallery and History View to Match Server [DONE]
- [x] Research existing `GalleryScreen` and `HistoryScreen` implementation <!-- id: 0 -->
- [x] Investigate `MainViewModel` for sync and clear logic <!-- id: 1 -->
- [x] Define verification criteria for "Clear and Refresh" <!-- id: 2 -->
- [x] Implement "Clear" functionality in `MainViewModel` <!-- id: 3 -->
- [x] Add Refresh action to `GalleryScreen` and `HistoryScreen` with confirmation <!-- id: 4 -->
- [x] Verify functionality via build and manual check <!-- id: 5 -->

## Roadmap Evolution

- Phase 80 added: Improve android icon
- Phase 81 added: Image-to-image workflow node support (was open TODO, 2026-01-30)
- Phase 82 added: Prompt field ordering — positive prompt must be topmost (was open TODO, 2026-01-30)
- Phase 83 added: Camera capture for gallery add-image (was open TODO, 2026-01-24)
- Phase 84 added: Real progress indicator (was unchecked Nice-to-Have, never phased)
- Phase 85 added: Fix subgraph flattening output-link bug (discovered during Phase 81 live testing, 2026-08-25 — HTTP 400 "video/IMAGE mismatch" on `SaveVideo` node when a phantom subgraph node gets bypassed)
- Phase 86 added: Support `COMFY_AUTOGROW_V3` dynamic input type (discovered during Phase 85's live proof-of-fix, 2026-08-25 — `ComfyMathExpression`'s dynamic `values.a`/`values.b`... inputs aren't understood by the app's input-mapping logic)

## Next Steps

1. **Phase 80 wrap-up**: Themed-icon retinting under Android 13+ Material You wasn't interactively spot-checked in Settings — optional manual confirmation if desired.
2. **Phase 85 wrap-up**: Run `installDebug` next time a device is connected (code fix already verified live against the server independent of the device).
3. **Phase 81**: Code done — both bugs fixed (`pendingImageUploads` gate on Generate/Queue; upload failures now revert state + call `viewModel.reportError()`). `assembleDebug`/`testDebugUnitTest` green. Live test only exercised an image-to-video subgraph workflow, which hit the (now-fixed) Phase 85 bug before reaching the img2img upload path. **Still needs**: a plain `LoadImage`-only workflow to actually confirm the button-gating/error-surfacing fixes, if/when one is available — or retry the same MiniMax i2v workflow now that Phase 85 is fixed, since it also exercises a `LoadImage` node.
4. **Phase 86**: Study `COMFY_AUTOGROW_V3`'s schema shape (ideally across more than one example node) before planning a parsing approach.
5. **Phase 82**: Plan and implement prompt field ordering fix.
6. **Phase 83**: Plan and implement camera capture for gallery add-image.
7. **Phase 84**: Plan and implement real progress indicator.
8. **Verify** all recent completions once more on a physical device (Manual verification of UI layout).
