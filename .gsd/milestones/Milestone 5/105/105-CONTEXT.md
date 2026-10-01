# Phase 105 Context: Consistency Pass and Device Check

**Gathered:** 2026-09-30. Milestone 5 (UI/UX Overhaul), final must-have phase.
- Conventions: `.gsd/phases/100/100-UI-SPEC.md`.
- Audit: `100-AUDIT.md` (A3 landscape, A6 emoji, Queue row).
- Carried over from the Phase 102/103 device check: `102-VERIFICATION.md`, `103-VERIFICATION.md`.

## User decisions (2026-09-30)

1. **Landscape navigation: a navigation rail.**
   - In landscape the four tabs (Workflows, Queue, Gallery, Settings) move to a Material 3 `NavigationRail` on the left.
   - Portrait keeps the labelled bottom bar.
   - The rail is hidden on the same routes as the bar (form, media viewer, Connection).
2. **Device check: every screen, both themes.**
   - Screenshots of every screen and main state in light and dark, portrait and landscape.
   - An automated touch-target scan: interactive nodes smaller than 48 dp, flagged from `uiautomator` dumps.
   - Contrast checked by eye.
   - Before/after pairs against the Phase 100 audit.
3. **Queue Remove/Stop:** dropped. The partial verification from 2026-09-30 stands: the UI was shown and the requests were wired, but a live Remove/Stop wasn't caught.
4. **Nice-to-haves, split across two phases:**
   - **Haptics in 105:**
     - a light click when Generate or Queue sends a run;
     - a confirm vibration when a run's result arrives while the app is open;
     - a "Vibration" switch in Settings (on by default), which also respects the system's touch-feedback setting;
     - no vibration on failure.
   - **Pinned form fields and motion transitions go to a new Phase 106** (see `.gsd/phases/106/106-CONTEXT.md`), so that 105 closes the milestone's must-haves on its own.

## Code survey (read-only, 2026-09-30)

Paths are relative to `app/src/main/java/com/example/comfyui_remote/`.

- **Top bars:** every tab and pushed screen uses `AppTopBar`, with two deliberate exceptions from Phase 104:
  - the Gallery selection bar (`GalleryScreen.kt:207`);
  - the black full-screen viewer (`MediaDetailScreen.kt:179`, the `Color.Black`/`Color.White` scrims at `:71-135`).
- **Leftovers for the sweep:**
  - `ui/components/IssueList.kt`: `✖`/`⚠` text prefixes (audit A6) should become Outlined icons (ErrorOutline, WarningAmber), as in `StatusBanner`.
  - `ModelDownloadCard.kt` and `DynamicFormScreen.kt`: one `FontWeight.Bold` title each (spec §11 typography).
  - Dialogs not revisited since Phase 104: `SaveListDialog.kt`, `SavedListsDrawer.kt`, `GalleryFilterDialog.kt`, `components/ImageSelector.kt` (one `RoundedCornerShape`), `components/DatePickerDialog.kt`.
  - Check each for title case, outlined fields and destructive colours.
  - Hard-coded `.dp` values outside `Dimens`:
    - `DynamicFormScreen` (10), `ImageSelector` (7), `ErrorCard` (7), `EmptyState` (5), `WorkflowListScreen` (5), `ModelDownloadCard` (4), `GalleryScreen` (4).
    - Replace them where a token fits; keep icon and thumbnail sizes.
- **Navigation:** `MainActivity.kt` has the `TABS` list, `navigateToTab`, and the `Scaffold(bottomBar = { NavigationBar … })`.
  - The rail needs a landscape switch, from `LocalConfiguration` orientation or the window width class.
  - The rail should sit in a `Row` with the NavHost, keeping `PaddedScreen`'s inset handling.
- **FAB and card menus:** the Workflows FAB can cover a card's ⋮ menu in landscape. With the rail and more height this mostly goes away; re-check.
- **Haptics:** none in the app today. `LocalHapticFeedback` (Compose) covers the button click. The run-finished vibration fits where `executionStatus` becomes FINISHED and the form observes it; use `View.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` (API 30+, with a fallback).
- **Settings:** `SettingsScreen.kt` (Phase 102). Add the switch to Appearance, or to a new "Feedback" row.
- **Preferences:** `UserPreferencesRepository` (DataStore "settings") holds `auto_connect` and `workflow_sort`. Add a `vibration` key.

## Decisions

- **D-01: Rail.** Pick it by orientation: landscape uses `NavigationRail`, portrait uses `NavigationBar`.
  - Both are built from the same `TABS` list and use the same `navigateToTab`.
  - Labels show on the rail too (`alwaysShowLabel`).
  - The FAB stays in each screen's Scaffold.
- **D-02: Sweep scope.**
  - The leftovers listed above, plus anything the device check finds.
  - No new components unless two or more screens need the same thing.
  - The viewer and the selection bar stay as they are.
- **D-03: Touch-target scan.**
  - A small script in the scratchpad parses `uiautomator dump` bounds for clickable nodes under 48 dp (at the device density, 480 dpi, that's 144 px).
  - Each finding gets fixed or explicitly accepted, with the reason recorded in `105-VERIFICATION.md`. Example of a likely acceptance: text links inside a `TextButton` row.
- **D-04: Haptics.**
  - A `Haptics` helper in `ui/components` that checks the `vibration` preference.
  - Click on Generate and Queue.
  - Confirm when the selected workflow's run finishes with a result while the form is showing (no background vibration).
- **D-05: Carried-over checks in the device pass:**
  - the templates reload-error banner (aeroplane mode, asking the user first);
  - importing a template (asking first; it adds a workflow the user can delete).

## Out of scope

- Pinned form fields and motion transitions (Phase 106).
- Tablet layouts (milestone scope).
- A live test of Queue Remove/Stop (dropped).
- New features on any screen.
