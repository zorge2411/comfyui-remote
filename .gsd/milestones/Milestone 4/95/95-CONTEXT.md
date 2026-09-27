# Phase 95: Widget Mapping Gaps for V3 Nodes - Context

**Gathered:** 2026-09-25
**Status:** Ready for execution

<domain>
## Phase Boundary

Make Mode B widget mapping in `GraphToApiConverter` read `widgets_values` the way the ComfyUI frontend writes them. That covers four things:
- control widgets (`control_after_generate`, `control_filter_list`);
- socket-only inputs, which have no widget;
- widgets added to a node after the workflow was saved, which fall back to defaults;
- `widgets_values_named`, when present.

PrimitiveNode, Reroute and bypass fallback belong to Phase 90. Value randomisation in the app, e.g. honouring "randomize", is out of scope.

</domain>

<research>
## Findings

### What is left after Phase 94

Converting all 572 official templates with stock v0.37.2 `/object_info` gives 306 violations (scratch run, 2026-09-25):

| Check | Count | Main causes |
|---|---|---|
| C5 | 162 | Control values in combo slots (`randomize` ×46, `fixed`) and the values shifted after them (`1K` ×22, `2K` ×16, `16:9`, …). Mostly Gemini/Grok/Tripo/ElevenLabs V3 nodes. |
| C4 | 109 | Widgets newer than the workflow: `ImageCompare.compare_view` ×43, `resolution_steps` ×5, `MoGeInference.refine_steps` ×5, `ResolutionSelector.multiple`, `ImageStitch.*`, `TextEncodeAceStepAudio1.5.*`, `ChromaRadianceOptions.*`. Also seeds lost to shifting (`KlingVideoNode.seed`, `model.seed`). |
| C7 | 29 | PrimitiveNode (Phase 90). |
| C3 | 4 | Bypass fallback (Phase 90). |
| C1 | 2 | Custom nodes that the stock server doesn't have (not a bug). |

A few C4 cases are unlinked sockets (`CLIPVisionEncode.image`, `MaskToImage.mask`, `PreviewAny.source`). They will be looked at after this phase and may belong to Phase 90.

### Frontend rules (ComfyUI_frontend `main` @ `adc55d7`)

**Control widgets** (`src/scripts/widgets.ts`, `renderer/extensions/vueNodes/widgets/composables/use{Int,Float,Combo}Widget.ts`):
- **INT:** a control widget is added when `control_after_generate` is truthy, **or** when it's absent and the input is named `seed` or `noise_seed`. An explicit `false` means no control widget. This goes through `addValueControlWidget`, which has `addFilterList: false`, so there's **1 extra slot**.
- **FLOAT:** 1 control widget when `control_after_generate` is truthy.
- **COMBO:** `addValueControlWidgets` when `control_after_generate` is truthy, which adds a control widget **plus a `control_filter_list` string widget**. That's **2 extra slots**.
- The control's default is `randomize` for INT (`fixed` when the spec gives a string) and `fixed` for FLOAT and COMBO. Its values are `fixed`, `increment`, `decrement`, `randomize`, plus `increment-wrap` for combos.
- Control widgets are `serialize: false`, so they're never in the API prompt. They **are** in `widgets_values` right after their target, even when the target input is linked.
- Today the converter skips a control value only when the next input's type rejects it. A V3 `COMBO` accepts any string, so `"fixed"`/`"randomize"` are consumed and every later widget shifts. This is the Phase 89 finding for `templates-character_sheet` and `template_image_speech_to_video`.

**Socket inputs:** inputs without a widget (IMAGE, MASK, …) have no `widgets_values` slot. At top level, the converter currently calls `findNextCompatibleWidget` for any unlinked graph slot. An unknown type counts as compatible with anything, so an unlinked optional socket can take the next widget's value. Phase 94 already fixed this for dynamic sub-inputs (`WIDGET_KINDS`).

**Defaults for missing widgets** (`use*Widget.ts`, `useComboWidget.getDefaultValue`): when a saved node has fewer `widgets_values` than the current definition has widgets, the new widgets keep their construction default:

| Type | Default |
|---|---|
| INT / FLOAT | `spec.default ?? 0` |
| STRING | `spec.default ?? ''` |
| BOOLEAN | `spec.default ?? false` |
| COMBO | `spec.default`, else the first option |

The converter leaves these inputs out, which gives the server's "Required input is missing" error.

**Named values** (`LGraphNode.serialiseWidgetValues` / `createWidgetRestorationState`):
- The frontend now saves `widgets_values_named` (`{widgetName: value}`, serialisable widgets only, dotted names for dynamic sub-widgets) alongside the positional array.
- 132 of the 572 templates have it, on 1,603 nodes.
- The frontend still restores positionally unless `LiteGraph.namedValuesRestore` is on, but both arrays come from the same widgets, so they agree at save time. When a node definition has since changed, only the named map stays correct.
</research>

<decisions>
## Implementation Decisions

- **D-01 Control widgets:** after reading a widget value for an input (top-level or dynamic sub-input), and even when the input is linked (Phase 87 rule), check the input's spec:
  - INT with `control_after_generate` truthy, or absent with name `seed`/`noise_seed`: skip 1 slot.
  - FLOAT with it truthy: skip 1 slot.
  - COMBO with it truthy: skip 2 slots.
  - Defensive rule: skip the control slot only if its value is one of `fixed`/`increment`/`decrement`/`randomize`/`increment-wrap`, because older saves may lack it. Skip the combo filter slot only if the control slot was skipped and the next value is a string.
- **D-02 Socket slots:** at top level too, an unlinked graph slot with no `widget` property, whose spec kind isn't a widget kind, consumes nothing. Unknown or custom types whose graph slot has a `widget` property keep today's behaviour.
- **D-03 Defaults:** when no widget value is left for a widget-kind input (neither positional nor named), emit the frontend default from D-01's table (combos: `default` or the first option; dynamic combos: `default` or the first option key, then expand that option). Only do this for widget kinds, never for sockets.
- **D-04 Named values first:** if the graph node has a `widgets_values_named` object:
  - use `named[path]` for each widget path (top-level or dotted). The map lists every saved widget, so a path missing from it is a widget added later: it gets the default (D-03), not the positional value. (Refined during execution.)
  - still advance the positional cursor as today, so paths missing from the map fall back consistently;
  - control widgets aren't in the map, which is fine.
  Phase 93 promoted values still win over both.
- **D-05 Validator:** add 3D and other asset file extensions to `FILE_VALUE` (`glb`, `gltf`, `fbx`, `obj`, `ply`, `stl`, `usdz`, `spz`, `splat`). `Load3D.model_file = "toy.glb"` is a server-file value, not a converter bug.
- **D-06:** the corpus known-failures entries citing Phase 95 must clear, which removes `known-failures.json` entries. Re-measure all 572 templates and record before/after in the summary.
- Out of scope: PrimitiveNode/Reroute/bypass (Phase 90), app-side seed randomisation, `COMFY_DYNAMICSLOT_V3`.

### Claude's Discretion
- Helper names; whether defaults and named lookup live in `findNextCompatibleWidget` or around it.
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`: Mode B `emitInput`, `findNextCompatibleWidget`, `WIDGET_KINDS`, promoted overrides
- `app/src/test/java/com/example/comfyui_remote/domain/corpus/ApiPromptValidator.kt` (`FILE_VALUE`)
- `app/src/test/resources/workflow-corpus/known-failures.json`
- Frontend: `src/scripts/widgets.ts` (`addValueControlWidget(s)`), `src/renderer/extensions/vueNodes/widgets/composables/use{Int,Float,Combo,String,Boolean}Widget.ts`, `src/lib/litegraph/src/LGraphNode.ts` (`serialiseWidgetValues`)
</canonical_refs>
