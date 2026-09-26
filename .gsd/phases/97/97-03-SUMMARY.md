# Plan 97.3 Summary: Download Missing Models from the App

**Code completed:** 2026-09-26. Device check pending.

## Delivered

**API and view model** (`bd18a49`)
- `ComfyApiService`: the five `remote_helper` routes. Start returns `Response<JsonObject>`, so error bodies can be shown.
- `MainViewModel` state:
  - `helperAvailable` and `helperHasToken`, checked on every change to CONNECTED; running jobs are restored from `/remote_helper/models/downloads` at the same time;
  - `modelDownloads`, keyed by `directory/filename`, updated by the new `remote_helper.download` branch in `handleMessage`;
  - `modelsVersion`.
- `MainViewModel` functions:
  - `missingModels(workflow)` checks the stored links against `/models/{dir}`, cached per folder; the cache is cleared on reconnect and when a download finishes;
  - `probeModel`, `downloadModel` and `cancelModelDownload`. A start error (400/403/409 or network) is shown on that model's row, not as an execution error.
- When a download finishes: the folder cache is cleared, `fetchNodeMetadata()` runs and `modelsVersion` is bumped, so the card and the pre-flight warnings update without reconnecting.

**UI** (`f3b7f66`)
- A `MissingModelsCard` sits under the Missing Nodes banner. Each row shows the file name and `models/<dir>`, with:
  - **Download**, which opens a confirm dialog with size, folder and a gated warning;
  - a progress bar with MB/GB and **Cancel**;
  - the error text with **Retry**;
  - **Copy link** plus an install hint when the extension is absent.
- `PreflightDialog` shows "N missing model files can be fetched from the Missing Models card".

## Verification

- `testDebugUnitTest`: 192 tests, 0 failures. `assembleDebug` OK.
- **Device check pending** (no device was on adb). Steps are in 97-03-PLAN.md Task 3:
  1. install `server/comfyui_remote_helper` in the server's `custom_nodes` and restart;
  2. `installDebug`; the database upgrades from 11 to 12 with every workflow kept;
  3. re-import a template whose models are missing → the card lists them;
  4. download a small model → progress, then the row and the pre-flight warning clear;
  5. cancel a large download → no `.part` file left;
  6. a gated model without HF_TOKEN → the gated message;
  7. without the extension → Copy link and the hint.
