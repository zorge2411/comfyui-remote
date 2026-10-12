# Phase 107 Summary: Visible Import and Sync Failures

**Completed:** 2026-10-12 (code). **Pending:** Android build and device check (Plan 97.2 checkpoint).

## Delivered

**Plan 97.1: `WorkflowImportPrep`** (`4bbb2af`)
- `prepare(json, nodeMetadata)`: API-format JSON is stored as is; editor-format JSON only after a successful conversion; invalid JSON, a missing server node list, or a converter exception are failures. It never returns an unconverted graph.
- `ImportFailure(step, name, message)` with steps downloading / loading the server's node list / converting / saving, and `describe()` mapping exceptions to plain language (server unreachable with its address, HTTP 404 and other codes, TLS, otherwise the message).
- 9 tests. They confirm the converter really throws on a node without a type, so that case is covered by a test, not an assumption.
- Placement differs slightly from the plan: `ImportFailure` and `Step` are nested in `WorkflowImportPrep`.

**Plan 97.2: wiring** (this commit)
- `MainViewModel`: new `importFailure`, `serverWorkflowsError` and `syncError` state, with `describeFailure()` adding the server address and HTTP code.
- `importWorkflowInternal`: fetches the node list only if the workflow needs it, then uses `WorkflowImportPrep.prepare`. Failures are reported at the right step (METADATA when the fetch failed, CONVERT, SAVE for normalization and the database insert). The old behaviour of saving raw graph JSON when conversion or metadata failed is gone.
- `importServerWorkflow`: reports download failures and a missing path.
- `fetchServerWorkflows`: sets `serverWorkflowsError`; success clears it.
- `syncHistory` and `clearAndRefreshHistory`: set `syncError`.
- UI:
  - Workflows screen: an "Import failed" dialog with Copy and OK, shown above the import dialog; an error card with Retry when the server's workflow list can't load.
  - Gallery and History: a snackbar for sync errors.
- Also fixed: `ErrorCard`'s Retry was plain text with no click handler, so `onRetry` was never called. It is now a button.

## Verification

- Plain-JVM suite passes, including `WorkflowImportPrepTest` (9).
- **Not compiled in the cloud session:** `MainViewModel.kt`, `WorkflowListScreen.kt`, `GalleryScreen.kt`, `HistoryScreen.kt`, `ErrorCard.kt`.
- Pending (user): `gradlew.bat testDebugUnitTest`, `assembleDebug`, `installDebug`, then:
  - (a) server off, import an editor-format workflow from a file → "Import failed … node list", nothing added to the list;
  - (b) server on → imports and runs as before;
  - (c) Workflows with the server off → error card; Retry works once the server is back;
  - (d) pull-to-refresh in Gallery with the server off → snackbar;
  - (e) invalid JSON in the import dialog → still stopped by the dialog's own check.

## Notes

- Behaviour change: an editor-format workflow can no longer be imported while the server's node list is unavailable. It is refused with a message instead of being saved in a broken state.
- Not changed: the silent node-list fetch on connect (`fetchNodeMetadata`). Import now retries the fetch itself and reports a failure.
