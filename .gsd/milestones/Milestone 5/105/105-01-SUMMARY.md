# Plan 105.1 Summary: Navigation Rail and Haptics

**Completed:** 2026-09-30 (commits `7689bcc` rail, `e11f774` haptics).

## Delivered

- **Rail (D-01), in `MainActivity`:**
  - Landscape (`LocalConfiguration` orientation) shows a `NavigationRail` with the four tabs, vertically centred and with labels.
  - Portrait keeps the `NavigationBar`.
  - Both are hidden on the form, the media viewer and Connection.
  - `Tab.isSelected(route)` is shared by both, as is `navigateToTab`.
- **Insets with the rail:**
  - The rail takes the start system inset.
  - The content gets the Scaffold padding with start = 0, and the start `safeDrawing` inset is marked consumed, so top bars beside the rail don't pad it again.
- **Haptics (D-04):**
  - `ui/components/Haptics.kt`: `rememberHaptics(enabled)` with `click()` (`CONTEXT_CLICK`) and `confirm()` (`CONFIRM` on API 30+, otherwise `LONG_PRESS`), through `View.performHapticFeedback`, which follows the system touch-feedback setting.
  - The form clicks when Generate or Queue actually sends, including "Queue anyway" after the pre-flight dialog.
  - It confirms on EXECUTING/QUEUED → FINISHED while the form is showing.
  - `UserPreferencesRepository.vibration` (default true) and `MainViewModel.vibration`/`setVibration`.
  - Settings › Appearance has a "Vibration" row ("On Generate and when a run finishes") with a switch, min 48 dp.

## Verification

- `testDebugUnitTest`: 251 tests, 0 failures. `assembleDebug` OK.
- The rail and the vibration are checked on the device in 105.3.
