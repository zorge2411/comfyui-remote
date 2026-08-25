# Phase 85: Fix Subgraph Flattening Output-Link Bug — Context

**Date**: 2026-08-25
**Domain**: `GraphToApiConverter.kt`'s subgraph-instance detection — fixing a wrong-node-link bug for embedded ComfyUI subgraphs that lack `properties.proxyWidgets`.

Root cause was fully diagnosed by fetching and analyzing the actual failing workflow (`video_minimax_h3_i2v.json`) and the server's `/object_info` directly against a live server (Tailscale IP reachable from dev environment). No further research needed — this is a confirmed bug with a confirmed fix, not an open design question.

## Root Cause (confirmed against real data)

`GraphToApiConverter` has two separate subgraph-handling code paths:

1. **`expandGraphOnce`** (proper expansion) — correctly expands a subgraph-instance node into its internal nodes, rewiring boundary links (`-10`/`-20`) using per-slot `inputRedirects`/`outputRedirects` maps. This handles multi-input/multi-output nodes correctly.
2. **`convert()`'s phantom-node flattening** (`resolveRealSource`, lines ~123-146) — a much cruder fallback for nodes missing `/object_info` metadata. For a phantom node, it just takes **input link index 0** of that node and recursively resolves through it, assuming the phantom node is a simple 1-in-1-out passthrough (like a `Reroute` node). It has no concept of output-slot correspondence.

Both paths gate "is this a subgraph instance" on: `def != null && node.has("properties") && node.get("properties").asJsonObject.has("proxyWidgets")` (`expandGraphOnce` ~line 502, `convert()` pre-scan ~line 78-79). `def` comes from `definitions[type]`, populated by `parseSubgraphDefinitions()` reading the workflow's embedded `definitions.subgraphs` block — this is already the authoritative "is this type a known subgraph" signal.

**The bug:** for the real failing workflow, node 105 (type = UUID `4c314f31-ecda-4b08-ae98-faaba1bf613f`, matching a real definition "Image to Video (MiniMax H3)" with 15 internal nodes) has `properties: {"previewExposures": []}` — **no `proxyWidgets` key**. So `isSubgraph` evaluates `false` in both places despite `def != null` being true. The node is never expanded via path 1; it falls through to path 2's crude flattening instead. Since node 105 has widgets (a full sampler config) it's *not* "contentless," but it also has non-null links on 3 of its input slots (`first_frame` → link 218, `width` → link 219, `height` → link 220), so it's misclassified as a "flattening candidate" (category A in the pre-scan, not category C "producer, keep it"). `resolveRealSource` then blindly takes `inputs[0]` = link 218 = `first_frame`, sourced from a `LoadImage` node (114) — instead of properly expanding into the subgraph and reaching the internal `CreateVideo` node (91), which the definition's own internal links confirm is the real producer of the subgraph's declared `VIDEO` output (`{"id":168,"origin_id":91,"origin_slot":0,"target_id":-20,"target_slot":0,"type":"VIDEO"}`).

Result: `SaveVideo` (node 92)'s `video` input gets wired to `LoadImage`'s `IMAGE` output → server rejects with `HTTP 400 prompt_outputs_failed_validation`: *"Return type mismatch between linked nodes: video, received_type(IMAGE) mismatch input_type(VIDEO)"*.

## Decisions

### Fix approach
- **Drop the `properties.proxyWidgets` requirement** from both `isSubgraph` checks. Use `definitions.containsKey(type)` (equivalently, `def != null`) as the sole signal that a node type is a subgraph instance that should be expanded via `expandGraphOnce`.
- Applies in exactly two places: `GraphToApiConverter.kt` `expandGraphOnce()` (~line 502: `val isSubgraph = def != null && node.has("properties") && node.get("properties").asJsonObject.has("proxyWidgets")` → becomes `val isSubgraph = def != null`) and `convert()`'s pre-scan (~line 78-79: `val isSubgraph = node.has("properties") && node.get("properties").asJsonObject.has("proxyWidgets")` → becomes `val isSubgraph = definitions.containsKey(type)`). `definitions` (from `parseSubgraphDefinitions`) is already a local val in scope at both sites — no new plumbing needed, this is a pure condition change.
- No other changes to the flattening/phantom-node heuristic itself (`resolveRealSource`) — fixing detection so this node routes through proper expansion makes the crude heuristic irrelevant for this case. The crude heuristic's own multi-input blind-first-link behavior is a separate, lower-priority correctness gap (only matters for genuinely-missing/unregistered node types with multiple inputs) — **not in scope for this phase** unless it turns out other cases need it.

### Verification method
- **Verify directly via API, not just the device UI.** The exact failing workflow JSON and the server's `/object_info` have already been fetched from the live server (Tailscale-reachable). Plan is to: run the fixed converter logic against this real data (either via a unit test using the real fixture, or a scripted repro), confirm the resulting API JSON links `SaveVideo`'s `video` input to `CreateVideo`'s output, and POST the result to the live server's `/prompt` endpoint to confirm no validation error — without requiring another device round-trip.
- **Sensitive data handling:** the real workflow JSON contains a NSFW prompt string in its widget values. It must **not** be committed to the repo or left in the project directory — kept only in the session scratchpad, referenced by absolute path if needed for a throwaway verification script, and not embedded verbatim into any committed test fixture. If a unit test needs a fixture, construct a minimal synthetic JSON that reproduces the same structural bug (subgraph instance node lacking `proxyWidgets`, linked to a downstream type-checked consumer) rather than using the real prompt text.

## Deferred Ideas
- The `resolveRealSource` heuristic's "always take input[0]" behavior for genuinely-unregistered phantom nodes (not subgraph instances) is still fragile for any real multi-input passthrough-like custom node. Not touched here — only relevant if a *different* bug report surfaces for a node that's correctly identified as non-subgraph-phantom but still multi-input.

## Canonical Refs
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` — both `isSubgraph` check sites (`expandGraphOnce` ~line 502, `convert()` pre-scan ~line 78-79), plus `resolveRealSource` (~line 124-146) for context on why detection failure matters
- `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterSubgraphTest.kt` — existing subgraph test coverage (untracked in git as of this session — verify it exists and check whether it already covers a `proxyWidgets`-present case, so the new test can cover the *absent* case without duplicating)
- Session scratchpad: `video_minimax_h3_i2v_debug.json` (real failing workflow, fetched from `http://<server>:8188/api/userdata/workflows%2Fvideo_minimax_h3_i2v.json`) — contains sensitive prompt text, not for repo inclusion
- `.gsd/phases/81/*` — where this bug was originally discovered during live testing
