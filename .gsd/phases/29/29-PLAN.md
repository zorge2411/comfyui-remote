---
phase: 29
plan: 1
wave: 1
---

# Plan 29.1: Model Listing in Workflow List

## Objective

Display the Checkpoint/Model name (SafeTensors) used by each workflow directly in the main `WorkflowListScreen` to allow users to quickly identify which model a workflow is built for.

## User Review Required

- **Database Migration**: This change increments the DB version from 3 to 4.
- **UI Change**: Adds a small badge to the workflow list items.

## Proposed Changes

### Data Layer (`com.example.comfyui_remote.data`)

#### [MODIFY] [WorkflowEntity.kt](file:///d:/Antigravity/ComfyUI%20frontend/app/src/main/java/com/example/comfyui_remote/data/WorkflowEntity.kt)

- Add `val baseModelName: String? = null` column.

#### [MODIFY] [AppDatabase.kt](file:///d:/Antigravity/ComfyUI%20frontend/app/src/main/java/com/example/comfyui_remote/data/AppDatabase.kt)

- Increment version to `4`.
- Add `MIGRATION_3_4`: `ALTER TABLE workflows ADD COLUMN baseModelName TEXT`.

### ViewModel Layer (`com.example.comfyui_remote`)

#### [MODIFY] [MainViewModel.kt](file:///d:/Antigravity/ComfyUI%20frontend/app/src/main/java/com/example/comfyui_remote/MainViewModel.kt)

- Update `importWorkflow` to parse the JSON and extract inputs.
- Use `workflowParser.parse(json)` to get `InputField.ModelInput` and extract the value.
- Add logic in `init` or a migration helper to "Backfill" existing workflows (parse their JSON and update the DB) - **Optional but recommended for user experience**.

### UI Layer (`com.example.comfyui_remote.ui`)

#### [MODIFY] [WorkflowListScreen.kt](file:///d:/Antigravity/ComfyUI%20frontend/app/src/main/java/com/example/comfyui_remote/ui/WorkflowListScreen.kt)

- Update `WorkflowItem` to accept and display `workflow.baseModelName`.
- Design: Small chip/badge below the name or next to the date. e.g. `📦 v1-5-pruned.ckpt`.

## Verification Plan

### Automated Tests

- Build verification (`./gradlew assembleDebug`).

### Manual Verification

1. **Migration**: Install over existing app. Verify app doesn't crash.
2. **Import**: Import a new workflow (Standard or API format). Verify the model name appears in the list.
3. **UI**: Verify the model name is truncated or handled gracefully if long.
