---
phase: 22
plan: 1
wave: 1
---

# Plan 22.1: Workflow History Implementation

## Objective

Implement a dedicated History system that separates user-saved workflow templates from actual execution history, allowing users to browse past runs and "reload" their state (inputs/graph) without polluting the saved workflows list.

## Context

- **Problem**: `syncHistory` previously polluted the Saved Workflows list. Now it's disabled, but users can't easily access history or re-run past prompts.
- **Solution**:
    1. Store the full Prompt JSON with every `GeneratedMediaEntity`.
    2. Create a "History" UI to list these executions.
    3. Allow loading a history item into the `DynamicFormScreen` as a temporary/unsaved workflow.

## Proposed Changes

### 1. Database Schema

- **File**: `app/src/main/java/com/example/comfyui_remote/data/GeneratedMediaEntity.kt`
- **Change**: Add `val promptJson: String? = null` column.
- **Migration**: Increment Database Version and provide `MIGRATION_2_3`.

### 2. Logic & Data Capture

- **File**: `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`
- **Change**:
  - Add `private val _executionCache = mutableMapOf<String, String>()` // prompt_id -> json
  - In `executeWorkflow`: After `queuePrompt` returns a `prompt_id`, store the input JSON in `_executionCache`.
  - In `handleMessage` -> `executed`: Look up the JSON using `prompt_id` from the message. Include this JSON when creating `GeneratedMediaEntity`.

### 3. UI Implementation

- **File**: `app/src/main/java/com/example/comfyui_remote/ui/HistoryScreen.kt` (NEW)
  - List `GeneratedMediaEntity` items sorted by timestamp (descending).
  - Show Thumbnail, Workflow Name (if known), Date.
  - Click Action: `viewModel.loadHistory(item)`.

- **File**: `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`
  - Function `loadHistory(media: GeneratedMediaEntity)`:
    - Extract `promptJson`.
    - Create a temporary `WorkflowEntity` (id=0, name="History: ${timestamp}").
    - Set `_selectedWorkflow`.
    - Trigger navigation to `DynamicFormScreen`.

- **File**: `app/src/main/java/com/example/comfyui_remote/ui/AppNavigation.kt`
  - Add `History` route or integrate into `Gallery` as a tab.

## Verification Plan

### Automated

- **Unit Test**: Test `MainViewModel` logic:
  - Queue prompt -> Check cache has ID.
  - Simulate `executed` message -> Verify `insert` is called with JSON.
  - `loadHistory` -> Verify `selectedWorkflow` is set correctly with parsed inputs.

### Manual

1. **Run Workflow**: Execute a workflow.
2. **Check DB**: Inspect `generated_media` table (via App Inspection) to see `prompt_json` populated.
3. **View History**: Go to History screen, see the new item.
4. **Reload**: Tap the item. Ensure `DynamicFormScreen` opens with the *exact* settings used for that generation.
5. **Multi-Run**: Run multiple times. Ensure history builds up and each item loads its own specific settings.
