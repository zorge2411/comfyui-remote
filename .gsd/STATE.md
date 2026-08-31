# Mission Control State

## Current Position

- **Phase**: 81 (Image-to-Image Workflow Node Support)
- **Status**: 🔄 In Progress — 3rd bug found and fixed, installDebug pending device reconnect
- **Session Goal**: Live testing found a 3rd, more fundamental img2img bug (LoadImage's image field never showed the picker on real servers); fixed, needs install + retest

## Achievements

- [x] Implemented Phase 85 (Fix Subgraph Flattening Output-Link Bug)
  - [x] Fixed `isSubgraph` detection in both `expandGraphOnce` and `convert()`'s pre-scan to key off `definitions.containsKey(type)` instead of requiring `properties.proxyWidgets`
  - [x] Added 2 permanent synthetic regression tests to `GraphToApiConverterSubgraphTest.kt` (now tracked in git for the first time)
  - [x] Verified against the real failing workflow via a throwaway test + live `/prompt` POST — original `video`/`IMAGE` type-mismatch error confirmed gone (resolves to `CreateVideo`, not `LoadImage`); throwaway test and scratch files deleted before commit, nothing sensitive touched the repo
  - [x] Full `testDebugUnitTest` suite green, `assembleDebug` succeeds
  - [x] `installDebug` succeeded (Fairphone 6)
- [x] Implemented Phase 81 (Image-to-Image Workflow Node Support) — code portion
  - [x] **Fixed the real blocker**: `WorkflowParser.kt` checked combo/dropdown metadata before checking for `LoadImage`'s own image field — real ComfyUI servers always declare `LoadImage`'s "image" input as a combo of existing server files, so that branch always won, silently disabling the gallery/camera picker entirely. Found via live device screenshot showing a plain filename+dropdown instead of a thumbnail. Fixed by reordering the checks; added a regression test reproducing the real metadata shape. This was very likely the actual reason img2img never worked, more fundamental than the two bugs below.
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

- **2026-08-31**: Phase 82 context gathered (see `.gsd/phases/82/82-CONTEXT.md`) — locked topology-based positive-prompt detection, absolute-top-of-form ordering, no-reorder fallback on ambiguity. Ready for `/gsd:plan-phase 82`.
- Phase 80 added: Improve android icon
- Phase 81 added: Image-to-image workflow node support (was open TODO, 2026-01-30)
- Phase 82 added: Prompt field ordering — positive prompt must be topmost (was open TODO, 2026-01-30)
- Phase 83 added: Camera capture for gallery add-image (was open TODO, 2026-01-24)
- Phase 84 added: Real progress indicator (was unchecked Nice-to-Have, never phased)
- Phase 85 added: Fix subgraph flattening output-link bug (discovered during Phase 81 live testing, 2026-08-25 — HTTP 400 "video/IMAGE mismatch" on `SaveVideo` node when a phantom subgraph node gets bypassed)
- Phase 86 added: Support `COMFY_AUTOGROW_V3` dynamic input type (discovered during Phase 85's live proof-of-fix, 2026-08-25 — `ComfyMathExpression`'s dynamic `values.a`/`values.b`... inputs aren't understood by the app's input-mapping logic)

## Next Steps

1. **Phase 81**: 3 bugs fixed now (parser branch ordering — the real blocker, upload race condition, silent upload failure). `assembleDebug`/`testDebugUnitTest` green, code committed. **`installDebug` pending** — device disconnected before this last fix could be installed. Once reconnected: install, reopen the `LoadImage` field, confirm it now shows the gallery/camera picker (not a dropdown), pick a real image, confirm Generate gates correctly during upload, confirm the executed result uses the picked image.
2. **Phase 80 wrap-up**: Themed-icon retinting under Android 13+ Material You wasn't interactively spot-checked in Settings — optional manual confirmation if desired.
3. **Phase 86**: Study `COMFY_AUTOGROW_V3`'s schema shape (ideally across more than one example node) before planning a parsing approach.
4. **Phase 82**: Plan and implement prompt field ordering fix.
5. **Phase 83**: Plan and implement camera capture for gallery add-image.
6. **Phase 84**: Plan and implement real progress indicator.
7. **Verify** all recent completions once more on a physical device (Manual verification of UI layout).
