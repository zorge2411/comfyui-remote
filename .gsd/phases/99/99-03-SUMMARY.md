# Plan 99.3 Summary: Queue UI, Download All, Notifications

**Code completed:** 2026-09-26. Device check pending.

## Delivered

**Download all and the Queue section** (`36a8f89`)
- `MissingModelsCard` has **Download all (N)** when the helper is available and at least 2 models aren't already downloading.
  - It probes every model in parallel, then asks "Download N models to the server?".
  - The dialog shows the total size (and how many are of unknown size), the order and the gated or unavailable names.
  - Confirming calls `downloadAllModels`.
- `QueueScreen(viewModel, downloads, onBack)`:
  - a "Model downloads" section, with **Clear finished** on extension v2, then a "Local queue" header;
  - the empty state shows only when both lists are empty;
  - repository errors show in a Snackbar.
- New `ModelDownloadCard`:
  - file name, then "models/dir • #N in queue" (or Downloading, Done, Failed, Cancelled);
  - progress with MB/GB, and the error text on failure;
  - Up/Down on v2, disabled at the ends, and Remove for queued jobs;
  - Cancel while downloading;
  - Retry on v2 for failed or cancelled jobs.
- `formatBytes` moved to `ui/components/Format.kt`.

**Notifications** (`85e7e12`)
- `ExecutionService` has a `model_downloads` channel (low importance) and watches the repository once, even though `onStartCommand` runs on every connect.
- While downloads are active it shows progress (id 2): "Downloading 2 of 5" with "name, 40%" and a progress bar.
- When the queue goes idle it removes the progress notification and posts "Model downloads finished" (id 3, dismissed on tap): "4 models downloaded, 1 failed".
- The service skips notifications when they're disabled, and ignores a `SecurityException`.
- `DynamicFormScreen` asks for `POST_NOTIFICATIONS` (Android 13+) once, after the first Download or Download all.

## Verification

- `testDebugUnitTest`: 197 tests, 0 failures. `assembleDebug` OK.
- **Device check pending:**
  - the server still runs extension version 1 (`/remote_helper/info` → `"1"`);
  - the phone isn't on adb.

  Steps are in 99-03-PLAN.md Task 3.
