# Phase 91: Pre-flight Compatibility Check - Context

**Gathered:** 2026-09-25 (`--auto`: Claude picked the recommended option for every question; see `91-DISCUSSION-LOG.md`)
**Status:** Ready for planning

<domain>
## Phase Boundary

Before a prompt is queued, check it against the connected server's `/object_info` and tell the user, in the app, what will fail:
- node types the server doesn't have;
- required inputs that are missing;
- combo values the server doesn't offer, including model files that aren't installed.

The pre-flight result replaces today's import-time missing-node card on the form screen (`DynamicFormScreen.kt` lines 124–145), which shows a comma-separated string saved when the workflow was imported and is never refreshed.

Since Phase 90 the converter no longer reports frontend-only nodes (Reroute, PrimitiveNode, Note, MarkdownNote, SetNode, GetNode) or muted/bypassed subgraph instances as missing, so the check can trust the converted prompt.

Out of scope:
- Showing the server's `/prompt` `node_errors` in full: Phase 92. This phase only designs its issue list so Phase 92 can reuse it (D-13).
- Fixing converter gaps the check reveals, such as unlinked subgraph inputs feeding sockets: Phase 97.
- Offering to install missing custom nodes or models (ComfyUI-Manager integration).

</domain>

<decisions>
## Implementation Decisions

### Validator
- **D-01:** Move `ApiPromptValidator` from `app/src/test/.../domain/corpus/` to `app/src/main/.../domain/` and use the same checks in the app and in the corpus test. One validator, so the corpus keeps testing what users see. `WorkflowCorpusTest` and `ApiPromptValidatorTest` keep passing unchanged apart from the import.
- **D-02:** The graph argument becomes optional. C6 (muted/bypassed node sent) needs the saved graph, and at queue time the app only has the stored API-format prompt, so C6 runs only when a graph is given, i.e. in the corpus test.
- **D-03:** Add a live mode, used by the app, that also checks file names in combo values (models, LoRAs, input images). The corpus test keeps skipping them (`FILE_VALUE`), because a snapshot can't know the server's files. A live server can, and a missing model is the most common reason a shared workflow fails.
  - Values the app has just uploaded (`uploadedFilenames`) are skipped: the cached `/object_info` won't list them yet.
  - Inputs with `image_upload: true` in their spec are skipped the same way when the value came from the gallery or camera.
- **D-04:** Keep the check IDs (C1–C7) internally for logs and tests. The user sees plain wording (D-09).

### When it runs
- **D-05:** Run the check in two places, both with the same validator:
  1. **On the form screen**, when it opens and whenever `nodeMetadata` changes (reconnect, other server): validate the stored `jsonContent`. This drives the card that replaces the missing-node card.
  2. **At queue time**, in `executeWorkflow` before the batch loop: validate the prompt after uploads are patched and values injected (the same JSON `prepareAndQueue` would send). Once per batch, not per item; the random seed doesn't affect the result.
- **D-06:** If issues are found at queue time, refetch `/object_info` once and validate again before showing anything, so a server where the user just installed a node or model isn't reported against a stale cache. Refetching also updates `_nodeMetadata` for the rest of the app.
- **D-07:** If no `/object_info` is available (not connected, fetch failed), skip the check, log it, and queue as today. The server still validates.

### What happens when issues are found
- **D-08:** Warn, don't block. At queue time, show a dialog listing the issues with **Queue anyway** and **Cancel**. The server is the authority: its `/object_info` can lag a custom-node reload, and a false block would be worse than the server's own error.
  - Implementation shape: `executeWorkflow` gets a `skipPreflight` flag; with issues it sets a `preflightIssues` state and returns; **Queue anyway** calls it again with `skipPreflight = true`.
- **D-09:** Show issues grouped by node, using `_meta.title` and the class type ("Load Checkpoint (CheckpointLoaderSimple)"). Wording per check:
  - C1: "Node type X isn't installed on the server".
  - C4: "Required input *name* has no value or link".
  - C5 file values: "*value* isn't on the server" plus up to 5 available options; other C5: "*value* isn't a valid option".
  - C2, C3, C7 (converter problems, not user-fixable): one line "The workflow didn't convert cleanly (details in the log)" with the node, so they are visible but not alarming.
- **D-10:** Order: missing node types first (the cause of most other issues on the same nodes), then missing models/files, then the rest. Cap the dialog at a scrollable list; don't truncate.
- **D-11:** On the form screen the card shows a one-line summary ("2 missing node types, 1 missing model") that expands to the same grouped list. No issues: no card.

### Missing-node list replacement
- **D-12:** Stop showing `WorkflowEntity.missingNodes` on the form screen; the live check replaces it. Keep the column and keep filling it at import (the workflow list or import log may still use it, and removing it needs a Room migration for no gain). The researcher checks whether anything else reads it; if nothing does, say so in the plan and leave it.
- **D-13:** Put the issue model (`PreflightIssue(nodeId, title, classType, kind, message)`) and the grouped list composable in their own files so Phase 92 can map server `node_errors` onto them.

### Verification
- **D-14:** Unit tests for the live mode: missing model, uploaded file skipped, `image_upload` input skipped, unknown class type, missing required input, grouping and ordering. Run against the stock `object_info` snapshot from the corpus.
- **D-15:** Corpus results stay identical: `known-failures.json` stays empty and the all-template numbers from Phase 90 don't change.
- **D-16:** Device check: a workflow with a model that isn't on the server shows the card and the dialog, **Queue anyway** still queues, and a clean workflow shows neither.

### Claude's Discretion
- Class and file names (`PreflightChecker`, `PreflightIssue`, `PreflightIssueList`).
- Dialog vs bottom sheet for D-08, as long as it has both actions.
- Whether the form-screen check runs in the ViewModel or in a `remember`/`LaunchedEffect` in the screen.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Code
- `app/src/test/java/com/example/comfyui_remote/domain/corpus/ApiPromptValidator.kt`: the checks to move (D-01–D-03), `FILE_VALUE`, `effectiveSpecs` (dynamic combos), `comboOptions`.
- `app/src/test/java/com/example/comfyui_remote/domain/corpus/WorkflowCorpusTest.kt` and `ApiPromptValidatorTest.kt`: callers to update.
- `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`:
  - `executeWorkflow`, around lines 576–670 (queue path and the current `node_errors` handling that Phase 92 replaces);
  - `_nodeMetadata` and its fetch, around lines 682–700;
  - import path writing `missingNodes`, around lines 1060–1150.
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowExecutionService.kt`: `prepareAndQueue` (patch → inject → queue). The pre-flight needs the injected JSON before the queue call; split or add a `prepare` step.
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowPatchingService.kt`: which node inputs get uploaded file names.
- `app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt`: missing-node card (lines 124–145), Generate button calling `executeWorkflow` (around line 484).
- `app/src/main/java/com/example/comfyui_remote/data/WorkflowEntity.kt`: `missingNodes` column (D-12).
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`: `_meta.title` written at line ~271 (D-09).

### Prior phase decisions
- `.gsd/phases/89/89-CONTEXT.md` D-05: check definitions C1–C7.
- `.gsd/phases/90/90-CONTEXT.md` D-01, D-02: frontend-only nodes not reported missing.
- `.gsd/phases/94/94-CONTEXT.md`: dotted dynamic-combo inputs the validator already understands.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ApiPromptValidator` already covers every check the phase needs, including V3 autogrow and dynamic combos.
- `ErrorCard` in `DynamicFormScreen.kt` for styling the warning card.
- The `connectionState` collector refetches `/object_info` on every connect (Phase 81 fix), so `nodeMetadata` is normally fresh.

### Established Patterns
- Stored workflows are already API format; conversion happens at import.
- Seeds are randomised per batch item in `executeWorkflow`; pre-flight runs once before that loop.
- Log tags per area (`EXECUTE_DEBUG`, `IMPORT_DEBUG`); use `PREFLIGHT_DEBUG`.

### Integration Points
- `nodeMetadata: StateFlow<JsonObject?>` (form screen and ViewModel).
- `uploadedFilenames` from `workflowExecutionService.uploadImages` (D-03 skip list).

</code_context>

<specifics>
## Specific Ideas

- The live file-name check is the biggest user-visible gain: the corpus can't test it, so D-14 needs synthetic cases with an edited copy of the snapshot's `CheckpointLoaderSimple.ckpt_name` options.
- Phase 97's two ernie templates will show a C4 in pre-flight until Phase 97 lands. That's correct behaviour, not a pre-flight bug.

</specifics>

<deferred>
## Deferred Ideas

- Full `node_errors` display after the server rejects a prompt: Phase 92 (reuses D-13).
- One-tap "pick an installed model instead" fix for a missing model value: possible follow-up once the issue list exists.
- Installing missing nodes or models via ComfyUI-Manager.
- Dropping the `missingNodes` column.

</deferred>

---

*Phase: 91-pre-flight-compatibility-check*
*Context gathered: 2026-09-25*
