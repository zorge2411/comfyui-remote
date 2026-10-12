# Phase 100 Summary: Compatibility Badge on the Workflow List

**Status**: Code done; device check pending (CI result below).

## Delivered

- `domain/WorkflowCompatibility.kt`: `assess(issues)` (READY / WARNINGS / WILL_FAIL with the first reason; missing node types listed once each, sorted) and `check(json, objectInfo)` (unreadable JSON → null; ignores file-list mismatches on `image`, `video`, `audio`, `mask`, which the form replaces at run time).
- 8 unit tests plus a corpus test: no converted corpus workflow is badged will-fail against the snapshot.
- `MainViewModel.compatibility`: `combine(allWorkflows, nodeMetadata, connectionState)` → `mapLatest` on `Dispatchers.Default`; empty unless CONNECTED with a node list; cached per (workflow id, JSON hash) and cleared when the node list instance changes (every reconnect). Declared after `nodeMetadata` so it initialises in order.
- `WorkflowListScreen`: `✓ Ready` / `⚠ N warnings` / `✕ <reason>` line under each local workflow card, theme colours; nothing while disconnected.

## Device check
1. Connected: a good workflow shows Ready; the list opens without delay.
2. A workflow needing a missing node shows "Missing nodes: …".
3. A workflow naming a checkpoint the server lacks shows a warning.
4. A LoadImage pointing at a missing file is not flagged.
5. Disconnect: badges vanish; reconnect: they return.
6. Import a workflow: its badge appears without reopening the screen.
