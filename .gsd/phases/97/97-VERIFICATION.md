## Phase 97 Verification

Checked on 2026-09-26 against the code and tests, not against the summaries.

### Must-Haves

- [x] **Model links survive import.**
  - Evidence: `ModelSources.extract` covers nodes, subgraph definitions and the top-level list (`ModelSourceExtractorTest`, including 2 corpus fixtures).
  - `importWorkflowInternal` stores `modelSources`, filtered by `usedBy`.
- [x] **Migration 11 → 12 exists.**
  - Evidence: `AppDatabase.MIGRATION_11_12` is registered, and the version is 12.
  - Upgrading an existing phone database is still unchecked (device).
- [x] **The server extension downloads Hugging Face / GitHub models safely.**
  - Evidence: 9 validation tests.
  - Smoke test with a real huggingface.co download: done, 403 for another host, 409 for a duplicate or an existing file, the gated message, cancel leaving no `.part` file.
  - Not yet run inside a real ComfyUI.
- [~] **Download from the phone with progress; the card and warnings clear.**
  - Code is in place: `MissingModelsCard`, the `remote_helper.download` handler, and a refresh when a download finishes.
  - Needs the device check.
- [~] **Without the extension, missing models still show with Copy link.**
  - Code is in place (`helperAvailable == false` branch).
  - Needs the device check.

### Commands

- `gradlew.bat testDebugUnitTest assembleDebug`: 192 tests, 0 failures.
- `python -m unittest discover -s server/comfyui_remote_helper -p "test_*.py"`: 9 OK.

### Verdict: PASS (code). Device check pending (97-03-PLAN.md Task 3).
