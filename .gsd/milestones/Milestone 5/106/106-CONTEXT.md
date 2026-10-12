# Phase 106 Context: Pinned Form Fields and Motion

**Gathered:** 2026-09-30, split out of the Phase 105 discussion. Milestone 5 nice-to-haves. Runs after Phase 105.

## User decisions (2026-09-30)

1. **Pinned form fields: a pin icon per field.**
   - Each field in the node groups gets a small pin action, either in its label row or in a long-press menu.
   - Pinned fields show in a "Pinned" group right under the prompt, per workflow.
   - They are kept across launches, stored with the workflow (Room 15 → 16).
2. **Motion transitions:**
   - **Card → form:** the workflow card's thumbnail grows into the form's result preview.
   - **Result → viewer:** the form's result preview grows into the full-screen viewer (gallery → viewer already animates).
   - **Tab switches:** not animated beyond today.

## Starting points

- **Form layout:** `domain/FormLayout.build` from Phase 101 (main settings and per-node groups). Pinned fields are a new group placed before the main settings, or merged with them. To be decided at planning, with a JVM test.
- **Remembered values:** `WorkflowEntity.savedInputs` (Phase 101). Pins get their own column, e.g. `pinnedFields` (a JSON list of `nodeId/fieldName`). Don't mix them into `savedInputs`, because Reset clears that.
- **Motion:**
  - `SharedTransitionLayout` already wraps the NavHost in `MainActivity.kt`, with gallery → viewer shared elements (Phase 35/104).
  - The card thumbnail (Phase 103 `WorkflowCard`) and the form's result preview (Phase 101 status area) need matching keys, for example the media id or the workflow id.

## Out of scope

- Reordering pinned fields (possible later).
- Tab-switch animations.

## Addendum (2026-10-01, planning)

- **D-01: Pin scope.** Only fields inside the per-node groups get a pin. The prompt, negative and Main settings stay as they are. A pinned field moves into "Pinned", where the same icon unpins it. A pin on a key that has become a main setting is ignored.
- **D-02: Storage.**
  - `workflows.pinnedFields` holds a JSON list of `"nodeId/fieldName"` keys (Room 15 → 16), written by a targeted UPDATE.
  - The post-run `lastImageName` write also becomes a targeted UPDATE. The survey found it rewrote a stale row snapshot, which would undo pins and form values saved since the workflow was selected.
- **D-03: Labels.** Pinned fields use their humanized name, with the node title prefixed when two pinned labels collide.
- **D-04: Motion keys.**
  - Card → form uses `"workflow-thumb-<workflowId>"`. The form only learns the media id asynchronously, and this key doesn't clash with the gallery's `"image-<id>"`.
  - Preview → viewer reuses `"image-<mediaId>"`.
  - `selectWorkflow` takes the list's known `lastResult`, so the preview URL and id are set synchronously.
