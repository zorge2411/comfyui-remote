# Plan 97.3 Summary: Download Missing Models from the App

**Completed:** 2026-09-26. Device check passed (see the end of this file).

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
- Device check steps (97-03-PLAN.md Task 3):
  1. install `server/comfyui_remote_helper` in the server's `custom_nodes` and restart;
  2. `installDebug`; the database upgrades from 11 to 12 with every workflow kept;
  3. re-import a template whose models are missing → the card lists them;
  4. download a small model → progress, then the row and the pre-flight warning clear;
  5. cancel a large download → no `.part` file left;
  6. a gated model without HF_TOKEN → the gated message;
  7. without the extension → Copy link and the hint.

## Device check (2026-09-26)

Passed on the Fairphone 6 over wireless adb. Build from `master` at `4e72038`, installed with `installDebug`. Server: ComfyUI 0.37.0 (Linux) at the user's Tailscale address, running `comfyui_remote_helper` without `HF_TOKEN`.

1. The database upgraded from 11 to 12 in place: all 19 workflows kept, `modelSources` column added, no crash.
2. The build installed.
3. Re-imported templates list their missing models:
   - "Video Upscale: Real-ESRGAN": 1 model (`RealESRGAN_x4plus`, `models/upscale_models`);
   - "HiDream E1.1 Image Editing": 6 models across 3 folders.
4. Real-ESRGAN download:
   - the confirm dialog showed "Size: 63,8 MB" and the folder;
   - the progress bar and Cancel appeared, and the server finished 66,857,836 of 66,857,836 bytes;
   - `/models/upscale_models` now lists the file, and the card disappeared without reconnecting;
   - Generate then gave no pre-flight warning about the model. The server rejected the prompt only because the template's sample input `gan_input.mp4` isn't on the server, which is unrelated.
5. `clip_g_hidream` (1.3 GB), cancelled from the phone at 84 MB: the server job shows `cancelled`, the row returned to Download, and the user confirmed there are no `.part` files on the server.
6. Gated model: tested against the real server directly, because no template model is gated. A FLUX.1-dev probe gave `gated: true`, and the download failed with the HF_TOKEN message.
   - Not seen in the app: the gated warning in the confirm dialog and the error row.
7. Helper removed and ComfyUI restarted:
   - the app reconnected by itself;
   - every row showed Copy link, with the hint "Install comfyui_remote_helper on the server to download from here";
   - Copy link put the Hugging Face URL on the clipboard.

**Noticed (outside this phase):** the Phase 91 pre-flight check didn't warn that the `LoadVideo` input `gan_input.mp4` is missing from the server; only the server caught it.
