# Phase 91: Pre-flight Compatibility Check - Context

**Gathered:** 2026-09-26
**Status:** Ready for execution

<domain>
## Phase Boundary

Before a prompt is queued, from the form or from the local queue, check the final prompt (after image uploads and form values are applied) against the connected server's live `/object_info`. Tell the user what the server would reject, and let them cancel or queue anyway. Replace the stale import-time "Missing Nodes on Server" banner with a live check. Showing the server's own `node_errors` after a rejection is Phase 92.

</domain>

<research>
## Findings

### App today

- `MainViewModel.executeWorkflow` and `QueueViewModel` (the local queue) both call `WorkflowExecutionService.prepareAndQueue`. That function patches uploads (`WorkflowPatchingService`), injects the form values (`WorkflowExecutor.injectValues`) and POSTs `/prompt`. No check happens first.
- `WorkflowEntity.missingNodes` is set **once, at import**, from `GraphToApiConverter`'s list against the `/object_info` of that moment. `DynamicFormScreen` shows it as "⚠️ Missing Nodes on Server".
- The list goes stale when the server changes, e.g. a custom node is installed later, or a different server is used.
- Before Phase 90 it also listed nodes the converter removed. Phase 90 stopped that for virtual, muted and bypassed nodes, but stored lists from earlier imports keep the old entries.
- `executeWorkflow` only logs a warning for missing nodes.
- Live `/object_info` is already fetched on every connect into `MainViewModel._nodeMetadata` (Phase 81).

### Server validation (ComfyUI v0.37.2 `execution.py`)

- `validate_prompt` only validates nodes reachable from **output nodes** (`output_node: true` in `/object_info`). A prompt with no output node is rejected (`prompt_no_outputs`). An unknown `class_type` gives `missing_node_type`.
- `validate_inputs` checks each input of each reachable node:

| Error type | When |
|---|---|
| `required_input_missing` | a required input is absent; for V3 nodes, dynamic sub-inputs of the selected option count too |
| `bad_linked_input` | a link list doesn't have exactly 2 elements |
| `return_type_mismatch` | a linked output's type doesn't fit the input's type |
| `invalid_input_type` | the value can't be converted to INT/FLOAT/STRING/BOOLEAN |
| `value_smaller_than_min` / `value_bigger_than_max` | the value is outside the spec's `min`/`max` |
| `value_not_in_list` | a combo value isn't among the options; this includes model/file lists, which on the live server hold the real file names |
| `custom_validation_failed` | the node's own validation rejected it |

- Range and combo checks are **skipped for inputs the node validates itself** (`VALIDATE_INPUTS`/`validate_inputs` arguments). `/object_info` doesn't expose which inputs those are, so the app can't know. Its range and combo findings are therefore **warnings**, not certainties.

### Existing validator

`ApiPromptValidator` (test source set, Phases 89 and 94) already does most of the structural work:
- dynamic-combo spec resolution;
- autogrow `min: 0`;
- match-type and `*`;
- comma-list types.

Its C6 and C7 checks need the original graph and only matter for the converter corpus.
</research>

<decisions>
## Implementation Decisions

- **D-01 One validator in main code:** move the C1–C5 logic into `domain/PromptValidator.kt` (main), producing `Issue(nodeId, classType, nodeTitle, kind, inputName, message, severity)`. The test `ApiPromptValidator` becomes a thin wrapper that maps kinds to C1–C5 and keeps its graph-only C6/C7, so the corpus tests don't change.
- **D-02 Mirror the server:**
  - Validate only nodes reachable from `output_node: true` nodes. If there are none, report "no output node".
  - Add the server's INT/FLOAT/BOOLEAN conversion check and the `min`/`max` range check.
  - Check file-valued combos too: on the live server a missing model file is exactly what users need to hear about. The corpus's `FILE_VALUE` exemption stays in the test wrapper only, because the snapshot has no files.
  - Messages use the server's wording where one exists, e.g. "Value 200 bigger than max of 150", "Required input is missing".
- **D-03 Severity:**
  - **Error** (the server will certainly reject it): missing node type, required input missing, bad link, type mismatch, no output node.
  - **Warning** (the server may accept it through a node's own validation): value not in list, out of range, conversion.
  - Neither blocks. The dialog offers "Queue anyway" and "Cancel".
- **D-04 When:** run the check in the form on Generate and on Add to Queue, before `executeWorkflow` or adding to the queue, on the prompt built exactly as `prepareAndQueue` would build it. Uploaded image file names aren't known yet; LoadImage `image` values are exempt when an upload is pending for that node. If `_nodeMetadata` is null, fetch it first; if that fails, skip the check and log it. Don't re-check queue items at execution time.
- **D-05 Banner:** when a workflow is opened and live metadata is available, the missing-node banner shows the pre-flight's missing node types (issue kind "missing node type"). Otherwise it falls back to the stored `missingNodes`. The stored field is left as it is (no DB migration).
- Out of scope: showing the server's own `node_errors` (Phase 92), auto-fixing values, the workflow-list compatibility badge (nice-to-have, can build on this later).

### Claude's Discretion
- Dialog layout (grouping per node); helper names; whether the check runs on `Dispatchers.Default`.
</decisions>

<canonical_refs>
- `app/src/test/java/com/example/comfyui_remote/domain/corpus/ApiPromptValidator.kt` (logic to move)
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowExecutionService.kt` (`prepareAndQueue`), `WorkflowExecutor.kt`, `WorkflowPatchingService.kt`
- `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt` (`executeWorkflow`, `_nodeMetadata`), `QueueViewModel.kt`
- `app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt` (missing-node banner, Generate / Add to Queue buttons)
- ComfyUI `execution.py` (`validate_prompt`, `validate_inputs`), v0.37.2
</canonical_refs>
