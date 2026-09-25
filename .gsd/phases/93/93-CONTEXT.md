# Phase 93: Map Subgraph Instance Inputs by Name - Context

**Gathered:** 2026-09-25
**Status:** Ready for execution

<domain>
## Phase Boundary

Make subgraph expansion in `GraphToApiConverter` (`expandGraphOnce`) match the ComfyUI frontend on two things:
1. How a subgraph instance's inputs map to the subgraph's inputs.
2. Where the values of promoted widgets come from.

Outputs, dynamic combos (Phase 94) and control_after_generate/defaults (Phase 95) are out of scope.

</domain>

<research>
## Findings

### Bug A: instance inputs are matched by position

- In `expandGraphOnce`, the External→Wrapper and Wrapper→Wrapper branches look up `wrapperInputRedirects[targetId][targetSlot]`. Here `targetSlot` is the index into the **instance node's** `inputs` array, but the redirect map is keyed by the subgraph-internal `-10` link's `origin_slot`, which is the index into **`definitions.subgraphs[].inputs`**.
- A serialized instance often lists only some of the subgraph's inputs, in a different order. For example, in `image_boogu_image_0_1_turbo_t2i.json` the instance lists `[width, height]` while the subgraph lists `[text, width, height, seed, unet_name, clip_name, vae_name]`. The width link therefore lands on `text`.
- Across all 572 official templates, 293 subgraph instances were counted:
  - 219 list fewer inputs than their subgraph;
  - 136 have at least one input whose position doesn't match its subgraph index.
- Outputs always match: in 0 of 293 instances do the output names differ. Nested subgraph definitions: 16.

### Frontend behaviour (source of truth)

Checked in ComfyUI_frontend `main` @ `adc55d7` (2026-09-24), `src/lib/litegraph/src/subgraph/`:

- `SubgraphNode.configure()` rebuilds `this.inputs` from `subgraph.inputNode.slots`, so at runtime there is one input per subgraph input, in subgraph order. `_rebindInputSubgraphSlots()` then binds each serialized input to a subgraph slot:
  - first by `name:type` signature,
  - then by `name` alone,
  - taking each slot at most once.
  Serialized inputs with no match are dropped.
- `ExecutableNodeDTO.resolveInput()`: an interior input linked from the subgraph input node goes to `subgraphNode.inputs[origin_slot]`.
  - If that input is linked outside, resolution continues through the outer link.
  - If it isn't linked and has a promoted widget, the widget's value is used (`widgetInfo`).
  - If neither applies, the input stays disconnected.
- **Promoted widget values:**
  - `_setWidget` initialises a promoted widget from the **first interior target that has a widget** (following `subgraphInput.linkIds` in order).
  - `_applyPromotedWidgetValues(info.widgets_values)` then overwrites the values **positionally**, one `widgets_values` entry per subgraph input that has a promoted widget, in subgraph input order.
  - An empty `widgets_values` (the newer `proxyWidgets` style) leaves the interior values as they are.
  - `properties.proxyWidgetErrorQuarantine` host values, keyed by input name, take priority.
  - `widgets_values_named` is used only when `LiteGraph.namedValuesRestore` is on, which is off by default.
- `ExecutableNodeDTO.resolveOutput()`: a bypassed node with no input of the matching type returns `undefined`, so the link is dropped. This confirms the Phase 90 finding for `3d_hunyuan3d_multiview_to_model`.

### Bug B: promoted widget values are ignored

The converter never reads an instance's `widgets_values`; interior nodes keep their own values. Across the templates there are 788 promoted values on instances, and 61 of them don't appear among the interior target's values. Examples:
- `video_minimax_h3_t2v.json`: a different prompt.
- `image_mage_flow_t2i_int8.json` (in the corpus): prompt and seed differ.

The app therefore sends different prompt text, seeds and sizes than ComfyUI shows for the same workflow. It shows no error; the values are silently wrong.

### Corpus impact

The known failures expected to clear are `image_boogu_image_0_1_turbo_t2i`, `image_mage_flow_t2i_int8`, the C4 part of `image_qwen_Image_2512_controlnet`, and the C3/C4 part of `video_ltx2_depth_to_video`. The remaining parts belong to Phase 94.
</research>

<decisions>
## Implementation Decisions

- **D-01 (Bug A):** for each wrapper instance, build `instanceSlot → subgraphInputIndex` using the frontend algorithm. Match the instance's `inputs[i]` to a `definition.inputs` entry, first by `name` + `type`, then by `name`, never assigning the same subgraph input twice; unmatched inputs are ignored. Use this map in the External→Wrapper and Wrapper→Wrapper branches before looking up `wrapperInputRedirects`. Change nothing on the output side.
- **D-02 (Bug B):** for each wrapper instance, decide which subgraph inputs are "promoted widgets": in subgraph input order, an input qualifies when one of its internal `-10` links, followed in `linkIds` order, targets an interior input slot that has a `widget` property. Assign `widgets_values` entries to those inputs by position. The value is applied only when **no external link** feeds that subgraph input (matching `resolveInput`).
- **D-03 (Bug B mechanism):** record the value on every interior target of that input as a per-node override keyed by widget name, e.g. a `"__promoted_widgets": {name: value}` object on the expanded node. Mode B checks it when it handles that key: the override value replaces the value from `widgets_values`, but the `widgets_values` slot is still consumed so later widgets stay aligned. Don't rewrite interior `widgets_values` arrays by index; that would repeat Mode B's widget-order logic.
- **D-04:** `proxyWidgetErrorQuarantine` host values (keyed by input name, `sourceNodeId == "-1"`) override the positional value. `widgets_values_named` is out of scope, since the frontend default ignores it.
- **D-05:** nested subgraphs need no special case. Each expansion pass treats the newly exposed inner instances as ordinary wrappers, so D-01 and D-02 apply at every level. A test must cover a promoted value at two levels.
- **D-06:** every fix removes the matching `known-failures.json` entries or narrows their checks (Phase 89 rule). The corpus test must pass.
- Out of scope: output mapping, Phase 94 dynamic combos, Phase 95 control_after_generate/defaults, UI changes.

### Claude's Discretion
- Helper names
- Override property name
- Whether the slot map is computed inside `expandGraphOnce` or in `parseSubgraphDefinitions`
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`: `expandGraphOnce` (External→Wrapper, Wrapper→Wrapper), `applySpecificInputUpdates`, Mode B loop (`findNextCompatibleWidget`, linked-slot branch)
- `app/src/main/java/com/example/comfyui_remote/domain/SubgraphDefinition.kt`
- `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterSubgraphTest.kt`: synthetic fixture style
- `app/src/test/resources/workflow-corpus/known-failures.json`
- Frontend reference: `SubgraphNode.ts` (`_rebindInputSubgraphSlots`, `_setWidget`, `_applyPromotedWidgetValues`) and `ExecutableNodeDTO.ts` (`resolveInput`)
</canonical_refs>
