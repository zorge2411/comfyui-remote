# Plan 102.3 Summary: Queue Tab, Offline Behaviour and Device Check

**Status:** code done 2026-09-29 (commit `4df1035`). **The device check is pending**: no phone was attached over adb.

## Delivered

- **Queue tab (`ui/QueueScreen.kt`):**
  - `AppTopBar("Queue")` with the chip and More ("Clear completed"). No back button, `BottomAppBar` or FAB.
  - Polls the server queue while visible (`DisposableEffect`).
  - **On the server:**
    - The running job shows its name (or "Workflow" + prompt id) with a progress bar, plus the node and step when the job is this app's run (`runningPromptId`); otherwise the bar is indeterminate. Stop is confirmed ("Stop the running job?").
    - Pending jobs show "Waiting · #n" and a remove button.
    - "Clear pending" is confirmed.
    - "The server is idle." or "Not connected." when there are no jobs.
    - A refresh error shows with Retry.
  - **In the app:**
    - Run/Stop is a `FilledTonalButton` in the header;
    - COMPLETED items read "Sent to server";
    - removing an item is confirmed with `ConfirmDialog`;
    - the empty text is "Items you add with Queue on a workflow wait here".
  - **Model downloads:** unchanged.
  - One `EmptyState` ("Nothing waiting") when everything is empty and the app is connected.
- **Offline gating:**
  - **Banner:** `NotConnectedBanner` on Workflows, Gallery, Queue and at the top of the form's status area.
  - **Form:** Generate reads "Not connected" (no spinner) and is disabled.
  - **Queue:** Run is disabled, and server actions are hidden.
  - **Workflows:** pull to refresh does nothing, and Browse templates is disabled (also the empty-state button).
  - **Gallery:** pull to refresh does nothing, "Reload from server" is disabled, and the upload FAB is hidden.

## Deviations

- **Form Queue button:** it stays enabled offline. It only adds to the app's own queue, which is useful before reconnecting. Only Generate is gated. `FormActionBar` got `queueEnabled` and `offlineLabel`.
- **Import dialog:** it has no "From server" option to disable. Server workflows are imported from the list section, which pull to refresh fills.
- **Templates:** gated at the entry (the Browse templates button) rather than inside the screen.

## Verification

- `testDebugUnitTest`: 238 tests, 0 failures. `assembleDebug` OK.
- **Device check (task 3): not run yet.** Needs the phone on adb and unlocked. See `102-VERIFICATION.md`.
