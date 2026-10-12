# Plan 105.2 Summary: Consistency Sweep

**Completed:** 2026-09-30 (commit `3cbe0d7`).

## Delivered

- **`IssueList`:**
  - each issue is a row with an Outlined `ErrorOutline` (error colour) or `WarningAmber` (onSurfaceVariant) icon at 16 dp, with the content description "Error" or "Warning";
  - no `✖`/`⚠` characters (audit A6);
  - it's used by the form's pre-flight dialog and the workflow list's problem dialog.
- **Typography:**
  - the model download card's filename uses titleSmall with no Bold override;
  - in the form's node-list dialog, the `#id` uses labelLarge in primary and the title uses bodyMedium.
- **Dialog titles:**
  - the image picker's title is now "Choose image";
  - the date dialog's title is "Date range", with "Start date"/"End date: not set" and "Clear date range".
- **Shapes:**
  - the image picker's preview uses `shapes.small`;
  - the gallery cell uses `shapes.extraSmall` (the same 4 dp);
  - there is no `RoundedCornerShape` left in `ui/`.
- **Spacing:** padding, spacers and `spacedBy` values that equal a token now use `Dimens` in `ImageSelector`, `DatePickerDialog`, `ErrorCard` and `EmptyState`. Sizes and the empty state's 32 dp outer padding are unchanged, since they have no token.

## Deviations

- **Files not edited:** `WorkflowListScreen` and `SavedListsDrawer` needed no changes; their remaining dp values are sizes or badge padding.
- **Extra file:** the gallery cell shape was an extra find of the final search.

## Verification

- `grep "RoundedCornerShape(|FontWeight.Bold|✖|⚠|📦|✓"` over `ui/`: no hits.
- `Color.Black`/`White` appear only in `MediaDetailScreen` (the deliberate black viewer, Phase 104).
- `testDebugUnitTest`: 251 tests, 0 failures. `assembleDebug` OK.
