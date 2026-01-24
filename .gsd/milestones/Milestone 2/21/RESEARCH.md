# Research Phase 21: Workflow vs History System

## 1. Problem Analysis

### A. The "Latest Image" Bug

**Issue**: When selecting a workflow from the list, the "result" image shown on the generation screen persists from the *previous* session or global state, rather than showing the last generated image for the *selected* workflow.
**Root Cause**: `MainViewModel.selectWorkflow` updates `_selectedWorkflow` but does **not** update `_generatedImage`.
**Fix**: `selectWorkflow` should check `workflow.lastImageName` and construct the URL to update `_generatedImage`.

### B. Current "Load History" Implementation

**Issue**: The `syncHistory()` function fetches history from ComfyUI and **converts every history item into a new saved `WorkflowEntity`**.
**Consequences**:

- **Pollution**: The "Saved Workflows" list gets flooded with "History 202X-XX-XX" entries.
- **Duplication**: The same workflow run multiple times creates multiple "Workflow" entries.
- **Confusion**: "History" and "Saved Templates" are mixed.

## 2. Proposed Architecture

### Goal

Separate "Saved Workflows" (Templates) from "Execution History" (Instances), while allowing history items to be "loaded" for re-execution.

### Changes

#### 1. Data Model

- **WorkflowEntity**: strictly for *saved* user-defined workflows (templates).
- **ExecutionHistoryEntity** (New or reuse generated_media): Stores the run details.
  - ComfyUI's history is ephemeral (memory-based usually).
  - We already have `GeneratedMediaEntity`. We should ensure it stores enough info to "reload" the state (e.g. the prompt JSON or a link to it).

#### 2. Workflow Loading Logic

- **Saved Workflow**: Load from DB -> Parse Forms -> Ready to Run.
- **History Item**: Load from Server/DB -> Extract Prompt JSON -> Parse Forms -> Ready to Run (as "Unsaved/Temporary").

#### 3. Refactoring Plan

1. **Stop `syncHistory` pollution**: Remove the logic that inserts `WorkflowEntity` for every history item.
2. **Implementation**:
    - Use `GeneratedMediaEntity` as the local "History" source of truth (since we save it on execution).
    - Or, query ComfyUI history dynamically for a "Recent" list.
    - When a user taps a History item (Image):
        - Extract the `prompt` (workflow json) from the image metadata or server history.
        - Use `workflowParser.parse(json)` to generate the form.
        - Navigate to `DynamicFormScreen` with this "temporary" workflow.

## 3. Immediate "Fix" (The requested scope)

The user asked to "analyse how to use workflows instead of the current load history implementation".

**Recommendation**:

1. **Disable** the "Auto-create workflow from history" feature in `syncHistory`.
2. **Fix** the "Latest Image" bug in `MainViewModel`.
3. **Future**: Build a dedicated History UI that loads the JSON into the form *without* saving a WorkflowEntity first.

## 4. Work Plan (Phase 21)

1. **Fix Bug**: Update `MainViewModel.selectWorkflow` to set `_generatedImage`.
2. **Refactor**: Modify `syncHistory` (or remove it) to stop polluting the database.
3. **Prototype**: Add a "Load Context" function that takes a history JSON and sets up the UI state, instead of saving to DB.
