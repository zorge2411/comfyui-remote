# Plan 100.3 Summary: App-wide Fixes and Reference Screens

**Completed:** 2026-09-28. Device check passed.

## Delivered (`65a0b8e`)

- **Edge-to-edge:** `enableEdgeToEdge()` in `MainActivity.onCreate`. The NavHost uses `padding(innerPadding).consumeWindowInsets(innerPadding)`, so inner Scaffolds and top bars don't add the system bars again.
- The Workflow Architecture sheet and the media Details sheet use `navigationBarsPadding()`, not a hard-coded 32 dp.
- **Connection (reference screen):**
  - `AppTopBar("Connection")`; the body scrolls, with `imePadding`;
  - a new `ConnectionStatus` chip (`ui/components/ConnectionStatus.kt`) in theme colours replaces the `Color.Green/Yellow/Red` dot (`StatusIndicator` deleted);
  - one filled button: "Go to workflows" when connected (with an outlined "Disconnect"), otherwise "Connect";
  - typed `menuAnchor(MenuAnchorType.PrimaryEditable)`.
- **Settings (reference screen):** `SettingsScreen(viewModel, onBack)` with `AppTopBar` and back, a scrolling body, and `AppCard` sections. "Reset folder permission" is now in sentence case.
- **Back arrows:** `Icons.AutoMirrored.Filled.ArrowBack` in DynamicForm, MediaDetail, Queue and Templates.

## Device check (2026-09-28, Fairphone 6, Android 16)

1. **Landscape Connection:** "Go to workflows" and "Disconnect" can be reached by scrolling, with no overlap. ✅
2. **No double top padding:** titles are centred at 206 px (was 316) on Connection, Workflows, Queue, Settings and the form. ✅
3. **Status-bar icons:** dark on the light theme and light on the dark theme. ✅ MediaDetail's black background was not rechecked.
4. The bottom bar and the form's last field aren't covered by the gesture area. ✅
5. Settings has a back button that returns to Execution History, and it scrolls in landscape to "Change Folder". ✅
6. **ErrorCard Retry:** the code fix (a `TextButton`) is in place, but it wasn't triggered on the device (no failing request at hand). ⚠️ partial

Other checks:
- `testDebugUnitTest`: 200 tests, 0 failures.
- The app theme was restored to System, and the user rotated the phone back afterwards.
