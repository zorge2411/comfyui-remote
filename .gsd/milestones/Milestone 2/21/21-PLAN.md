---
phase: 21
plan: 1
wave: 1
---

# Plan 21.1: Workflow History Refinements

## Objective

Fix the "Latest Image" display bug and stop the "Sync History" feature from polluting the user's saved workflows list.

## Context

- .gsd/SPEC.md
- .gsd/phases/21/RESEARCH.md
- app/src/main/java/com/example/comfyui_remote/MainViewModel.kt

## Tasks

<task type="auto">
  <name>Fix Latest Image Display</name>
  <files>app/src/main/java/com/example/comfyui_remote/MainViewModel.kt</files>
  <action>
    Modify `selectWorkflow` in `MainViewModel.kt` to update `_generatedImage` based on the selected workflow's `lastImageName`.

    1. Check if `workflow.lastImageName` is not null.
    2. If valid, construct the full URL (`http://{serverAddress}/view?filename={lastImageName}&type=output`) and update `_generatedImage.value`.
    3. If null, clear `_generatedImage.value` to avoid showing stale images from other workflows.
  </action>
  <verify>Manual verification: Select a workflow with a known previous generation and ensure the image appears.</verify>
  <done>Generated image view updates immediately upon workflow selection.</done>
</task>

<task type="auto">
  <name>Disable History-to-Workflow Pollution</name>
  <files>app/src/main/java/com/example/comfyui_remote/MainViewModel.kt</files>
  <action>
    Modify `syncHistory` in `MainViewModel.kt` to STOP creating new `WorkflowEntity` items for every history entry.

    1. Comment out or remove the `repository.insert(workflow)` call inside the `syncHistory` loop.
    2. Ideally, leave the parsing logic if we plan to use it later for a "Load History" feature, but for now, the priority is to stop the database pollution.
    3. Add a TODO comment explaining that history loading should be ephemeral or stored in a separate table.
  </action>
  <verify>Manual verification: Run 'Sync History' (or trigger it) and verify no new "History 202X..." workflows appear in the list.</verify>
  <done>Workflows list remains clean after history sync.</done>
</task>

## Success Criteria

- [ ] Selecting a workflow shows its last generated image (if any).
- [ ] Syncing history does not flood the "Saved Workflows" list.
