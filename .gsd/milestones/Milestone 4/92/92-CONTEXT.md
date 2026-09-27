# Phase 92: Full Server Validation Error Reporting - Context

**Gathered:** 2026-09-26
**Status:** Ready for execution

<domain>
## Phase Boundary

When the server rejects or fails a prompt, show the user everything it said, per node, by title. That covers validation errors on `/prompt`, partial acceptance, runtime execution errors, and local-queue failures. Pre-flight (Phase 91) happens before sending; this phase is about what comes back.

</domain>

<research>
## Findings

### Server responses (ComfyUI v0.37.2 `server.py` `post_prompt`, `execution.py` `validate_prompt`)

- **Rejected (HTTP 400):**
  ```
  {"error": {"type": "prompt_outputs_failed_validation", "message": "Prompt outputs failed validation", "details": "<every reason, one per line>", "extra_info": {}},
   "node_errors": {"<node_id>": {"errors": [{"type", "message", "details", "extra_info": {"input_name", ...}}], "dependent_outputs": [...], "class_type": "..."}}}
  ```
  Other 400s return `{"error": {...}, "node_errors": {}}`: `missing_node_type`, `prompt_no_outputs` and invalid-prompt cases.
- **Partially accepted (HTTP 200):** `{"prompt_id", "number", "node_errors": {...}}`. When some outputs fail validation and others pass, the server queues only the valid outputs and still lists the failing nodes. The app's `PromptResponse` has only `prompt_id`, so this is **silently ignored**; the user just gets fewer outputs.
- **Runtime failure** (websocket `execution_error`, not broadcast, sent to the client that queued it): `{prompt_id, node_id, node_type, executed, exception_message, exception_type, traceback, current_inputs, current_outputs}`. `execution_interrupted` has `prompt_id, node_id, node_type, executed`.
- Only nodes that have reasons appear in `node_errors`; downstream nodes that fail because of them are left out.

### App today

- **`MainViewModel.executeWorkflow` (HTTP 400):** shows only the **first error of the first node** ("Validation Error on Node 3 (KSampler): …"). It uses the node ID rather than the title, and ignores `extra_info.input_name`, the remaining errors, and the other nodes.
- **`execution_error`:** sets status ERROR but **no message**, so the form shows no error card.
- **`QueueViewModel.processQueueItem`:** on any exception it sets `QueueStatus.FAILED` with **no reason stored** (`LocalQueueItem` has no error field). Room DB is at version 10 with hand-written migrations.
- `ErrorCard(title, message)` renders a single text block.
- The prompt actually sent (with `_meta.title`) is at hand: `_executionCache[prompt_id]`, and the `buildPrompt` output (Phase 91).
</research>

<decisions>
## Implementation Decisions

- **D-01 One parser:** a pure-Kotlin `domain/ServerErrorReport.kt` turns each server payload into a `ServerErrorReport(summary, nodes: List<NodeError(nodeId, classType, title, errors: List<Reason(inputName?, message, details)>)>, isPartial)`. Titles come from the sent prompt's `_meta.title`, falling back to `class_type`. It handles:
  - a 400 body, with or without `node_errors`;
  - a 200 body with `node_errors`;
  - an `execution_error` message;
  - an unparseable body (the HTTP code plus the raw text, first 500 characters).
- **D-02 Wording:** `format()` gives readable text:
  - the summary line;
  - then per node, "Title (Class #id)";
  - then "• input: message — details" per reason, dropping details that just repeat the input name, which is what the server's `details` often contains.
  All nodes and all reasons are listed; nothing is truncated except the raw-body fallback.
- **D-03 Partial acceptance:** `PromptResponse` gains `node_errors: JsonObject?`. When it's non-empty on a 200, the form shows a warning card ("Some outputs were skipped by the server") with the formatted nodes, while the run continues.
- **D-04 Runtime errors:** `execution_error` sets `errorMessage` to "Node failed: Title (Class): ExceptionType: message". The title is looked up in `_executionCache[prompt_id]`. The traceback isn't shown.
- **D-05 Local queue:** add `errorMessage TEXT` to `local_queue` (Room 10 → 11, `ALTER TABLE ... ADD COLUMN`). On failure, `processQueueItem` stores the formatted report, or the exception message, and `QueueScreen` shows it under FAILED items.
- **D-06 Display:** `ErrorCard` keeps its API. The formatted text goes in `message`, and the card gets a "Copy" action and a max height with scrolling for long reports.
- Out of scope: showing tracebacks, retry or auto-fix, and the pre-flight (Phase 91).

### Claude's Discretion
- Class and helper names; exact card layout.
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt` (`executeWorkflow` catch, `handleMessage` `execution_error`, `_executionCache`)
- `app/src/main/java/com/example/comfyui_remote/QueueViewModel.kt` (`processQueueItem`), `data/LocalQueueItem.kt`, `data/AppDatabase.kt` (migrations), `ui/QueueScreen.kt`
- `app/src/main/java/com/example/comfyui_remote/network/ComfyApiService.kt` (`PromptResponse`)
- `app/src/main/java/com/example/comfyui_remote/ui/components/ErrorCard.kt`, `ui/DynamicFormScreen.kt`
- ComfyUI `server.py` (`post_prompt`), `execution.py` (`validate_prompt`, `handle_execution_error`), v0.37.2
</canonical_refs>
