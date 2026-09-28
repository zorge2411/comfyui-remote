# UI Spec: Milestone 5 Conventions

**Status:** draft for user approval (Plan 100.1, Task 3).
**Scope:** every screen of ComfyUI Remote. Material 3 with dynamic colour; phone portrait first, landscape must stay usable.
**Implemented in:** `app/src/main/java/com/example/comfyui_remote/ui/components/` (Plan 100.2). Each rule lists its reason and the audit item it fixes (see `100-AUDIT.md`).

## 1. Spacing

| Token | Value | Use |
|---|---|---|
| `Dimens.xs` | 4 dp | icon and text gaps |
| `Dimens.s` | 8 dp | list item gap, chip gaps |
| `Dimens.m` | 12 dp | related controls in a group |
| `Dimens.l` | 16 dp | screen padding, card inner padding |
| `Dimens.xl` | 24 dp | between groups and sections |
| `Dimens.minTouch` | 48 dp | touch targets |

*Why:* card padding varies (0, 4, 8, 10, 12, 16) and spacers vary (8, 16, 24, 32). One scale makes screens line up.

## 2. Top bars: `AppTopBar`

- Every screen has an M3 `TopAppBar` with a titleLarge title. Screens don't draw their own headline title (Connection, Settings and the form do today).
- **Tabs** have no back button. **Pushed screens** (form, Templates, Settings, media viewer) get `Icons.AutoMirrored.Filled.ArrowBack`, content description "Back".
- At most 3 action icons, each with a content description; more go in an overflow menu.
- The bar stays fixed while the content scrolls (the form's title row currently scrolls away).

*Fixes:* A2 (inconsistent tops), form title scrolling away, Settings without back, Queue with back.

## 3. Section headers: `SectionHeader`

- titleSmall in the primary colour, minimum height 48 dp, optional trailing TextButton (e.g. "Clear finished").

*Fixes:* headers currently mix labelLarge in primary, titleSmall in black, and titleMedium.

## 4. Cards: `AppCard`

- M3 `Card`, default shape, inner padding 16, no custom elevation.
- **Default:** `surfaceContainer`. **Highlight:** `primaryContainer`, e.g. the running item.
- A whole-card tap is the main action. Secondary actions go in an overflow menu, or at most one trailing icon, not two or three icons on every card.

*Fixes:* History alone uses elevation; card styles and paddings vary; Rename and Delete icons on every workflow card.

## 5. Status: `StatusBanner`

| Kind | Container | Icon |
|---|---|---|
| Error | `errorContainer` | `Icons.Outlined.ErrorOutline` |
| Warning | `tertiaryContainer` | `Icons.Outlined.WarningAmber` |
| Info | `secondaryContainer` | `Icons.Outlined.Info` |

- Each banner has a title, an optional message and optional actions.
- **No emoji in UI text** ("⚠️", "📦", "✓", "✖").

*Fixes:* A6, and the mix of ad-hoc errorContainer and tertiaryContainer cards.

## 6. Dialogs: `ConfirmDialog`

- TextButtons, with dismiss on the left and confirm on the right. Titles are short verbs ("Delete workflow?").
- A destructive confirm uses the error content colour; there are no filled red buttons.
- **Every delete is confirmed**, including gallery multi-delete.
- Text fields in dialogs are outlined, like everywhere else.

*Fixes:* three destructive styles, filled and text confirm buttons mixed, the unconfirmed multi-delete, filled `TextField`s in Import.

## 7. States

| State | Component | Rule |
|---|---|---|
| Empty | `EmptyState` | Scrolls, so it works in landscape; icon, title, message and one action |
| Loading (inline) | `LoadingIndicator` | Centred |
| Loading (blocking) | `LoadingOverlay` | Theme `scrim` colour, no hard-coded black or white |
| Error (inline) | `StatusBanner(Error)`, or `ErrorCard` with a working Retry | |

## 8. Buttons

- **One filled button per screen, for the main action** (Connect, Generate). Secondary actions are outlined or text buttons.

*Fixes:* Connection's two equal filled buttons.

## 9. Colour

- Theme roles only. There are no `Color.Green`, `Red`, `White` or `Black` literals outside media viewers.

| Connection status | Colour | Icon |
|---|---|---|
| Connected | primary | CloudDone |
| Connecting / reconnecting | tertiary | Sync |
| Error | error | CloudOff |
| Disconnected | outline | CloudOff |

- Media viewers may use a black background, with light system-bar icons.

## 10. Layout, scrolling and insets

- **Edge-to-edge:** `enableEdgeToEdge()`, and the outer Scaffold padding is consumed once. There are no hard-coded bar heights (no 32 dp guesses).
- **Status-bar icons follow the theme:** dark icons on a light theme, light icons on a dark theme.
- **Every full-screen form scrolls,** with `imePadding` where fields could sit under the keyboard. Every control must be reachable in landscape.
- **FABs mustn't cover content:** add bottom content padding equal to the FAB height plus 16 dp.

*Fixes:* A1, A2, A3 and the FAB overlaps.

## 11. Typography and text

- M3 default type scale; no custom fonts.
- Field labels are human-readable where the app can derive them (Phase 101 decides the form's labels).
- Sentence case for titles and buttons ("Clear finished", not "Clear Completed").

## 12. Touch targets and accessibility

- Every interactive element is at least 48 dp. Icon-only buttons have content descriptions.
- Contrast comes from the M3 roles; don't place text at alpha below 0.7 on containers.
