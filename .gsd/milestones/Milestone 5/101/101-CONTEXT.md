# Phase 101 Context: Workflow Form Declutter

**Gathered:** 2026-09-28. Milestone 5 (UI/UX Overhaul). Conventions: `.gsd/phases/100/100-UI-SPEC.md`.

## User decisions (2026-09-28)

1. **Layout:** the prompt and negative prompt, then a "Main settings" group (seed, size, steps, cfg, sampler, scheduler, denoise, model), then every other input grouped per node in **collapsed** sections titled by node name.
2. **Actions:** a **fixed bottom bar** (batch, Queue, Generate). Progress and the latest result appear in the status area **at the top**.
3. **Seeds:** a **Random / Fixed** toggle per seed field (Random is the default and today's behaviour). Show the seed that was actually used.
4. **Remember** edited values per workflow, with "Reset to workflow defaults".

## Code survey (read-only, 2026-09-28)

Paths are relative to `app/src/main/java/com/example/comfyui_remote/`.

- **`domain/InputField.kt`:**
  - sealed `InputField(label)`; `label` is a **type tag** ("Text", "Number", "Seed", "Number (Float)", "Model", "Selection", "Image") used by `QueueViewModel.InputFieldDeserializer` (`:208-223`) to rebuild queued items, so it must not change;
  - each field has `nodeId`, `nodeTitle` (from `_meta.title`) and `fieldName`;
  - no class_type, no min, max or default;
  - `displayName` replaces `.` with ` › `.
- **`domain/WorkflowParser.kt`:**
  - `parse()` goes node by node in JSON order and skips links and dynamic-combo keys;
  - type heuristics: `LoadImage.image`, then a combo becomes Selection, a name containing "seed" becomes Seed, `*_name` becomes Model, numbers become Float or Int, strings become String;
  - `findPositivePromptNodeId` (`:149-170`) hoists the positive prompt node (Phase 82); the negative source is ignored;
  - tests are in `WorkflowParserTest`.
- **`ui/DynamicFormScreen.kt`** (1,010 lines):
  - the header Row scrolls with the content (`:148`);
  - status cards (`:165-243`);
  - an inline fields loop (`:247-467`);
  - progress (`:471`), batch (`:506`), buttons (`:552`), "View Queue Manager" (`:612`), "Save as Template" (`:620`), result (`:641`);
  - the node sheet (`:689`), `PreflightDialog` (`:726`) and `MissingModelsCard` (`:786`).
- **State:**
  - `var inputs by remember` (`:88`) is set by `LaunchedEffect(workflow)`, which isn't keyed on `nodeMetadata`, so late metadata is ignored;
  - edits copy the list by index;
  - Int, Float and Seed parse on every keystroke and fall back to 0.
- **Uploads:** `pendingImageUploads` is keyed by **list index** (`:98`).
- **Sending:** `MainViewModel.executeWorkflow` → `WorkflowExecutionService.buildPrompt` → `WorkflowExecutor.injectValues`, which needs only `nodeId`, `fieldName` and the value. Order and grouping don't matter.
- **Seeds:** `executeWorkflow` (around `MainViewModel.kt:657-665`) and `QueueViewModel.kt:144-151` randomize **every** SeedInput on every run, so a typed seed is ignored.
- **Persistence:** none. `WorkflowEntity` has no saved-values column, and Room is at version 12.

## Decisions

- **D-01:** Classification (`FormLayout`) is a pure, JVM-tested domain function. The UI only renders its result.
- **D-02:** A field is promoted to Main settings only if its role is unique in the workflow. For example, two KSamplers keep `steps` in the node sections; the app doesn't guess.
- **D-03:** Queue compatibility.
  - `InputField.label` tags are unchanged.
  - The seed mode is `SeedInput.fixed: Boolean? = null`, and null or false means random. Gson leaves a missing field null, so old queued JSON keeps random behaviour.
- **D-04:** Remembered values go in the Room column `workflows.savedInputs` (12 → 13), JSON in the queue's format.
  - They are restored by nodeId, fieldName and subtype.
  - Selection values are kept only if still in the options.
  - Stale values from a re-import are dropped.
- **D-05:** Field state and pending uploads are keyed by `nodeId/fieldName`, not by list index.
