# Phase 85: Fix Subgraph Flattening Output-Link Bug

## Goal Description

Fix `GraphToApiConverter`'s subgraph-instance detection so a subgraph node lacking `properties.proxyWidgets` (but whose `type` matches a real parsed subgraph definition) is properly expanded instead of falling through to the crude phantom-flattening heuristic, which was wiring `SaveVideo`'s `video` input to `LoadImage`'s `IMAGE` output and causing an `HTTP 400 prompt_outputs_failed_validation` error.

Root cause fully diagnosed in `85-CONTEXT.md` against the real failing workflow, fetched live from the user's ComfyUI server. This is a confirmed 2-line fix, not an open design question — this plan is about applying it correctly and proving it.

## Proposed Changes

### 1. Fix subgraph-instance detection in `expandGraphOnce`

**Modify** `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` (~line 502):

Change:
```kotlin
val isSubgraph = def != null && node.has("properties") && node.get("properties").asJsonObject.has("proxyWidgets")
```
to:
```kotlin
val isSubgraph = def != null
```
`def` is already `definitions[type]` (looked up a few lines above, ~line 501). No other logic in this function needs to change — `inputRedirects`/`outputRedirects`/link remapping already correctly handle arbitrary input/output counts once a node is correctly identified as a subgraph instance.

### 2. Fix subgraph-instance detection in `convert()`'s phantom pre-scan

**Modify** `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` (~line 78-79):

Change:
```kotlin
val isSubgraph = node.has("properties") && node.get("properties").asJsonObject.has("proxyWidgets")
```
to:
```kotlin
val isSubgraph = definitions.containsKey(type)
```
`definitions` is already a local `val` at the top of `convert()` (line ~19, from `parseSubgraphDefinitions(graph)`), in scope at this point — no new parameter/plumbing needed. This is the same authoritative signal as change #1, just accessed via the `definitions` map directly since `def` (from `objectInfo.dynamicNodes`) isn't the right lookup here — this pre-scan is checking against subgraph *definitions*, not server node metadata.

**Note:** by the time `convert()`'s pre-scan runs, `graph` has already been reassigned to the *expanded* graph (line 22: `graph = expandGraph(graph, definitions)`) if any definitions were found. So after fix #1 correctly expands node 105, this pre-scan should no longer even encounter it as a UUID-typed node — the pre-scan check here matters for any subgraph-definition-matching node that *still* wasn't expanded for some other reason (defense in depth), and for correctness/consistency with change #1.

## Verification Plan

### New unit tests (permanent, synthetic data — no real/sensitive fixture committed)

**Modify** `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterSubgraphTest.kt`:

1. **`expandGraph_SubgraphWithoutProxyWidgets_StillExpandsCorrectly`** — duplicate the existing `expandGraph_SimpleSubgraph_ExpandsCorrectly` test's JSON but remove `"properties": { "proxyWidgets": true }` from the wrapper node entirely (or set `"properties": {}`). Assert the same expansion outcome (2 nodes: `LoadImage` + `UpscaleImage`, correctly relinked) — this is the regression test proving the fix, since this exact test currently fails with the `proxyWidgets` requirement in place if that property is absent.

2. **`convert_SubgraphWithoutProxyWidgets_ResolvesToInternalProducer_NotFirstInput`** — a new test exercising the *full* `convert()` pipeline (not just `expandGraph`), reproducing the actual bug shape: a subgraph definition with **two** external inputs of different types (e.g. `IMAGE` and `STRING`) feeding two different internal nodes, where the internal node connected to the subgraph's *first* input is NOT the one producing the subgraph's declared output — and a downstream consumer node (with real `/object_info` metadata, type-checked) reads the subgraph's output. Before the fix, `convert()` would (if `expandGraph` were somehow skipped/failed) wire the consumer to the wrong upstream node; after the fix, expansion happens correctly and the consumer's link resolves to the correct internal producer. Use synthetic node/type names — do not reference `MiniMaxH3ImageToVideo`, `CreateVideo`, or any real prompt content from the actual failing workflow.

### Regression check

```bash
./gradlew.bat testDebugUnitTest
```

Full suite must stay green, including all existing `GraphToApiConverter*Test` classes (`GraphToApiConverterFallbackTest`, `GraphToApiConverterFlatteningTest`, `GraphToApiConverterSubgraphTest`, `GraphToApiConverterWidgetTest`) and `WorkflowPatchingServiceTest`.

### Live proof-of-fix (one-time, not committed to the repo)

Since a real ComfyUI server and the actual failing workflow are available (already fetched once this session into the local scratchpad, not the repo):

1. Temporarily add a throwaway JUnit test method (in a scratch location, or the existing test class — **removed before final commit**) that reads the real fixture from its scratchpad path, runs `GraphToApiConverter.convert()` against it with the real `/object_info` (also already fetched), and asserts the resulting API JSON's node `92` (`SaveVideo`) `video` input link references the internal `CreateVideo`-derived node ID, not `LoadImage`'s node ID.
2. Extract the resulting API JSON and `POST` it to the live server's `/prompt` endpoint (same shape as the app's real request: `{"prompt": <api_json>, "client_id": "<anything>"}`) — confirm the response is no longer an `HTTP 400 prompt_outputs_failed_validation`.
3. Delete the throwaway test method and any scratch script/output before finalizing — the real workflow content (NSFW prompt) must never land in a commit.

### Build Verification

```bash
./gradlew.bat assembleDebug
./gradlew.bat installDebug
```

No device UI testing is required for this phase — verification is proven directly via the live API round-trip in step above. `installDebug` is still run to keep the on-device build current for the still-open Phase 81 live-test follow-up.

## Success Criteria

- [ ] `isSubgraph` in both `expandGraphOnce` and `convert()`'s pre-scan keys off `definitions.containsKey(type)` / `def != null`, not `properties.proxyWidgets`
- [ ] New synthetic unit tests added and passing, proving the fix without embedding real/sensitive workflow data
- [ ] Full `testDebugUnitTest` suite passes (no regressions in existing subgraph/converter tests)
- [ ] Live proof-of-fix: the real failing workflow, converted with the fixed code, no longer triggers `HTTP 400 prompt_outputs_failed_validation` when POSTed to the live server's `/prompt`
- [ ] No real workflow content, prompt text, or scratchpad file path lands in any commit
- [ ] `assembleDebug` / `installDebug` succeed
