# Phase 90: Frontend-Only and Virtual Node Support - Context

**Gathered:** 2026-09-25 (`--auto`: Claude picked the recommended option for every question; see `90-DISCUSSION-LOG.md`)
**Status:** Ready for planning

<domain>
## Phase Boundary

Make `GraphToApiConverter` resolve nodes that exist only in the editor the way the ComfyUI frontend does, so none of them reach the server and their links and values land on the real nodes:
- **Phantom passthrough** (Reroute and other unknown pass-through nodes): pick the input by type, not "the first input link" (`resolveRealSource`).
- **PrimitiveNode**: its value becomes a widget value on each target input. The node itself is never sent.
- **SetNode/GetNode** (KJNodes): these pairs are linked by name, not by a graph link. A GetNode output resolves to the source of the SetNode input with the same name.
- **Bypass fallback** (Phase 88): when no input matches the output type, drop the link instead of wiring the input at the same index.
- **Investigate**: links whose source doesn't resolve around bypassed or muted groups, found in the Phase 95 all-template run.

Done when `known-failures.json` is empty, and across all 572 templates C7 goes 29 → 0 and C3 goes 4 → 0.

Out of scope: the pre-flight check and missing-node UI (Phase 91), server `node_errors` reporting (Phase 92), and other custom virtual-node packs such as cg-use-everywhere.

</domain>

<decisions>
## Implementation Decisions

### Virtual node detection
- **D-01:** Treat a node as frontend-only by an explicit type set: `Reroute`, `PrimitiveNode`, `Note`, `MarkdownNote`, `SetNode`, `GetNode`. Apply this only when `/object_info` has no definition for the type, because a server that defines the type wins.
  - Today's rule stays for other unknown types: UUID-typed, or no content.
  - This matters because PrimitiveNode, SetNode and GetNode all have `widgets_values`, so they currently fail the "contentless" test and fall through to the heuristic mapper. They are then sent to the server.
- **D-02:** Frontend-only nodes the converter resolves are **not** added to `missingNodes`. This is the converter-side half of the Phase 91 "no false missing-node warnings" must-have. The rest of the pre-flight UI stays in Phase 91.
- **D-03:** Remove the stale comment block at the end of `convert()` (the "Remove bypassed nodes from missing nodes list…" musings) once D-02 is in place.

### Type-aware phantom passthrough
- **D-04:** `resolveRealSource` passes down the type the consumer expects: the link's `type` field, or failing that the phantom node's output type. For a phantom node, choose in this order:
  1. the first linked input whose type equals that type;
  2. failing that, its only linked input when there is exactly one (Reroute: `*` types);
  3. failing that, drop the link and log a `CONVERT_DEBUG` warning.
- **D-05:** Wildcard `*` on either side counts as a match. Reroute slots are typed `*` in older saves.

### PrimitiveNode
- **D-06:** A target input whose link resolves to a PrimitiveNode, directly or through Reroutes, gets the primitive's `widgets_values[0]` as a **value**. It never gets a link.
  - The value goes through `resolveComboValue`.
  - The target's own `widgets_values` slot is still consumed, with control-widget skipping as in Phase 87/95, so later widgets stay aligned.
  - Frontend reference: `widgetInputs.ts` `PrimitiveNode.applyToGraph` writes the primitive's value into each target widget at queue time, so the primitive's value wins over the target's saved value if the two differ. In all 3 corpus fixtures they match.
- **D-07:** A PrimitiveNode with no linked targets, or with an empty `widgets_values`, is dropped. The primitive's own control value (`"fixed"`/`"randomize"`, `widgets_values[1]`) is ignored, the same as Phase 95's rule for control widgets: the app doesn't randomise.
- **D-08:** A primitive's mode (muted or bypassed) doesn't stop its value applying unless the frontend skips it. The researcher checks what `applyToGraph` / `graphToPrompt` do for virtual nodes in non-ALWAYS modes. If unsure, apply the value.
- **D-09:** PrimitiveNodes inside subgraphs, and primitives feeding a subgraph instance input, must work after `expandGraph`. Add a synthetic test for the instance-input case, which goes through the Phase 93 remapping.

### SetNode / GetNode
- **D-10:** The key is `widgets_values[0]`, the constant name, on both nodes.
  - A GetNode output link resolves to the SetNode input link with that name, then continues through `resolveRealSource`, so chains through Reroutes and bypassed nodes work.
  - Matching happens on the expanded (flattened) graph.
  - If several SetNodes share a name, the lowest node id wins and a warning is logged.
  - If no SetNode has the name, the link is dropped and a warning is logged.
- **D-11:** No corpus fixture has these nodes (KJNodes isn't in the official templates). Cover them with synthetic tests: a basic pair, a chain through a Reroute, a missing name, and a type-mismatch pair.

### Bypass fallback
- **D-12:** Match the frontend's bypass resolution (`ExecutableNodeDTO` bypass path).
  - Try the same-index input first, then the others, and accept an input only when its type equals the output type (`*` counts as a match).
  - If nothing matches, the link is **dropped**; there is no same-index fallback any more. This fixes `3d_hunyuan3d_multiview_to_model` (CLIP_VISION wired into CLIP_VISION_OUTPUT).
  - The researcher confirms the exact order, and whether a matching but unlinked input stops the search or lets it continue to the next input.

### Unresolved link sources (investigation)
- **D-13:** Re-run the all-template measurement. For `video_wan2_2_14B_s2v`, `flux1_dev_uso_reference_image_gen`, `image_ernie_image(_turbo)` and `image_qwen_image_instantx_inpainting_controlnet`, find the root cause:
  - If the cause is bypass, mute or virtual-node resolution, fix it in this phase.
  - If it's something else, file it as a new roadmap phase with the evidence. Don't fix it here.
- **D-14:** The frontend drops an optional input whose source doesn't resolve. For a **required** input it still sends nothing, and the server error is correct. Don't invent values.

### Verification
- **D-15:** Clear all 4 `known-failures.json` entries, so the file is an empty object. Record the all-572 before/after numbers (C3, C7, total clean prompts; 542 of 572 now) in `90-SUMMARY.md`, as Phases 94 and 95 did.
- **D-16:** Keep the existing Reroute corpus fixtures and Phase 88 bypass tests green. Update any Phase 88 test that asserts the old same-index fallback, and say so in the summary.

### Claude's Discretion
- Helper and data-structure names, e.g. a `VirtualNodes` pre-pass or a sealed "resolved source = link | value" result from `resolveRealSource`.
- Whether primitive and SetNode/GetNode handling lives in `resolveRealSource` or in a separate pre-pass that rewrites links.
- Debug log wording.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Converter and tests
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`:
  - phantom pre-scan, around lines 57–122;
  - mode handling and `bypassInputLink`, around lines 124–156;
  - `resolveRealSource`, around lines 158–191;
  - `nonExecutableTypes`, around line 219;
  - Mode B `emitInput` / `addLink`, around lines 370–455;
  - the fallthrough heuristic mapper, around lines 456–515;
  - the stale missing-nodes comments, around lines 520–548.
- `app/src/test/java/com/example/comfyui_remote/domain/corpus/ApiPromptValidator.kt`: `FRONTEND_ONLY` (C7), C3 type check. Consider adding `SetNode`/`GetNode` to `FRONTEND_ONLY`.
- `app/src/test/resources/workflow-corpus/known-failures.json`: 4 Phase 90 entries.
- `app/src/test/resources/workflow-corpus/workflows/`:
  - `hidream_e1_1.json`: 2 FLOAT primitives into `DualCFGGuider`.
  - `audio_ace_step_1_5_checkpoint.json`: 1 primitive into two targets; seed primitive into `KSampler.seed`.
  - `utility_topaz_illustration_upscale.json`.
  - `3d_hunyuan3d_multiview_to_model.json`.
- Existing converter tests for bypass/mute (Phase 88), subgraphs (Phases 85, 93) and widgets (Phases 87, 94, 95) under `app/src/test/java/com/example/comfyui_remote/domain/`.

### Prior phase decisions
- `.gsd/phases/95/95-CONTEXT.md`: control-widget skipping, named values, defaults (D-01–D-04). PrimitiveNode targets must keep these rules.
- `.gsd/phases/95/95-SUMMARY.md` § "What's left": the unresolved-source template list and the all-template numbers.
- `.gsd/phases/93/93-SUMMARY.md`: subgraph instance-input mapping (relevant to D-09).
- `.gsd/phases/89/89-SUMMARY.md`: corpus harness and checks C1–C7.

### ComfyUI frontend (Comfy-Org/ComfyUI_frontend `main`)
- `src/extensions/core/widgetInputs.ts`: `PrimitiveNode` (`applyToGraph`, `isVirtualNode`).
- `src/lib/litegraph/src/subgraph/ExecutableNodeDTO.ts`: bypass resolution by type, and virtual-node / Reroute passthrough.
- `src/utils/executionUtil.ts` (`graphToPrompt`): when `applyToGraph` runs, and how modes are skipped.
- `src/extensions/core/rerouteNode.ts`: legacy Reroute node.
- KJNodes `web/js/setgetnodes.js` (kijai/ComfyUI-KJNodes): SetNode/GetNode name linking.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `bypassInputLink` / `resolveRealSource`: extend these rather than adding a second resolver.
- `resolveComboValue`, `findNextCompatibleWidget`, `skipControlWidgets`, `controlSlots`: reuse them for primitive values on target inputs.
- The corpus harness (`WorkflowCorpusTest`, `ApiPromptValidator`) and the Phase 95 scratch all-template runner: use them to measure before and after.

### Established Patterns
- Links in the graph JSON are arrays `[id, origin_id, origin_slot, target_id, target_slot, type]`. Subgraph definitions may use the object form. The type is already there for D-04.
- Every decision is traced to a specific frontend source file. Phases 93–95 used this "match the frontend" approach, and this phase keeps it.
- `CONVERT_DEBUG` println logging for every drop or rewrite.

### Integration Points
- `ConversionResult.missingNodes` is consumed by the app's missing-node warning (D-02).
- `expandGraph` runs before node processing, so virtual nodes inside subgraphs come out already flattened.

</code_context>

<specifics>
## Specific Ideas

- In all 3 PrimitiveNode fixtures the target's saved `widgets_values` already equals the primitive's value, so either source passes the corpus. D-06 still uses the primitive's value, to match `applyToGraph`.
- Native LiteGraph reroutes (link midpoints in `extra.reroutes` / `links[].parentId`) are visual only, and links still run origin → target. No work is needed; just check that one fixture with them still passes.

</specifics>

<deferred>
## Deferred Ideas

- Other custom virtual-node packs: cg-use-everywhere broadcast links, rgthree Context/Reroute variants. Their own phase, if real workflows need them.
- App-side value randomisation, i.e. honouring a primitive's `control_after_generate`. Already out of scope in Phase 95.
- Pre-flight UI and the missing-node list redesign: Phase 91.

</deferred>

---

*Phase: 90-frontend-only-and-virtual-node-support*
*Context gathered: 2026-09-25*
