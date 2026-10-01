# Plan 106.1 Summary: Pinned Fields: Layout and Storage

**Completed:** 2026-10-01 (commits `34f8b40` layout, `c5058b6` storage).

## Delivered

- **`FormLayout.build(..., pinned: Set<String> = emptySet())`** returns `FormModel.pinned`:
  - the pinned fields come from what's left for the node groups after the prompt and main roles are taken, in input order;
  - groups left empty are dropped;
  - pins on the prompt, negative or main fields, and unknown keys, are ignored;
  - labels are humanized, with "<node title> · <label>" when two pinned labels collide.
- **Storage:**
  - `WorkflowEntity.pinnedFields` (a JSON key list); `MIGRATION_15_16` adds the column; version 16;
  - DAO `setPinned` and `setLastImage` (targeted UPDATEs), with repository pass-throughs.
- **ViewModel:**
  - `pinnedFields(workflow)` returns an empty set on null or garbage;
  - `togglePin(workflow, key)` updates `_selectedWorkflow` at once and writes on IO; history previews (`id == 0`) aren't stored.
- **Race fix:** the post-run `lastImageName` write is now `setLastImage`, plus an in-memory update of the selection, instead of a whole-row REPLACE from the selection-time copy, which undid pins and form values saved since.

## Verification

- `testDebugUnitTest`: 255 tests, 0 failures.
- New `FormLayoutTest` cases:
  - pinned field moves;
  - pin keeps the rest of the group;
  - main, prompt and unknown keys are ignored;
  - colliding labels get the node title.
- The migration is checked on the device in 106.3.
