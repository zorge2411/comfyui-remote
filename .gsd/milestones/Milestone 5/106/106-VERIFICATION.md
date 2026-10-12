## Phase 106 Verification

**Device check:** 2026-10-01, Fairphone 6 over USB adb, portrait, light theme. Build `18ee539` installed over the v15 install.

### Results

- **Migration 15 → 16:** PASS.
  - `user_version` 16, `workflows.pinnedFields` present.
  - All 19 workflows and their form values kept.
- **Pinning:** PASS.
  - In Z-Image-Turbo, the CLIPLoader group shows pin icons beside "Clip name", "Type" and "Device"; Main settings (including Model) have none.
  - Pinning "Device" moves it to a new "Pinned" section under the prompt with a filled pin, and CLIPLoader shows "2 inputs".
- **Race fix:** PASS.
  - With "Device" pinned, one Z-Image-Turbo run (user-approved), then a force-stop.
  - The database shows `pinnedFields = ["63/device"]` and `lastImageName` updated to the new result (`z-image-turbo_00044_.png`).
  - After reopening, "Pinned › Device" is still shown.
- **Unpin:** PASS.
  - "Unpin Device" returns it to CLIPLoader ("3 inputs") and removes the Pinned section.
  - The database has `pinnedFields = NULL`.
- **Card → form motion:** PASS.
  - A frame captured right after the tap shows the thumbnail lifted out of the card.
  - The form opens with the preview already set (same image).
  - The user watched it: "Both animated".
- **Preview → viewer motion and Back:** PASS. The user saw the preview grow into the viewer and back.

### Not checked

- **Landscape** for the pin row (the phone stayed in portrait). The pin uses the same `Row` and `IconButton` layout as the rest of the form, which was checked in landscape in Phase 105.

### Automated

- `testDebugUnitTest`: 255 tests, 0 failures (4 new `FormLayoutTest` cases). `assembleDebug` OK.

### Verdict: PASS. Both Milestone 5 nice-to-haves (pinned fields, motion) are done.
