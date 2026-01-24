---
phase: 30
plan: 1
wave: 1
---

# Plan 30.1: Graph to API Conversion

## Objective

Enable importing of "Graph Format" JSON files (the standard save format of ComfyUI Web) by converting them to "API Format" on the client side. This improves UX by removing the need for users to manually "Save as API Format".

## Context

- Phase 30 Research: Identified need for client-side conversion using `ObjectInfo`.
- `ImportWorkflowDialog`: UI entry point.
- `WorkflowParser`: Existing API-format parser.
- `ComfyApiService`: Source of `ObjectInfo`.

## Tasks

<task type="auto">
  <name>Create GraphToApiConverter</name>
  <files>d:/Antigravity/ComfyUI frontend/app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt</files>
  <action>
    Create a new domain service `GraphToApiConverter` that takes `ObjectInfo` and the raw Graph JSON string, and returns the API JSON string.
    - Parse Graph JSON (nodes, links, widgets_values).
    - Fetch Node Definition from `ObjectInfo` using `type` (which maps to `class_type`).
    - Map `widgets_values` (array) to named inputs using the order defined in `ObjectInfo` input/required/optional.
    - Map `inputs` (node connections) using the `links` array to find the source node and slot.
    - Handle missing nodes or unknown types gracefully (throw specific error).
  </action>
  <verify>Create a unit test passing a sample Graph JSON and Mock ObjectInfo, asserting the output matches expected API JSON.</verify>
  <done>Unit tests pass.</done>
</task>

<task type="auto">
  <name>Integrate Converter in MainViewModel</name>
  <files>d:/Antigravity/ComfyUI frontend/app/src/main/java/com/example/comfyui_remote/MainViewModel.kt</files>
  <action>
    Add a function `importGraphWorkflow(name: String, json: String)` to `MainViewModel`.
    - Retrieve current `_nodeMetadata` (ObjectInfo).
    - If missing, attempt to fetch it or error.
    - Call `GraphToApiConverter.convert`.
    - Then call existing `importWorkflow` with the converted JSON.
  </action>
  <verify>Build project.</verify>
  <done>Function exists and compiles.</done>
</task>

<task type="auto">
  <name>Update Import Dialog</name>
  <files>d:/Antigravity/ComfyUI frontend/app/src/main/java/com/example/comfyui_remote/ui/ImportWorkflowDialog.kt</files>
  <action>
    Update `ImportWorkflowDialog` to accept Graph Format.
    - Remove the error/block for `GraphFormatDetected`.
    - Instead, show a "Active Conversion" indicator or just allow it.
    - Pass a flag or detect format in ViewModel to decide whether to convert.
    - Note: To keep ViewModel clean, the Dialog can just pass the JSON, and ViewModel detects format? Or Dialog detects and calls specific VM method.
    - Decision: ViewModel `importWorkflow` should be smart enough or have a separate `importGraph` method. Let's update `onImport` signature or internal logic in UI to call the right thing.
    - Actually, let's keep `onImport` generic, but maybe add a `isGraph` boolean, or just let VM handle it.
    - Simplest: Update `validateJson` to allow Graph format. Update `onImport` to just pass the JSON. VM logic (Task 2) will handle detection/conversion or we assume the new converter handles both or we have a `tryConvert` step.
    - Refinement: Let's make `MainViewModel.importWorkflow` capable of trying to convert if it detects Graph format.
  </action>
  <verify>Manual verification: Import a standard workflow.json and see it succeed.</verify>
  <done>Dialog allows selection and import of graph json.</done>
</task>

## Success Criteria

- [ ] User can select a standard `workflow.json` (Graph format) in the Import Dialog.
- [ ] The workflow is successfully converted and imported into the database in API format.
- [ ] Running the imported workflow works (because it's now valid API format).
