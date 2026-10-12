# Phase 99 Context: Model Download Queue

**Gathered:** 2026-09-26

## User choices

- Features: **Download all missing**; a **downloads list**; **reorder and remove** queued items; **retry** failed or cancelled items.
- Location: a **"Model downloads" section on the existing Queue screen**.
- Persistence: **memory only**. A ComfyUI restart clears the queue, as today.
- **Notifications**: progress while minimized, then a finished summary.

## Starting point

- Extension v1 (`server/comfyui_remote_helper`) runs downloads one at a time, FIFO, through an `asyncio.Queue`. There's no reorder, retry or clear. Progress goes out as `remote_helper.download`.
- The app's download state lives in `MainViewModel`:
  - `ModelDownload`, `modelDownloads`, `helperAvailable`, `checkModelHelper`, `downloadModel` and `cancelModelDownload`, around `MainViewModel.kt:751-901`;
  - the `handleMessage` branch at around `:1046`;
  - the CONNECTED hook at around `:1878`.
- `QueueScreen(viewModel: QueueViewModel, onBack)` (`ui/QueueScreen.kt`) is a single `LazyColumn` of `QueueItemCard` rows. Its only row action is delete, and it has no section headers.
- `ExecutionService` has a single channel, `comfy_connection_channel`, and notification id 1. It only observes `connectionRepository.connectionState`.
  - `POST_NOTIFICATIONS` is declared in the manifest but never requested at run time.
- `ComfyApplication` holds the singletons. `ConnectionRepository` (its own scope, `connectionState`, `messages`) is the pattern for app-scoped state.

## Decisions

- **D-01:** The download state moves to an app-scoped `ModelDownloadRepository` in `ComfyApplication`, shared by the workflow screen, the Queue screen and `ExecutionService`.
  - `MainViewModel` delegates to it through thin pass-throughs.
  - The repository reads host and port from `UserPreferencesRepository`, like `QueueViewModel.buildApiService`.
- **D-02:** Order, retry and clear are done on the server (extension v2), so all clients agree. With v1, the app lists and cancels only, and hides reorder, retry and clear.
- **D-03:** A new websocket event, `remote_helper.queue`, carries the full list whenever jobs or order change. `remote_helper.download` stays for throttled per-job progress.
- **D-04:** Notifications use a new channel `model_downloads` (low importance): progress is id 2 and the finished summary id 3 (auto-cancel, opens MainActivity). On Android 13+, `POST_NOTIFICATIONS` is requested before the first download.
