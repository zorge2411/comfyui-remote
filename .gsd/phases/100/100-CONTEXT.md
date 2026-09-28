# Phase 100 Context: UI Audit and Shared Components

**Gathered:** 2026-09-28. Milestone 5 (UI/UX Overhaul).

## Milestone scope (from the user)

- Areas: the workflow form, navigation, gallery and viewer, and the workflow list and templates.
- Polish Material 3 and keep dynamic colour; no new brand.
- Pain points: **the form is cluttered** and **screens are inconsistent**.
- Phone portrait. Landscape must not break.

## Code survey (read-only, 2026-09-28)

Paths are relative to `app/src/main/java/com/example/comfyui_remote/`.

**Top bars**
- `TopAppBar` is used in WorkflowList, Templates, Queue, History, Gallery and MediaDetail.
- DynamicForm uses a custom Row (`ui/DynamicFormScreen.kt:147`). Connection (`MainActivity.kt:409`) and Settings (`ui/SettingsScreen.kt:42`) have only a headlineMedium Text.
- Settings is pushed from History but has no back button. Queue is a tab but has one (`QueueScreen.kt:64`).
- `Icons.Default.ArrowBack` isn't AutoMirrored in DynamicForm, MediaDetail, Queue and Templates.

**Shared components**
- `LoadingOverlay` is unused; WorkflowList (:157) and Templates (:200) rebuild it with `Color.Black`/`Color.White`.
- `SmallLoadingIndicator` is unused.
- `ErrorCard`'s Retry row isn't clickable (`ui/components/ErrorCard.kt:115-131`).
- `EmptyState` doesn't scroll.
- SavedListsDrawer has its own copy of EmptyState (:88-114).

**Cards**
- Outer padding 0, 4 or 8; inner 8, 10, 12 or 16; corners 4, 8, 12 or the default.
- Only History sets elevation (2 dp).
- Container colours: default, surfaceVariant, or surfaceVariant at 0.5 alpha.

**Dialogs**
- Destructive confirms come in three styles: TextButton with the error content colour, `Text(color = error)`, and a filled `Button(containerColor = error)`.
- Confirm buttons mix TextButton and filled Button.
- Gallery multi-delete (`GalleryScreen.kt:144`) has no confirm.
- ImportWorkflowDialog uses a filled `TextField`, a non-scrolling text slot, and `Color(0xFF4CAF50)`/`Color(0xFF2196F3)`.

**Colour and emoji**
- The connection dot uses `Color.Green/Yellow/Red/Gray` (`MainActivity.kt:547-550`).
- Emoji status prefixes: "⚠️ Missing Nodes", "⚠️ Missing Models", "⚠️ Some outputs were skipped", "✓", "📦", "✖/⚠".

**Scrolling and landscape**
- The Connection screen's `Column(fillMaxSize, Arrangement.Center)` has no `verticalScroll` (`MainActivity.kt:398-404`). About 400 dp of content doesn't fit in landscape (roughly 250 dp after the status bar and the 80 dp NavigationBar), so the Connect button is cut off.
- Settings and `EmptyState` don't scroll either.

**Insets**
- targetSdk 35 means Android 15 forces edge-to-edge, but there's no `enableEdgeToEdge()`.
- The outer Scaffold's `innerPadding` goes to the NavHost (`MainActivity.kt:244`) without `consumeWindowInsets`. Inner Scaffolds and TopAppBars add the status bar again, which likely pads the top twice.
- MediaDetail's sheet uses a hard-coded 32 dp bottom padding; DynamicForm guesses too (around `:696`).

**Theme**
- Stock template: purple 80/40, one Typography override, no Shapes, no spacing tokens.
- `window.statusBarColor` (`ui/theme/Theme.kt:54`) is deprecated.
- `isAppearanceLightStatusBars = darkTheme` (`:55`) looks inverted.

**Deprecations**
- No-arg `menuAnchor()` at `MainActivity.kt:432` and DynamicForm `:349`, `:385`.
- `Icons.Default.PlaylistAdd` in SavedListsDrawer.

## Decisions

- **D-01:** Phase 100 builds the components and applies them only to the **Connection and Settings** screens (as references), plus the app-wide fixes (insets, theme, landscape, AutoMirrored back icons). Other screens migrate in 101 (form), 102 (navigation), 103 (list and templates), 104 (gallery) and the 105 sweep.
- **D-02:** The user approves the UI spec (`100-UI-SPEC.md`) at a checkpoint before components are built.
- **D-03:** No Compose UI tests; the repo has none. Components get `@Preview`s. Verification is `assembleDebug` plus on-device screenshots over adb: portrait light and dark, and landscape. Screenshots stay in the session scratchpad because they may show personal images and prompts; only findings are committed.
