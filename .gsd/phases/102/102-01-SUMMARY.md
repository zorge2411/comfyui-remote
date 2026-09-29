# Plan 102.1 Summary: Connection State and Server Queue

**Completed:** 2026-09-29 (commit `b0b0cd9`).

## Delivered

- **Server queue API** (`ComfyApiService`): `getQueue()`, `editQueue(body)` (`{"delete":[ids]}` or `{"clear":true}`), `interrupt(body)` (`{"prompt_id": id}`).
- **`domain/ServerQueue.parse(json, knownNames)`** gives `ServerJob(number, promptId, workflowName, running)`.
  - Running jobs come first, then pending jobs by number.
  - The name comes from `extra_pnginfo.workflow.extra.name`, then from the names the app remembered.
  - Malformed entries are skipped.
- **`data/ServerQueueRepository`**, app-scoped in `ComfyApplication`:
  - it follows `ModelDownloadRepository` and uses `ConnectionRepository.baseUrl`;
  - `jobs`, `lastError`;
  - refresh on CONNECTED, and on `status`/`execution_*`/`executing` websocket messages (debounced 300 ms);
  - `startPolling()`/`stopPolling()` (every 3 s);
  - `cancel`, `interrupt`, `clearPending`;
  - it clears on DISCONNECTED.
- **Job names:** `rememberName(promptId, name)` (a bounded map of 200 entries) is called from `MainViewModel.executeWorkflow` and `QueueViewModel.processQueueItem`.
- **Auto-connect:**
  - `UserPreferencesRepository.autoConnect` (default true) and `saveAutoConnect`;
  - `connect()` sets it, `disconnect()` clears it;
  - after the settings load, the view model connects when a host is saved, auto-connect is on, and the connection is DISCONNECTED or ERROR (so it isn't doubled when the foreground service kept it up).
- **Gate:** `MainViewModel.isConnected`, `isConnecting`, `hasSavedServer` (null until loaded).
- **Removed:** `shouldNavigateToWorkflows`/`onNavigatedToWorkflows` (never set).

## Deviations

- **Extra view-model state:** the same commit also has three additions that 102.2 and 102.3 need:
  - `autoConnect`/`setAutoConnect` for the Settings switch;
  - `runningPromptId`, to match the server's running job to this app's progress events;
  - removal of the History date range (`historyStartDate`/`historyEndDate`, read only by the deleted History screen).
- **Commits:**
  - The shell permission check failed for a stretch of the session, so tasks were built and committed together: one commit for the data layer (this plan) and one for the UI (102.2 and 102.3).
  - The staged deletion of `HistoryScreen.kt` landed in the first commit, so `b0b0cd9` on its own doesn't compile; `4df1035` does.

## Verification

- `testDebugUnitTest`: 238 tests, 0 failures (new: `ServerQueueTest` 5).
- `assembleDebug` OK.
