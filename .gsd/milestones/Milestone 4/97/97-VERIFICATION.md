## Phase 97 Verification

Checked on 2026-09-26 against the code and tests, not against the summaries.

### Must-Haves

- [x] **Model links survive import.**
  - Evidence: `ModelSources.extract` covers nodes, subgraph definitions and the top-level list (`ModelSourceExtractorTest`, including 2 corpus fixtures).
  - `importWorkflowInternal` stores `modelSources`, filtered by `usedBy`.
- [x] **Migration 11 → 12 exists.**
  - Evidence: `AppDatabase.MIGRATION_11_12` is registered, and the version is 12.
  - Device: the phone database upgraded from 11 to 12 with all 19 workflows kept.
- [x] **The server extension downloads Hugging Face / GitHub models safely.**
  - Evidence: 9 validation tests.
  - Smoke test with a real huggingface.co download: done, 403 for another host, 409 for a duplicate or an existing file, the gated message, cancel leaving no `.part` file.
  - Also run inside the real ComfyUI 0.37.0 server during the device check.
- [x] **Download from the phone with progress; the card and warnings clear.**
  - Code is in place: `MissingModelsCard`, the `remote_helper.download` handler, and a refresh when a download finishes.
  - Device-verified 2026-09-26 (see 97-03-SUMMARY.md, Device check).
- [x] **Without the extension, missing models still show with Copy link.**
  - Code is in place (`helperAvailable == false` branch).
  - Device-verified 2026-09-26 (see 97-03-SUMMARY.md, Device check).

### Commands

- `gradlew.bat testDebugUnitTest assembleDebug`: 192 tests, 0 failures.
- `python -m unittest discover -s server/comfyui_remote_helper -p "test_*.py"`: 9 OK.

### Verdict: PASS. Device check passed 2026-09-26 (7/7; the gated case was checked on the server, not rendered in the app).
