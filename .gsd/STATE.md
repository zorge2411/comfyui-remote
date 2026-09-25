# Mission Control State

## Current Position

- **Milestone**: 4 — Workflow compatibility (planned)
- **Phase**: 89 (Workflow Compatibility Regression Corpus)
- **Task**: Planning complete: 2 plans in 2 waves (`.gsd/phases/89/`)
- **Status**: Ready for execution
- **Previous**: Milestone 3 ended at Phase 88 (bypassed/muted node modes, verified live)

## Achievements (Milestone 3)

- [x] Implemented Phase 88 (bypassed/muted node modes) — found testing the real MiniMax workflow on the phone: bypassed node 18 (SageAttention patch) was sent to the server and failed. Converter now drops muted nodes and rewires bypassed ones to their type-matched upstream input. 5 tests; verified live: the real MiniMax H3 easy i2v workflow now runs end to end from the phone and the video lands in the gallery (also confirms Phases 84, 86, 87 in real use).
- [x] Implemented Phase 87 (MiniMax widget mapping) — `GraphToApiConverter` Mode B: linked widget-inputs consume their `widgets_values` slot (fixed CreateVideo `bit_depth`=24), and invalid combo values from custom-frontend localized labels resolve to valid options (fixed MiniMaxH3Easy). 5 tests; real workflow now passes server validation (`node_errors: {}`).
- [x] Implemented Phase 86 (COMFY_AUTOGROW_V3 support) — `GraphToApiConverter` Mode B now copies linked dotted autogrow slots (`values.a`, `images.image0`) and keeps autogrow keys out of the widget matcher (also fixes a latent bug where `values` stole the next input's widget value, e.g. `StringFormat.f_string`). 4 synthetic tests. Confirmed against the real workflow via Phase 88's live run.
- [x] Implemented Phase 84 (Real Progress Indicator) — new `ExecutionProgressTracker` (nodes completed + step fraction over nodes that will run, excluding `execution_cached`), wired into `MainViewModel.handleMessage()`, single bar + node/step label in `DynamicFormScreen.kt`. 5 unit tests, verified live on Fairphone 6.
- [x] Closed Phase 83 (Camera Capture for Gallery Add-Image) — no code needed: `GalleryScreen.kt` already had a "Take Photo" option with runtime permission handling (built 2026-01-24, roadmap entry never closed). Verified live on Fairphone 6.
- [x] Implemented Phase 82 (Prompt Field Ordering)
  - [x] Added `WorkflowParser.findPositivePromptNodeId()` — finds a sampler-shaped node (has both `positive`/`negative` link inputs) and traces the `positive` link to its source node
  - [x] `WorkflowParser.parse()` hoists the positive-source node's fields to the absolute front of the returned list; falls back to unchanged order when detection is ambiguous (no sampler found, multiple sampler nodes disagree, or the source node has no primitive fields of its own)
  - [x] 4 new unit tests added to `WorkflowParserTest.kt` (topology match, no sampler, ambiguous multi-sampler, source node with no primitive fields) — all passing, full `testDebugUnitTest` suite green
  - [x] `assembleDebug` succeeds
  - [~] Not verified live on device — pure data-transformation logic, fully unit-tested; no device UI check performed
- [x] Implemented Phase 85 (Fix Subgraph Flattening Output-Link Bug)
  - [x] Fixed `isSubgraph` detection in both `expandGraphOnce` and `convert()`'s pre-scan to key off `definitions.containsKey(type)` instead of requiring `properties.proxyWidgets`
  - [x] Added 2 permanent synthetic regression tests to `GraphToApiConverterSubgraphTest.kt` (now tracked in git for the first time)
  - [x] Verified against the real failing workflow via a throwaway test + live `/prompt` POST — original `video`/`IMAGE` type-mismatch error confirmed gone (resolves to `CreateVideo`, not `LoadImage`); throwaway test and scratch files deleted before commit, nothing sensitive touched the repo
  - [x] Full `testDebugUnitTest` suite green, `assembleDebug` succeeds
  - [x] `installDebug` succeeded (Fairphone 6)
- [x] Implemented Phase 81 (Image-to-Image Workflow Node Support) — complete, including full live UAT
  - [x] **Fixed the real blocker**: `WorkflowParser.kt` checked combo/dropdown metadata before checking for `LoadImage`'s own image field — real ComfyUI servers always declare `LoadImage`'s "image" input as a combo of existing server files, so that branch always won, silently disabling the gallery/camera picker entirely. Found via live device screenshot showing a plain filename+dropdown instead of a thumbnail. Fixed by reordering the checks; added a regression test reproducing the real metadata shape.
  - [x] Fixed race condition: Generate/Queue now disabled while an image upload is in flight (`DynamicFormScreen.kt`)
  - [x] Fixed silent upload failure: now reverts state and surfaces an error via new `MainViewModel.reportError()`
  - [x] `/gsd:verify-work 81` — 4 planned tests + 1 organically-discovered check (see `81-UAT.md`): 3/4 planned tests passed outright (upload gating, upload-failure error surfacing, picked image reaches server/executes correctly); 2 gaps found and fixed:
    - [x] **Camera crash**: tapping Camera in the LoadImage picker threw `SecurityException` (runtime `CAMERA` permission never requested, despite being manifest-declared). Fixed in `ImageSelector.kt` by mirroring the existing correct permission-request pattern from `GalleryScreen.kt` (`ContextCompat.checkSelfPermission` → `RequestPermission()` launcher → `launchCamera()`). User-confirmed fixed on device.
    - [x] **Stale metadata cache after process restart**: the camera crash killed the app process; on restart the checkpoint/model dropdown came back empty because `MainViewModel`'s `_nodeMetadata`/`_availableModels` StateFlows were only populated inside the explicit `connect()` call, with no re-fetch on restart. Root-caused by directly querying the live server (`/object_info`, `/models/checkpoints` via curl) to rule out a server-side cause, then confirmed by reproducing via force-stop + reopen. Fixed by moving the fetch calls into the existing `connectionState` collector (already reacting to every `CONNECTED` transition for `syncHistory()`) so any reconnect — automatic or manual — self-heals the cache. User-confirmed fixed on device (force-stop + reopen, no manual reconnect needed).
  - [x] `assembleDebug`/`testDebugUnitTest`/`installDebug` all verified after every fix, including the two post-UAT fixes
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

- **2026-09-25**: Milestone 3 completed and archived (`.gsd/milestones/Milestone 3/`, `Milestone 3-SUMMARY.md`). Milestone 4 created: workflow compatibility, Phases 89–92.

- **2026-08-31**: Phase 82 implemented and verified via unit tests — sampler-topology detection (positive/negative link keys), hoist positive-source node's fields to front, no-op fallback on ambiguity. `testDebugUnitTest`/`assembleDebug` green.
- Phase 80 added: Improve android icon
- Phase 81 added: Image-to-image workflow node support (was open TODO, 2026-01-30)
- Phase 82 added: Prompt field ordering — positive prompt must be topmost (was open TODO, 2026-01-30)
- Phase 83 added: Camera capture for gallery add-image (was open TODO, 2026-01-24)
- Phase 84 added: Real progress indicator (was unchecked Nice-to-Have, never phased)
- Phase 85 added: Fix subgraph flattening output-link bug (discovered during Phase 81 live testing, 2026-08-25 — HTTP 400 "video/IMAGE mismatch" on `SaveVideo` node when a phantom subgraph node gets bypassed)
- Phase 86 added: Support `COMFY_AUTOGROW_V3` dynamic input type (discovered during Phase 85's live proof-of-fix, 2026-08-25 — `ComfyMathExpression`'s dynamic `values.a`/`values.b`... inputs aren't understood by the app's input-mapping logic)
- Phase 87 added: Fix widget value mapping for MiniMax H3 workflow (server validation errors on `CreateVideo.bit_depth` and localized combo labels)
- Phase 88 added: Honor bypassed and muted node modes in graph conversion (discovered 2026-09-24 running the real MiniMax workflow on the phone)

## Next Steps

1. **Execute Phase 89** (`/execute 89`): Plan 89.1 (fixtures and object_info snapshot), then Plan 89.2 (test harness and known-failures baseline). Plan 89.1 may need the user to export `/object_info` from their server if headless ComfyUI can't be installed in the session.
2. **Optional device checks carried over from Milestone 3**:
   - Phase 82: confirm the positive prompt field appears first in a real workflow (unit-tested only).
   - Phase 80: confirm themed-icon retinting under Android 13+ Material You.
