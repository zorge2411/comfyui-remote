# Phase 84: Real Progress Indicator - Context

**Gathered:** 2026-09-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the per-node progress bar in the workflow form with a real overall progress bar for the whole execution. A per-step bar already exists from Phase 43: `MainViewModel.ExecutionProgress` (line ~240) is fed by the ComfyUI websocket in `handleMessage()` (`executing` / `progress` events) and rendered in `DynamicFormScreen.kt` (~line 379-410). Its weaknesses: it resets to 0% at every node, and it is indeterminate for every non-sampler node, so multi-stage workflows (upscale passes, video) jump backwards.

</domain>

<decisions>
## Implementation Decisions

- **D-01 Metric:** overall progress = (nodes completed + current node's step fraction) / total nodes that will run. Total = nodes in the queued workflow minus nodes the server reports as cached via the `execution_cached` websocket message (currently unhandled). Monotonic, never resets.
- **D-02 Layout:** one unified bar with a percentage, plus a text label under it naming the current node and, when a sampler reports steps, its step count (e.g. "KSampler 12/20"). Not two bars.
- **D-03 Surfaces:** form screen only (`DynamicFormScreen.kt`). Foreground notification, Queue screen and Gallery are out of scope.

### Claude's Discretion
- Where the counting logic lives; it should be a pure, unit-testable class rather than inline in the ViewModel (the project's tests cover stateless domain classes, there are no ViewModel tests).
- Clamp overall progress below 100% until the run actually finishes, so a miscounted total (e.g. subgraph/dynamic node ids) can never show 100% early or exceed 100%.

</decisions>

<canonical_refs>
## Canonical References

- `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt` — `ExecutionProgress` (~240), `handleMessage()` (~758), `resolveNodeTitle()` (~462), `parseAllNodes()`
- `app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt` (~379-410) — current bar UI
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt` — `parseAllNodes()` returns `NodeInfo(id, title, classType)` for the workflow's node count
- `.gsd/phases/82/82-PLAN.md` — plan format used in this project

No external specs.

</canonical_refs>

<code_context>
## Existing Code Insights

- Websocket events already handled: `execution_start`, `executing` (node id, null node = finished), `progress` (value/max), `executed`, `execution_error`. Missing: `execution_cached`.
- `cachedNodeTitles` already holds the id-to-title map for the selected workflow; its size is the total node count.
- Existing UI has the queued/executing states and a `LinearProgressIndicator`; reuse it.

</code_context>

<specifics>
No specific references beyond the decisions above.
</specifics>

<deferred>
## Deferred Ideas

- Weighting progress by sampler steps instead of node count (rejected for now; less accurate before the first sampler starts).
- Showing progress in the foreground notification.

</deferred>

---

*Phase: 84-Real Progress Indicator*
*Context gathered: 2026-09-24*
