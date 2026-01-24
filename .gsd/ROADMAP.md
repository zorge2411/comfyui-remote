# ROADMAP.md

> **Current Milestone**: None (Milestone 2 Completed)
> **Goal**: TBD (Run /new-milestone)

## Must-Haves

- [ ] TBD

## Nice-to-Haves

- [ ] TBD

## Phases

### Backlog

### Phase 16: Icon Refinement

**Status**: ⏸️ Deferred (Moved from Milestone 2)
**Objective**: Generate 5 premium icon concepts, allow user selection, and implement the chosen icon. Blocked by API capacity.

### Phase 29: Model Listing in Workflow List

**Status**: ✅ Done
**Objective**: Display the safeTensor/checkpoint name on the workflow list item for quick identification.

**Tasks**:

- [x] Create Plan
- [x] Implement Schema Changes (v5)
- [x] Implement Parsing Logic
- [x] Implement UI (Badges)
- [x] Verify

### Phase 30: Support for Importing Graph Format JSON

**Status**: ⬜ Not Started
**Objective**: Enable functionality to import workflows defined in the graph JSON format.
**Depends on**: Phase 29

**Tasks**:

- [ ] Create GraphToApiConverter
- [ ] Integrate Converter in MainViewModel
- [ ] Update Import Dialog
- [ ] Verify

**Verification**:

- Import `workflow.json` (Graph Format)
- Confirm it appears in list
- Run it and verify execution starts

### Phase 31: Sync Gallery & History

**Status**: ✅ Done
**Objective**: Synchronize the local app gallery with the ComfyUI server history on startup to ensure consistency across devices/sessions.
**Depends on**: Mileage 2 Completion

**Tasks**:

- [x] Create Phase Plan
- [x] Implement Sync Logic in MainViewModel
- [x] Optimize DAO with getAllPromptIds
- [x] Verify Sync on Startup

---

### Phase 32: Pull to Sync Gallery & History

**Status**: ✅ Done
**Objective**: Implement pull-to-refresh functionality on the Gallery and History pages to manually trigger synchronization with the ComfyUI server.
**Depends on**: Phase 31

**Tasks**:

- [x] Implement `isSyncing` state in `MainViewModel`
- [x] Update `syncHistory` with state management
- [x] Add `PullToRefreshBox` to `GalleryScreen`
- [x] Add `PullToRefreshBox` to `HistoryScreen`
- [x] Verify functionality

**Verification**:

- [ ] Pull down on Gallery screen triggers sync
- [ ] Pull down on History screen triggers sync
- [ ] UI reflects loading state during sync
