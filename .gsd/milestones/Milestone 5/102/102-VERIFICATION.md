## Phase 102 Verification

**Code check:** 2026-09-29. **Device check:** 2026-09-30, on the Fairphone 6 (adb). Builds `ecb2bcc` and `e55479e` (installDebug over the existing install).

### Must-haves

- [x] **Four labelled tabs; graph rooted at Workflows; Back from a tab root goes to Workflows.** VERIFIED on the device:
  - Workflows, Queue, Gallery, Settings, each with a label;
  - Back from Queue and from Gallery goes to Workflows;
  - Back from Workflows leaves the app;
  - Templates keeps the Workflows tab selected.
- [x] **Connection state visible without a dedicated screen.** VERIFIED:
  - The chip shows "Connected" and "Offline", and "Connecting…" while connecting.
  - Settings › Server shows the address, status, Disconnect/Connect and "Change server…".
- [x] **Offline: server actions disabled with a reason.** VERIFIED:
  - after Disconnect, the "Not connected" banner shows with Connection and Reconnect;
  - Browse templates is disabled;
  - Reconnect from the banner connects and turns auto-connect back on.
- [~] **Queue shows the server's jobs; Stop, Cancel and Clear.** PARTLY VERIFIED:
  - With Z-Image-Turbo (batch 2), the server section showed the running job with its node, step ("KSampler · step 5 of 8") and progress bar, plus the pending job as "Waiting · #1". "Clear pending" showed while a job was pending.
  - The app's item read "Batch 2 · Sent to server"; Run is in the "In the app" header; there is no second bottom bar.
  - Remove was tapped on the pending job, but it had already started by then (the server finished both runs), so the removal couldn't be confirmed.
  - Stop: the job finished before the confirmation. Each Z-Image-Turbo run takes about 14 s and each adb round trip 1–2 s, too tight to catch by script.
  - **Open:** a manual check with a longer job.

### Device checks (102.3 task 3)

1. **Cold start with a saved server:** PASS. It connected by itself and opened on Workflows with four labelled tabs.
2. **Back and tab state:** PASS. See above; the form hides the tab bar and Back returns to Workflows.
3. **Chip, Disconnect, banners, Reconnect:** PASS.
4. **Settings:** PASS for the Server section and the tab. Theme and folder weren't changed during the check.
5. **Server queue:** PARTLY PASS (see above).
6. **Restart after Disconnect:** PASS. There was no auto-connect, the banner showed, and there were no badges.
7. **Landscape:** PASS. The tabs and screens are usable. The Workflows list was cramped, fixed in `ecb2bcc` and re-checked (see `103-VERIFICATION.md`). The tab bar's height in landscape is noted for Phase 105.

### Found and fixed during the check

- **Server workflows after a cold start (`e55479e`):**
  - The fetch ran only from `connect()`. If it failed before the network was up, "On the server" stayed empty until a pull to refresh.
  - It now runs on every transition to CONNECTED, without the loading overlay.
  - Verified: after a cold start the section showed "On the server (15)" without a pull.
- **Not an app issue:** the phone's Tailscale link was down once, so the connection timed out while the PC could reach the server. The app kept showing "Connecting…" and retrying, as intended.

### Automated

- `testDebugUnitTest`: 251 tests, 0 failures. `assembleDebug` OK.

### Verdict: PASS, except server-queue Remove/Stop, which is left for a manual check with a longer job.
