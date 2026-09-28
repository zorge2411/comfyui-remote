# Plan 100.2 Summary: Shared Components and Theme Cleanup

**Completed:** 2026-09-28

## Delivered

**Components** (`603ed0a`), all in `ui/components/`:
- `Dimens`: spacing tokens xs 4 / s 8 / m 12 / l 16 / xl 24, plus `screenPadding`, `listGap`, `cardPadding`, `minTouch` 48.
- `AppTopBar(title, onBack?, actions)`: M3 `TopAppBar`, titleLarge, AutoMirrored back.
- `SectionHeader(title, action)`: titleSmall in primary, minimum height 48 dp. Moved out of `QueueScreen`, which now uses the shared one.
- `AppCard(variant = Default | Highlight, onClick?)`: surfaceContainer or primaryContainer, 16 dp inside.
- `StatusBanner(kind = Error | Warning | Info, title, message?, actions, content)`: container colour plus a Material icon (ErrorOutline, WarningAmber, Info), no emoji.
- `ConfirmDialog(title, text, confirmLabel, onConfirm, onDismiss, destructive)`: TextButtons; a destructive confirm uses the error colour.
- `@Preview`s, light and dark, for each new component.

**Fixes to existing components**
- `ErrorCard`: Retry is now a `TextButton`. Before, the row wasn't clickable, so `onRetry` never fired.
- `EmptyState`: scrolls, so its action stays reachable in landscape.
- `LoadingOverlay`: theme `scrim` and `inverseOnSurface` instead of Black and White.

**Theme** (this commit)
- Dropped `window.statusBarColor`.
- Status-bar and navigation-bar icons are now `isAppearanceLight…Bars = !darkTheme`. They were inverted: audit A1.

## Verification

`gradlew.bat testDebugUnitTest assembleDebug`: 200 tests, 0 failures. Checked on the device in 100.3.
