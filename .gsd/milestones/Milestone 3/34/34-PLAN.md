# Phase 34: Media Detail Individual Delete

**Objective**: Add individual deletion in detail view.

## Technical Design

- Add Delete icon to `MediaDetailScreen.kt` top bar.
- Implement confirmation dialog.
- Call `viewModel.deleteMedia()` on confirm.
- Navigate back to Gallery after deletion.

## Tasks

- [ ] Add Delete icon and state to `MediaDetailScreen`
- [ ] Implement confirmation dialog
- [ ] Hook up ViewModel delete logic
- [ ] Verify navigation after delete
