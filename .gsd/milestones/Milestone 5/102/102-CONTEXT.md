# Phase 102 Context: Navigation and Structure

**Gathered:** 2026-09-29. Milestone 5 (UI/UX Overhaul). Conventions: `.gsd/phases/100/100-UI-SPEC.md`. Audit: A4 (tab labels), A5 (tab state), and the Connection, Queue and History rows of `100-AUDIT.md`.

## User decisions (2026-09-29)

1. **Tabs:** Workflows, Queue, Gallery, Settings, all labelled.
   - Connection becomes a status chip on the tab top bars (tap it for connection details) and a section in Settings.
   - History is removed.
2. **Startup:** auto-connect to the last server and open on Workflows. The connection screen shows only on first run, or from the chip.
3. **Queue:** also shows the ComfyUI server's own queue (running and pending, with cancel and interrupt), next to the app's local queue and model downloads.
4. **Offline:** a "Not connected" banner with Reconnect; server actions are disabled with a reason instead of failing.

## Code survey (read-only, 2026-09-29)

Paths are relative to `app/src/main/java/com/example/comfyui_remote/`.

### `MainActivity.kt`

- **Tabs:** five icon-only `NavigationBarItem`s (Connection, Workflows, Queue, Gallery, "More" → `history`), with 28 dp icons.
  - The Connection tab uses `popUpTo("connection")` without `saveState`.
  - The other tabs use `popUpTo("connection") { saveState = true }` with `restoreState`.
  - "More" is highlighted on both `history` and `settings`.
- **Start:** `startDestination = "connection"` (`:252`), with no auto-connect.
- **Form navigation:** `navigateToForm` (set by `loadHistory`) navigates to `remote_control` with no options.
- **Dead code:** `shouldNavigateToWorkflows` is never set to true. The form's `onViewQueue` is never called.
- **ConnectionScreen** lives at `MainActivity.kt:367-564`:
  - `AppTopBar("Connection")`, a `ConnectionStatus` chip, host with a server-profiles dropdown, port, and a Secure switch;
  - `connectAction` validates, saves, then runs `connect()` or `disconnect()`;
  - when connected, it shows a filled "Go to workflows" and an outlined "Disconnect".
- **Notifications:** `PendingIntent`s open `MainActivity` with no extras (`ExecutionService.kt:134,151`).

### Connection

- **State:** `connectionState` holds `WebSocketState` (DISCONNECTED, CONNECTING, CONNECTED, ERROR, RECONNECTING).
- **`connect()`** starts the foreground `ExecutionService`, calls `connectionRepository.connect`, then fetches models, metadata and workflows.
- **On CONNECTED:** `syncHistory`, metadata and models.
- **Reconnects:** `ConnectionRepository` retries unless the user disconnected (2 s × 2ⁿ, up to 64 s). There's no lifecycle hook.
- **`settingsLoaded`** only gates server calls.
- **Addresses:** `MainViewModel.buildApiService()` uses the in-memory host and port; `QueueViewModel` reads the saved preferences.
- **`components/ConnectionStatus.kt`** is an AssistChip with icon and label per state, used only on the Connection screen.
- **Disconnected behaviour:** no screen except Connection shows the state.
  - Form: Generate fails with an HTTP exception.
  - Queue: Run fails each item.
  - Workflows: sync fails silently.
  - Gallery: shows `syncError` (Phase 104).

### Queue (`ui/QueueScreen.kt`, `QueueViewModel.kt`)

- **Top bar:** `TopAppBar("Local Queue")` with a back arrow calling `popBackStack`, and "Clear Completed".
- **Bottom bar:** a `BottomAppBar` with a centred Play/Stop FAB (`startQueue`/`stopQueue`), stacked on the tab bar.
- **Sections:**
  - "Model downloads" (`ModelDownloadCard`: move, cancel, retry; header text in black);
  - "Local queue" (`QueueItemCard`, delete confirmed).
- **Statuses:** COMPLETED means sent to the server, not finished.
- **No server queue:** `ComfyApiService` has no `/queue` or `/interrupt`. The live server answers `GET /queue` with `{"queue_running": [], "queue_pending": []}`.
  - ComfyUI's entries are `[number, prompt_id, prompt, extra_data, outputs_to_execute]`.
  - `POST /queue {"delete":[ids]}` or `{"clear":true}` edits the pending queue.
  - `POST /interrupt {"prompt_id": id}` stops that prompt only when it is running.

### History (`ui/HistoryScreen.kt`)

- **Same data as Gallery:** it lists the same `allMedia` rows, one per media item.
- **Only here:**
  - "Tap to Restore" (`loadHistory`), now also the viewer's "Open in form" (Phase 104);
  - a date range (`historyStartDate`/`historyEndDate`) that bounds syncing;
  - the only entry to Settings (a gear).
- **Duplicated:** "Clear and Refresh" (the same `clearAndRefreshHistory` as Gallery's Reload) and pull to refresh.

### Settings (`ui/SettingsScreen.kt`)

- `AppTopBar("Settings", onBack)`, with cards for Appearance (theme), Synchronization (max history items) and Storage (save folder, reset permission).
- Nothing connection-related. `hasPermission` is unused.

### Workflows (`ui/WorkflowListScreen.kt`)

- **Top bar:** an M3 `TopAppBar` with "Browse Templates" and "Sync from Server".
- **FAB:** "+" imports.
- **Templates:** also reachable from the empty state.

### Tests

None for navigation or UI.

## Decisions

- **D-01: the graph is rooted at `workflows`.**
  - Tabs use `popUpTo(startDestination) { saveState = true }`, `launchSingleTop` and `restoreState`.
  - Back from a tab root goes to Workflows; Back from Workflows leaves the app.
  - Pushed screens (form, templates, viewer, connection) have Back.
- **D-02: Connection is a pushed screen** (`ui/ConnectionScreen.kt`, route `connection`).
  - Opened from the chip, from Settings › Server, or on first run.
  - After connecting it pops back, or goes to Workflows on first run.
- **D-03: auto-connect.**
  - After settings load, the app connects when a host is saved and the `autoConnect` preference is true.
  - Disconnect clears the preference; Connect sets it.
  - With no saved host, the start is Connection.
- **D-04: the server queue.**
  - `domain/ServerQueue.parse` is pure and tested.
  - The app-scoped `ServerQueueRepository` refreshes on websocket `status` and `execution_*` messages, and every 3 s while the Queue tab is visible.
  - Cancel pending uses `POST /queue {"delete":[id]}`.
  - Stop running uses `POST /interrupt {"prompt_id": id}`.
  - Clear pending uses `POST /queue {"clear": true}`, confirmed.
  - Job names come from `extra_pnginfo.workflow.extra.name`, or from the app's own `promptId → name` record.
- **D-05: one connection gate.**
  - `MainViewModel.isConnected`, plus `NotConnectedBanner` (a `StatusBanner` Warning with Reconnect).
  - Server actions are disabled with a reason: the form's Generate and Queue, Queue Run and server actions, Workflows sync and import from server, Templates, and Gallery sync.
- **D-06: History is removed** (screen, route, date-range UI state). Settings becomes a tab: Server, Appearance, Gallery sync, Storage.

## Out of scope

- The workflow list's cards and the compatibility badge (Phase 103).
- Deep links from notifications to specific screens.
