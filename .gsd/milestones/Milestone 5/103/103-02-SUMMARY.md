# Plan 103.2 Summary: Workflow List UI

**Completed:** 2026-09-29 (commit `85e942d`).

## Delivered

- **`ui/components/IssueList`:** the counts and the issues grouped by node. The form's `PreflightDialog` uses it and looks the same.
- **Workflows screen:**
  - **Top bar:** `AppTopBar("Workflows")` with the Phase 102 chip, Browse templates, and Sort (a menu with a check on Last used, Name or Newest).
  - **Above the list:**
    - Phase 102's pull to refresh and `NotConnectedBanner` are kept;
    - an outlined search field (shown once there is anything to search).
  - **`LazyColumn`:**
    - `Dimens` padding;
    - bottom padding of FAB + 32 dp, so the FAB no longer covers the last card.
  - **Server section:** "On the server (N)" as a `SectionHeader` with Show/Hide, collapsed by default and filtered by the search.
  - **States:**
    - `EmptyState` "No workflows yet" (Browse templates only when connected);
    - "No matches" with "Clear search".
  - **`LoadingOverlay`** replaces the black/white scrim.
- **`WorkflowCard`** (an `AppCard` row):
  - **Thumbnail:** 72 dp, `shapes.medium`: Coil for images; an Image or Movie icon for no result or a video.
  - **Text:** the name, and up to 2 model labels, then "+N".
  - **Badge:**
    - Ready: secondaryContainer;
    - "N warnings": tertiaryContainer;
    - "Won't run": errorContainer;
    - each with an Outlined icon;
    - none offline.
    - A badge other than Ready is a 48 dp touch target that opens "Problems in <name>" (`IssueList`, Close).
  - **More menu:** Rename (outlined field dialog) and Delete (`ConfirmDialog`, destructive, "Delete workflow?").
- **`ServerWorkflowItem`:** an `AppCard` with the name, the path, and a CloudDownload "Import <name>" button, disabled offline.
- **Import dialog:**
  - title "Import workflow";
  - outlined fields;
  - hint text that says both formats work, and asks for a name when that is what keeps Import disabled;
  - no Color literals or ✓ marks.

## Deviations

- **"Created" line:** dropped from the cards; the Newest sort covers it.
- **`WorkflowIssuesDialog`:** kept inline in the screen rather than as a separate component.

## Verification

- `assembleDebug` OK; 250 tests pass.
- `grep "Color.Black|Color.White|RoundedCornerShape|📦" WorkflowListScreen.kt`: no hits.
