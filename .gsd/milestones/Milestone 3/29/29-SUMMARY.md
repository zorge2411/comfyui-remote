# Phase 29: Model Listing in Workflow List

## Objective

Display the Checkpoint/Model name (SafeTensors) used by each workflow directly in the main `WorkflowListScreen` to allow users to quickly identify which model a workflow is built for.

## Delivered

- [x] **Schema**: Added `baseModelName` to `WorkflowEntity` and incremented DB version to 5.
- [x] **Logic**: Implemented JSON parsing in `importWorkflow` and a backfill logic on startup to populate existing workflows.
- [x] **UI**: Added a `📦 {model_name}` badge to the workflow list items.

## Verification

- Build verified (Exit code 0).
- Code changes applied successfully.

## Files Modified

- `AppDatabase.kt`
- `WorkflowEntity.kt`
- `MainViewModel.kt`
- `WorkflowListScreen.kt`
