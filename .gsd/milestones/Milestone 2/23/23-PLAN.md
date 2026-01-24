# Plan: Fix Import & Title Bugs

## Objective

Address the user report that "nothing is imported" and fix the UI bug where workflow filenames overwrite workflow names in the list.

## Proposed Changes

### 1. UI Fix: Workflow Titles

- **File**: `app/src/main/java/com/example/comfyui_remote/ui/WorkflowListScreen.kt`
- **Change**: Change `workflow.lastImageName ?: workflow.name` to `workflow.name`. The title should always be the workflow's name.

### 2. Logic Fix: History Sync

- **File**: `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`
- **Change**:
  - Re-enable the logic in `syncHistory()`.
  - Instead of creating `WorkflowEntity`, create `GeneratedMediaEntity`.
  - This will populate the **History** tab (Phase 22) from the server history.
  - To prevent duplicates, we need to track which server-side `prompt_id`s we've already synced.

### 3. Data Model Update (Optional but recommended)

- **File**: `app/src/main/java/com/example/comfyui_remote/data/GeneratedMediaEntity.kt`
- **Change**: Add `val promptId: String? = null`.
- **Migration**: Increment DB version and add column. This ensures we don't duplicate history items during sync.

### 4. Import Dialog Enhancement

- **File**: `app/src/main/java/com/example/comfyui_remote/ui/ImportWorkflowDialog.kt`
- **Change**: Add a hint that "API format" (the generated JSON from ComfyUI) is required, as the UI format (with `nodes`/`links` arrays) isn't currently supported by our parser.

## Verification Plan

1. **Manual**: Import a JSON manually, verify it appears in the list with its name.
2. **Manual**: Click "Sync from Server" and verify items appear in the **History** tab.
3. **UI**: Generate an image and verify the workflow name stays the same in the list.
