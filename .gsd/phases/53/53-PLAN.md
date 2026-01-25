---
phase: 53
plan: 1
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
  - app/src/main/java/com/example/comfyui_remote/ui/WorkflowListScreen.kt
autonomous: true
user_setup: []

must_haves:
  truths:
    - "Server workflows can be opened directly without importing to database"
    - "Temporary WorkflowEntity (id=0) is created for server workflows"
    - "DynamicFormScreen populates correctly from server workflow"
  artifacts:
    - "loadServerWorkflow function exists in MainViewModel"
    - "WorkflowListScreen calls loadServerWorkflow instead of importServerWorkflow"
  key_links:
    - "loadServerWorkflow creates temporary entity matching history workflow pattern"
---

# Plan 53.1: Enable Direct Server Workflow Execution

<objective>
Enable server workflows (from userdata/workflows/) to be opened directly in DynamicFormScreen without requiring database import, matching the behavior of local template workflows.

Purpose: Reduce friction for using server-hosted workflows and enable instant execution without creating local copies.
Output: `loadServerWorkflow` function in MainViewModel, updated WorkflowListScreen click handler.
</objective>

<context>
Load for context:
- .gsd/SPEC.md
- app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
- app/src/main/java/com/example/comfyui_remote/ui/WorkflowListScreen.kt
- app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt (lines 306-323 for reference)
- app/src/main/java/com/example/comfyui_remote/domain/NormalizedWorkflow.kt
</context>

<tasks>

<task type="auto">
  <name>Add loadServerWorkflow function to MainViewModel</name>
  <files>app/src/main/java/com/example/comfyui_remote/MainViewModel.kt</files>
  <action>
Create a new suspend function `loadServerWorkflow` in MainViewModel that:
1. Accepts `ServerWorkflowFile` and `onSuccess: (WorkflowEntity) -> Unit` callback
2. Downloads JSON using `getFileContent("userdata/$fullPath")`
3. Normalizes the workflow using `normalizationService.normalize()` with source `WorkflowSource.SERVER_USERDATA`
4. Creates a TEMPORARY `WorkflowEntity` with `id = 0` (not persisted to database)
5. Sets `_selectedWorkflow.value` and calls the success callback
6. Manages `_isSyncing` state (true while loading, false on complete/error)

AVOID: Do not modify `importServerWorkflow` - keep both functions separate. `importServerWorkflow` persists to DB (creates local copy), while `loadServerWorkflow` creates temporary entity (no persistence). This preserves both use cases.

Place the function immediately after `importServerWorkflow` (around line 311) to keep related functions together.
  </action>
  <verify>.\gradlew assembleDebug completes without errors</verify>
  <done>loadServerWorkflow function exists, creates temporary WorkflowEntity (id=0, source=SERVER_USERDATA), and does not persist to database</done>
</task>

<task type="auto">
  <name>Update ServerWorkflowItem click handler</name>
  <files>app/src/main/java/com/example/comfyui_remote/ui/WorkflowListScreen.kt</files>
  <action>
In `ServerWorkflowItem` composable (line 229), update the `onClick` parameter passed to the Card:

BEFORE (line 117-121):

```kotlin
onClick = {
    viewModel.importServerWorkflow(serverFile) { newWf ->
        onWorkflowValidation(newWf)
    }
}
```

AFTER:

```kotlin
onClick = {
    viewModel.loadServerWorkflow(serverFile) { tempWf ->
        onWorkflowValidation(tempWf)
    }
}
```

AVOID: Do not remove the `importServerWorkflow` function - it may be needed for explicit "save to local" functionality in the future. Only change the UI behavior.
  </action>
  <verify>.\gradlew assembleDebug completes without errors</verify>
  <done>ServerWorkflowItem calls loadServerWorkflow instead of importServerWorkflow</done>
</task>

<task type="checkpoint:human-verify">
  <name>Verify server workflow direct execution</name>
  <files>Manual testing on device/emulator</files>
  <action>
1. Connect app to ComfyUI server with workflows in `userdata/workflows/` directory
2. Navigate to Workflow List screen
3. Observe that server workflows appear under "Server Workflows (Userdata)" section
4. Click on a server workflow
5. Verify it opens DynamicFormScreen immediately with populated form fields
6. Check that NO new entry appears in "Local Workflows" section (confirming no DB persistence)
7. Execute the workflow and verify results appear in Gallery
8. (Optional) Click "Save as Template" button and verify it NOW appears in "Local Workflows"
  </action>
  <verify>All manual verification steps pass</verify>
  <done>Server workflows open directly in DynamicFormScreen without creating database entries</done>
</task>

</tasks>

<verification>
After all tasks, verify:
- [ ] Build completes successfully with no errors
- [ ] Server workflows open directly without import
- [ ] Temporary WorkflowEntity (id=0) is created
- [ ] No database entries created on initial click
- [ ] "Save as Template" still works to create local copy
</verification>

<success_criteria>

- [ ] All tasks verified
- [ ] Must-haves confirmed
- [ ] Server workflow execution matches template workflow UX
</success_criteria>
