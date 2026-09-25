# Phase 91 Research: Pre-flight Compatibility Check

**Date:** 2026-09-25
**Discovery level:** 1. All internal work: every check already exists in `ApiPromptValidator`. This file records what the codebase showed and where it refines `91-CONTEXT.md`.

## 1. The validator already fits

- `ApiPromptValidator.validate(api, objectInfo, graph)` has no Android or test dependencies (Gson only), so it can move to `app/src/main/.../domain/` as it is.
- It has 4 callers, all in `app/src/test/.../domain/corpus/`: `WorkflowCorpusTest`, `AllTemplatesReportTest`, `ApiPromptValidatorTest`, and itself. `PromotedWidgetCorpusTest` doesn't use it.
- `graph` is used only to build the `skipped` set for C6, so making it nullable is a one-line change (D-02).
- C5 already ignores combos with an **empty** option list (`options.isNotEmpty()`). The stock snapshot has empty lists for every file combo, e.g. `CheckpointLoaderSimple.ckpt_name: [[], {...}]` and `LoadImage.image: [[], {"image_upload": true}]`. So turning off the `FILE_VALUE` skip in live mode changes nothing for the corpus (D-15 holds by construction). Live-mode tests need their own object_info with filled lists (D-14).

## 2. Upload inputs

- The snapshot has 3 upload inputs, each flagged in the spec config: `LoadImage.image` (`image_upload`), `LoadVideo.file` (`video_upload`), `LoadAudio.audio` (`audio_upload`). Treat any config key ending in `_upload` with value `true` as an upload input.
- `WorkflowPatchingService.patchApiFormat` writes the uploaded name into `inputs.image` of the node whose id is in `uploadedFilenames`. The uploaded name is the server's `response.name`, so it matches the patched value exactly and can be skipped by value.
- ComfyUI's server skips its own combo-list check for inputs that the node's `VALIDATE_INPUTS` covers, and `LoadImage` checks there that the file exists instead. A stale input image is therefore a real failure, just reported differently by the server.

**Refinement of D-03:**
- **Form-screen check:** skip file values on upload inputs. The stored workflow still holds the author's example image, and the user is expected to pick their own, so reporting it would be noise on almost every image workflow.
- **Queue-time check:** check them, but skip the values in `uploadedFilenames`. A LoadImage the user didn't touch that points at a file the server doesn't have is reported.

## 3. Queue paths

There are three places that send a prompt:

| Path | Where | Pre-flight |
|---|---|---|
| Generate | `MainViewModel.executeWorkflow` → `prepareAndQueue` | Yes. Dialog before the batch loop (D-05, D-08). |
| Queue button (local queue) | `MainViewModel.addToQueue` stores the workflow and inputs; `QueueViewModel` sends later in the background | Yes, at **add** time, with the same dialog and the form-screen rules (no uploads have happened yet). Not new in CONTEXT; added so the Queue button doesn't bypass the check. |
| Local queue runner | `QueueViewModel` around line 153 | None. It runs unattended and has no `/object_info` source (its constructor takes only the repositories and `WorkflowExecutionService`). The add-time check covers it; the server's error marks the item FAILED, as today. |

`prepareAndQueue` does patch → inject → queue in one call. Split it into:
- `prepare(workflowJson, uploadedFilenames, inputs): String`, which is pure and can be unit-tested;
- the queue call.

Keep `prepareAndQueue` as a wrapper so `QueueViewModel` is unchanged.

## 4. Form-screen inputs

- `DynamicFormScreen` holds `inputs` in local state, and the user can change the model dropdown there. The card should reflect the current choice, not only the stored JSON.
- So validate `workflowExecutor.injectValues(jsonContent, inputs)`, not the raw JSON. Recompute when `inputs` or `nodeMetadata` changes, debounced about 300 ms, off the main thread (`Dispatchers.Default`).
- `injectValues` already returns the original JSON on failure, so this is safe.

## 5. `/object_info` refresh

- `fetchNodeMetadata()` is fire-and-forget. For D-06, add a `suspend fun refreshNodeMetadata(): JsonObject?` that awaits the fetch, updates `_nodeMetadata`, and returns the new value (or null on failure).
- The existing function can call it.

## 6. Titles

- The converter writes `_meta.title` (`GraphToApiConverter.kt` ~271), and normalisation preserves it (`WorkflowNormalizationService.kt` 75–83).
- Pasted API-format workflows may have no `_meta`. Fall back to the class type alone.
