# Phase 90: Frontend-Only and Virtual Node Support - Context

**Gathered:** 2026-09-26
**Status:** Ready for execution

<domain>
## Phase Boundary

Resolve the graph the way the ComfyUI frontend does before it builds the API prompt. That means:
- bypass slot matching;
- bypassed and muted **subgraph instances**;
- virtual nodes: PrimitiveNode, Reroute, KJNodes SetNode/GetNode, Note/MarkdownNote;
- one gap left over from Phase 93: interior socket inputs fed by a promoted widget when the instance saved no values.

Pre-flight checks (Phase 91) and error reporting (Phase 92) are out of scope.

</domain>

<research>
## Findings

### What is left after Phase 95

From the all-template run (572 templates, v0.37.2 `/object_info`):

| Check | Count | Cause |
|---|---|---|
| C7 | 29 | PrimitiveNode sent to the server, in 16 templates |
| C3 | 4 | bypass fallback |
| C4 | 37 | unresolved inputs |

Corpus known failures: 4, all Phase 90.

### Frontend behaviour

Source: ComfyUI_frontend `main` @ `80a8b15` (2026-09-26); KJNodes @ `d3cfe21`.

**Bypass** (`ExecutableNodeDTO._getBypassSlotIndex(slot, type)`). `type` is the type of the **final target input**, carried unchanged through chains. For output slot `s`:
1. If `type` is `*` or empty: use input `s` if it exists, else input 0.
2. If input `s` is compatible with both the output type and `type`: use `s`.
3. Otherwise use the first input whose type equals `type` exactly.
4. Otherwise use the first input compatible with both.
5. Otherwise the link is dropped (`resolveOutput` returns `undefined`).

`isValidConnection` treats `*` as matching anything and accepts comma-separated type lists that overlap.

Phase 88 matched on the output type and fell back to the same index, which is why `3d_hunyuan3d_multiview_to_model` wires CLIP_VISION into a CLIP_VISION_OUTPUT input (C3). The frontend drops that link.

**Virtual nodes** (`ExecutableNodeDTO.resolveOutput`, `isVirtualNode`):
- The output resolves through `resolveVirtualOutput(slot)` if the node defines it (cross-graph Set/Get), or else through `getInputLink(slot)`, the input at the **same slot index**.
- With neither, the link is dropped.
- Reroute has one input and one output, so this is the existing phantom passthrough, but now as an explicit rule.

**PrimitiveNode** (`widgetInputs.ts`):
- It's virtual and has no inputs, so links out of it are dropped.
- Before the prompt is built, `applyToGraph` copies its first widget value into every target widget (`applyFirstWidgetValueToGraph`), so each target sends that value as a literal.
- `widgets_values` is `[value, control_after_generate?, filter?]`. The control widgets follow Phase 95's rules for the target's spec.
- The "Run widget replace on values" property (`%date%`-style text replacement) is out of scope. Log it when set.
- The converter currently doesn't treat PrimitiveNode as a phantom (it has widgets and isn't a UUID type), so it's sent as a node (C7).

**KJNodes SetNode/GetNode** (`web/js/setgetnodes.js`):
- Both are virtual, and the name is `widgets_values[0]`.
- `GetNode.getInputLink(slot)` returns the link on the `SetNode` with the same name, at input `slot`. The lookup is the node's own graph, then its ancestor graphs.
- None of the official templates use them, so a synthetic test is needed.

**Bypassed or muted subgraph instances:**
- The converter expands every subgraph instance whatever its `mode`, so interior nodes run with inputs that were fed by bypassed nodes: 23 C4 in `video_wan2_2_14B_s2v`, plus `flux1_dev_uso_reference_image_gen` and `image_qwen_image_instantx_inpainting_controlnet`.
- In the frontend, a bypassed `SubgraphNode` goes through `resolveOutput`'s bypass branch like any other node: its outputs map to its inputs with the rule above, and none of its interior nodes execute. A muted one produces nothing.
- At runtime the instance's inputs are the full subgraph input list in order (Phase 93); the serialized list may be a subset.

**Promoted widget feeding a socket** (`image_ernie_image(_turbo)`, `PreviewAny.source`): a subgraph input feeds both a widget (e.g. a Primitive INT's `value`) and a socket input. The instance uses the proxyWidgets style, so it saved no values (`widgets_values []`).
- In the frontend, the promoted widget starts with the interior widget's value (`_setWidget`), and `resolveInput` gives that value to **every** interior target, sockets included.
- Phase 93 only sets values when the instance saved some, so the socket target gets nothing (C4).
</research>

<decisions>
## Implementation Decisions

- **D-01 Bypass rule:** replace `bypassInputLink` with the frontend rule. Pass the final target input's type through `resolveRealSource`: at the first call, use the graph slot's `type`. On the final `-1` result, drop the link. Compatibility means `*`, equality, or overlapping comma lists (the same helper as `ApiPromptValidator.compatible`; move a copy into main code).
- **D-02 Subgraph instance modes:** in `expandGraphOnce`, don't expand an instance whose `mode` is 2 or 4. Keep it as a node with its mode:
  - its `inputs` are rebuilt as the full `definition.inputs` list, in order, carrying the links mapped with `mapInstanceInputs`;
  - its `outputs` come from `definition.outputs`;
  - the existing muted/bypass handling in `convert()` then applies unchanged.
  Unexpanded instances and virtual nodes are never reported as missing nodes.
- **D-03 Virtual nodes:** Reroute, PrimitiveNode, SetNode, GetNode, Note and MarkdownNote are recognised by type and never emitted.
  - Reroute and other virtual nodes resolve through the same-slot input.
  - GetNode resolves through the SetNode with the same `widgets_values[0]`. After expansion, look it up across the whole flattened graph; the first match wins, and a name with no match drops the link.
  - Note and MarkdownNote are skipped.
- **D-04 PrimitiveNode:** when an input's link resolves to a PrimitiveNode, the target gets `primitive.widgets_values[0]` as a literal, passed through `resolveComboValue`, instead of a link. The target's own widget slot is still consumed (Phase 87 rule), and control slots are skipped (Phase 95). A Primitive reached through Reroute/Set/Get chains works the same way.
- **D-05 Promoted socket targets (Phase 93 follow-up):** when an instance has no value for a promoted input, record on each non-widget interior target a reference to the first widget target, as `(nodeId, inputName)`. After all nodes are emitted, copy that target's emitted literal into the socket input.
- **D-06:** remove the 4 corpus known failures, leaving `known-failures.json` empty, and re-measure all 572 templates. Add synthetic tests for Set/Get, since no template uses them.
- Out of scope: PrimitiveNode text replacement, Group Nodes (legacy `workflow>` group types), Phases 91 and 92.

### Claude's Discretion
- Helper names; whether virtual-node handling extends `phantomNodeInputs` or replaces it.
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`: pre-scan (phantom detection, `missingNodes`), `bypassInputLink`, `resolveRealSource`, `expandGraphOnce`, `promotedWidgetOverrides`, `emitInput`
- `app/src/test/resources/workflow-corpus/known-failures.json`
- Frontend: `src/lib/litegraph/src/subgraph/ExecutableNodeDTO.ts` (`resolveOutput`, `_getBypassSlotIndex`), `src/extensions/core/widgetInputs.ts` (`PrimitiveNode.applyToGraph`), `src/extensions/core/rerouteNode.ts`
- KJNodes: `web/js/setgetnodes.js` (`GetNode.getInputLink`, `findSetterByName`)
</canonical_refs>
