# Plan 102.2 Summary: Tabs, Connection Chip and Settings Tab

**Completed:** 2026-09-29 (commit `4df1035`, together with 102.3's code).

## Delivered

- **Navigation (`MainActivity.kt`):**
  - Four labelled tabs: Workflows, Queue, Gallery, Settings. Default-size icons.
  - The graph starts at `workflows`.
  - Tab clicks use `popUpTo(startDestination) { saveState }`, `launchSingleTop` and `restoreState`.
  - Templates highlights the Workflows tab.
  - The tab bar is hidden on the form, the media viewer and Connection.
  - `navigateToForm` uses `launchSingleTop`.
- **First run:** when `hasSavedServer` is false, the app opens Connection with no Back (Workflows is popped). After connecting it goes to Workflows.
- **`ui/ConnectionScreen.kt`** (moved out of `MainActivity`):
  - `AppTopBar("Connection", onBack)`.
  - Connect is the one filled button; Disconnect is outlined.
  - "Go to workflows" is removed; the screen pops back by itself once the requested connection is up.
- **Removed:** the `history` route, `HistoryScreen.kt`, the pushed `settings` route (Settings is now a tab), and the dead `onViewQueue` parameter.
- **`ConnectionChip`:**
  - a dot in primary/tertiary/error with "Connected", "Connecting…" or "Offline";
  - content description "Server: <label>. Opens connection settings";
  - shared `connectionLabel(state)`.
- **`NotConnectedBanner`:**
  - a Warning with Connection and Reconnect when offline;
  - an Info "Connecting…" with no actions while a connection is being made;
  - nothing when connected.
- **Top bars:**
  - Workflows: the chip and Browse templates; "Sync from server" is pull to refresh.
  - Gallery: the chip, Filter, and More, which now holds Saved lists.
  - Queue: the chip and More.
- **Settings tab:**
  - `AppTopBar("Settings")`, scrolling.
  - Sections: Server (address, status, Connect/Disconnect, "Change server…", "Connect automatically on start"), Appearance, Gallery sync ("Items to load from the server"), Storage.
  - `hasPermission` is removed.

## Deviations

- **Workflows pull to refresh:** a pull-started sync shows the pull indicator instead of the old full-screen overlay, which still shows for imports. Phase 103 replaces the overlay with `LoadingOverlay`.
- **Settings Connect button:** it shows only when a server is saved; otherwise "Change server…" is the way in.

## Verification

`assembleDebug` OK; 238 tests pass. The device check is in 102.3.
