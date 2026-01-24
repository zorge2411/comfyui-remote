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
