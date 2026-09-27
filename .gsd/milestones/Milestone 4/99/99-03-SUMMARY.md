# Plan 99.3 Summary: Queue UI, Download All, Notifications

**Completed:** 2026-09-27. Device check passed (see the end of this file).

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
- Device check: passed 2026-09-27 (below).

## Device check (2026-09-27)

Passed on the Fairphone 6 over wireless adb. Server: ComfyUI 0.37.0 with `comfyui_remote_helper` v2 (`/remote_helper/info` → `"2"`). Build `c9a1a14` (`installDebug`).

1. **Download all:** HiDream E1.1 showed "Download all (5)". The dialog said "Download 5 models to the server?", "Total: 46,6 GB", and listed the order. Cancel closed it without queueing.
2. **Per-model downloads:** `clip_g` (1.3 GB confirm), then `t5xxl` and `clip_l` were queued behind it (server positions 1 and 2). `clip_g` finished and dropped off the Missing Models card without reconnecting.
3. **Queue screen:**
   - the "Model downloads" section listed the downloading job with MB/GB, "#1 in queue" and "#2 in queue" with Up, Down and Remove, and the Done job;
   - **Move up** on `llama` (#2 → #1) changed the server order;
   - **Cancel** on the running `t5xxl` started `llama` next, confirming the new order;
   - cancelling `llama` let `clip_l` run to done;
   - **Retry** on the cancelled `t5xxl` re-queued it (it started at once);
   - **Remove from queue** on a waiting job cancelled it on the server and it never ran;
   - **Clear finished** emptied the server list, and the section disappeared.
4. **Notifications:**
   - the Android 13+ permission prompt appeared after the first download, and the user allowed it;
   - minimized during `t5xxl`: id 2 showed "Downloading 2 of 4", "t5xxl_fp8_e4m3fn_scaled.safetensors, 15%", progress 15;
   - when the queue went idle: id 3 showed "Model downloads finished", "2 models downloaded, 2 cancelled" (auto-cancel).
5. **Minimize and reopen:** the app came back with Download buttons and the live queue, not Copy link.
   - This time Android kept the activity, so the recreated-activity race fixed in `233a4fd` wasn't reproduced.
   - That race can no longer happen: the helper check now lives in the app-scoped repository and uses the live connection's address.
6. **Not run:** hiding reorder, retry and clear with extension v1. The server now runs v2.

Result: `clip_g_hidream` and `clip_l_hidream` are now on the server in `models/text_encoders`, 1.3 GB and 235 MB. No `.part` files are expected; the helper deletes them on cancel.
