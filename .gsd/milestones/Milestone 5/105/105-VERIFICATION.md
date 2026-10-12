## Phase 105 Verification

**Device pass:** 2026-09-30, Fairphone 6 (1116×2484, 480 dpi) over USB adb. Builds from `3cbe0d7` to `8c6844e`.

### Coverage

- **Four tabs (Workflows, Queue, Gallery, Settings):** light and dark × portrait and landscape. All four combinations seen.
- **Secondary screens in dark landscape:**
  - Templates, Import dialog, form (top, scrolled), form node list;
  - Gallery filter sheet, saved lists, selection bar, viewer;
  - Connection.
- **Secondary screens in light:**
  - portrait: Import dialog, Templates;
  - landscape: Templates (after the fix), Import dialog (after the fix), form, Queue with a local item, the Remove confirmation.
  - Light portrait form, queue and viewer were covered on 2026-09-30 in the Phase 102/103 check.
- **Contrast** by eye in both themes:
  - status-bar icons follow the theme;
  - rail, chips, badges, switches and banners are readable;
  - the dark theme uses the Material You surfaces throughout.

### Touch-target scan

A script over each `uiautomator dump` flagged clickable or checkable nodes under 144 px (48 dp). Every finding was a partly visible node:
- **Cut off by the screen or bar edges:**
  - the last card's ⋮ in landscape;
  - the bottom row of gallery cells and template cards;
  - the Connection "Secure" switch at the bottom edge;
  - list items under the tab bar.
- **Scrolled under the top bar:** the Settings Disconnect/"Change server…" buttons and the auto-connect and Vibration switches.

Scrolled into view, each is at least 48 dp: the ⋮ button is 144 × 144 px, and the switches are ≥ 144 px tall in their rows. **Accepted:** no real target is under 48 dp.

### Rail (105.1)

- **Landscape:** the rail shows the four labelled tabs, vertically centred; the selected tab is highlighted, and Templates keeps Workflows selected.
- **Hidden** on the form, the media viewer and Connection.
- **More room:** the list now shows about 1.5 cards, where before it had room for about one.

### Haptics (105.1)

- **First try: nothing felt.**
  - The vibrator log showed the app's click and confirm as usage TOUCH with status `ignored_for_settings`.
  - The phone has system touch feedback off (`haptic_feedback_enabled=0`, TOUCH intensity OFF).
- **User decision:** the app's own switch decides.
- **Fix `8c6844e`:** the Vibrator with `USAGE_MEDIA`, plus the `VIBRATE` permission.
- **Result:**
  - the log shows CLICK (54 ms) on Generate and DOUBLE_CLICK (179 ms) on the result, both `finished`;
  - **the user felt** "buzz at Generate, double buzz at finished";
  - with the Settings switch off, tapping Queue produced no vibration.

### Found and fixed during the pass

- **Templates, landscape (`df4303a`):** the search, chips and count left half a row of cards. They're now the grid's first full-width item and scroll away. Re-checked: whole cards fit.
- **Import dialog, landscape (`df4303a`):** the JSON field was squeezed to one line. The content now scrolls. Re-checked: the field keeps its size.
- **Haptics silent with system touch feedback off (`8c6844e`):** see above.

### Carried-over checks

- **Importing a template:** PASS. Z-Image-Turbo was imported and opened in the form.
  - The extra copy ("Z-Image-Turbo: Text to Image", the newer of the two) is still on the phone for the user to delete; the adb connection dropped before cleanup.
- **Templates reload-error banner in aeroplane mode:** skipped by the user.

### Left for the user after the connection dropped

- Settings › Appearance › **Vibration** was switched off for the check. The last tap to turn it back on may not have landed, so turn it on again if needed.
- Delete the extra imported "Z-Image-Turbo: Text to Image" (More › Delete on the newer copy).

### Automated

- `testDebugUnitTest`: 251 tests, 0 failures. `assembleDebug` OK.
- Code search in `ui/`:
  - no `RoundedCornerShape(`, `FontWeight.Bold` or emoji status marks;
  - `Color.Black`/`White` only in the full-screen viewer.

### Verdict: PASS. The milestone's must-haves "Shared UI building blocks…" and "No screen breaks in landscape; dark mode and touch targets checked on the device" are met.
