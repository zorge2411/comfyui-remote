# Phase 91 Summary: Pre-flight Compatibility Check

**Completed:** 2026-09-26 (code). **Pending:** Android build and device check (Plan 91.2 checkpoint).

## Delivered

**Plan 91.1: `PromptValidator` in main code** (`c6a576c`)
- `domain/PromptValidator.kt` follows ComfyUI's `validate_prompt`/`validate_inputs`. It checks only nodes reachable from `output_node` nodes, and reports:

| Kind | Severity |
|---|---|
| missing node type | error |
| no output node | error |
| bad link | error |
| return type mismatch | error |
| required input missing (including dynamic-combo sub-inputs) | error |
| INT/FLOAT conversion | warning |
| min/max range | warning |
| value not in list, including model/file lists | warning |

- Messages use the server's wording. Value problems are warnings because a node can accept them through its own validation, which `/object_info` doesn't reveal.
- The corpus `ApiPromptValidator` is now a thin wrapper: C1–C5 come from `PromptValidator` (every node, no value checks, file names skipped), and C6/C7 from the graph. Corpus and template results are unchanged (565/572).
- Two converter fixes, found while moving the checker:
  - List-valued widgets are sent wrapped as `{"__value__": [...]}` (frontend `executionUtil.graphToPrompt`). Phase 95 sent `IMAGECOMPARE` as a bare `["", ""]`, which the server reads as a link.
  - Combo options may be numbers (`CreateVideo.bit_depth ["auto", 8, 10]`).
- 10 tests in `PromptValidatorTest`.

**Plan 91.2: pre-flight and live banner** (`5350e45`)
- `WorkflowExecutionService.buildPrompt` holds the patch and inject steps; `prepareAndQueue` uses it, so its behaviour is unchanged. Test: `WorkflowExecutionServiceTest`.
- `MainViewModel.preflight(workflow, inputs)`:
  - validates the prompt Generate / Add to Queue would send, against live `/object_info` (fetched if missing);
  - returns null, and the app queues without a check, when metadata can't be fetched;
  - skips list checks for LoadImage inputs with a pending upload.
- `DynamicFormScreen`:
  - Generate and Queue run the check first. If it finds issues, a dialog lists errors and warnings grouped by node, with a count line, and offers **Queue anyway** / **Cancel**.
  - The buttons are disabled while checking.
  - The "Missing Nodes on Server" banner uses `missingNodeTypes()` against the live server and falls back to the stored import-time list.

## Verification

- Plain-JVM suite passes: `PromptValidatorTest` (10), `ApiPromptValidatorTest` (18, unchanged) and the corpus.
- **Not compiled in the cloud session:** `MainViewModel.kt`, `WorkflowExecutionService.kt`, `DynamicFormScreen.kt` and `WorkflowExecutionServiceTest.kt` (they depend on Android/Compose).
- Pending (user): `gradlew.bat testDebugUnitTest`, `assembleDebug`, `installDebug`, then the device checks:
  - (a) a workflow using a model that isn't on the server → warning dialog naming the model;
  - (b) a normal workflow → no dialog;
  - (c) "Queue anyway" queues;
  - (d) the banner is gone for a workflow with a stale stored missing-node list.

## Notes

- Local-queue items are checked when added, not again when they execute.
- The workflow-list compatibility badge (nice-to-have) can reuse `preflight`.
