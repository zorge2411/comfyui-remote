## Phase 35 Decisions

**Date:** 2026-01-24

### Scope

- **Multi-select Delete**: Implement long-press selection in Grid View for batch deletion.
- **Permissions**: Improve handling of revoked permissions (prompt user effectively).
- **UX**: Add Shared Element Transitions between Grid and Detail views.

### Approach

- **Selection**: Long-press triggers selection mode; TopAppBar changes to Context Bar.
- **Storage**: Add specific "Save Successful" snackbar. Add "Reset Folder" option in Settings.
- **Animations**: Use Compose Shared Element Transitions.

### Dependencies

- **Settings**: Add "Reset" action next to "Change Folder".

## Phase 105 Decisions

**Date:** 2026-09-30 (details: `phases/105/105-CONTEXT.md`, `phases/106/106-CONTEXT.md`)

### Scope

- 105: consistency sweep, navigation rail in landscape, haptics, full device check (every screen, light and dark, portrait and landscape, touch-target scan).
- Pinned form fields and card/result motion transitions split out into a new Phase 106.
- Queue Remove/Stop live test dropped.

### Approach

- Chose: NavigationRail in landscape, bottom bar in portrait, from one tab list.
- Reason: the labelled bottom bar took about a third of the landscape height.
- Haptics: click on Generate/Queue, confirm on a finished run while the form shows; a "Vibration" switch in Settings (default on).
