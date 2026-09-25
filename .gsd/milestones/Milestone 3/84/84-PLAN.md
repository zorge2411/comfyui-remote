# Phase 84: Real Progress Indicator

## Goal Description

Show one overall progress bar for the whole workflow run instead of the current per-node bar that resets at every node. Decisions are locked in `84-CONTEXT.md`: nodes-completed metric, single bar with a node/step label, form screen only.

## Proposed Changes

### 1. New pure tracker

**Create** `app/src/main/java/com/example/comfyui_remote/domain/ExecutionProgressTracker.kt`:

- `class ExecutionProgressTracker` with mutable state and these methods:
  - `start(totalNodes: Int)`: reset; store the total.
  - `markCached(nodeIds: Collection<String>)`: add to a `cached` set (ids only counted once).
  - `onExecuting(nodeId: String)`: if a previous node was running, add it to `completed`; set `currentNodeId`; reset the step fraction to 0.
  - `onStep(value: Int, max: Int)`: set the current step fraction (0 if `max <= 0`) and keep `currentStep` / `maxSteps`.
  - `finish()`: mark everything done.
  - `snapshot(): Snapshot` where `Snapshot(overall: Float, currentStep: Int, maxSteps: Int, completedNodes: Int, totalToRun: Int)`.
- `totalToRun = max(totalNodes - cached.size, 1)`.
- `overall = ((completed - cached).size + stepFraction) / totalToRun`, clamped to `[0f, 0.99f]` until `finish()` is called, then `1f`.

### 2. Wire into `MainViewModel`

**Modify** `MainViewModel.kt`:

- Add `overallProgress: Float = 0f` to `ExecutionProgress` (keep existing fields, `currentStep`/`maxSteps` still drive the label).
- Add a private `ExecutionProgressTracker` field; a helper that publishes `tracker.snapshot()` into `_executionProgress` (copying node id/title through).
- In `handleMessage()`: `execution_start` calls `tracker.start(cachedNodeTitles size)` (make sure titles are resolved for the selected workflow first by calling `resolveNodeTitle(wf, "")` or extracting a `totalNodeCount()` helper); new `"execution_cached"` case reads `data.nodes` (JsonArray of strings) and calls `markCached`; `executing` with a node id calls `onExecuting`; `progress` calls `onStep`; the null-node/`executed` finish paths call `finish()` before resetting state as today.
- If no workflow is selected or the total is unknown (0), fall back to today's behavior: `overallProgress` stays 0 and the UI shows the indeterminate bar.

### 3. UI

**Modify** `DynamicFormScreen.kt` (~379-410): bar `progress = { executionProgress.overallProgress }` when a total is known, indeterminate otherwise; percentage text from `overallProgress`; label `Executing: <node title>` plus ` (step/max)` when `maxSteps > 0`.

## Verification Plan

**Create** `app/src/test/java/com/example/comfyui_remote/domain/ExecutionProgressTrackerTest.kt` (JUnit4, backtick names like `WorkflowParserTest`):

1. `overall never decreases across nodes`: 4 nodes, feed executing/progress events, assert each snapshot >= previous, including when a second sampler's `progress` restarts at 0.
2. `cached nodes are excluded from the total`: total 5, 2 cached, 3 executed, assert progress reaches the cap only via `finish()`.
3. `step fraction contributes within the current node`: 2 nodes, first done, second at 10/20, assert ~0.75.
4. `clamped below 100 until finish`: all nodes executing done but no `finish()` gives <= 0.99; after `finish()` gives 1.0.
5. `unknown total yields zero-safe result`: `start(0)` does not divide by zero.

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat installDebug
```

Live check on the Fairphone 6: run a multi-node workflow (the simple img2img workflow works) and confirm the bar rises smoothly to the end without resetting, the label shows the current node and sampler step, and re-running an unchanged workflow (server-cached nodes) still finishes near 100% rather than stalling.

## Success Criteria

- [ ] Overall bar is monotonic across a whole run and never shows 100% before completion
- [ ] Label shows current node title and sampler step count when available
- [ ] `execution_cached` nodes are excluded from the total
- [ ] Unknown total falls back to the indeterminate bar
- [ ] New tracker unit tests and the full `testDebugUnitTest` suite pass; `assembleDebug` succeeds
- [ ] Verified live on device
