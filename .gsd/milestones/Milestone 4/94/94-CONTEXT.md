# Phase 94: Support COMFY_DYNAMICCOMBO_V3 Inputs - Context

**Gathered:** 2026-09-25
**Status:** Ready for execution

<domain>
## Phase Boundary

Convert nodes that have `COMFY_DYNAMICCOMBO_V3` inputs correctly: expand the selected option's sub-inputs into dotted API keys, keep widget alignment, and make the app form handle them. Also extend the corpus validator so it can see missing or invalid sub-inputs; today it can't. `control_after_generate` values (including those inside options) belong to Phase 95.

</domain>

<research>
## Findings

### Server contract (ComfyUI v0.37.2, `comfy_api/latest/_io.py`)

- `DynamicCombo._expand_schema_for_dynamic` reads the selected key from `live_inputs[<id>]`, finds the option with that `key`, and parses the option's `inputs` with prefix `<id>`. Sub-inputs are named by `finalize_prefix`: `".".join(prefix + [id])`, for example `resize_type.megapixels`.
- This recurses for nested dynamic combos (`format.codec.encoding.crf`) and for autogrow inside an option (`model.images.image_1`).
- So the API prompt needs the combo key itself (`"resize_type": "scale total pixels"`) plus every sub-input of the selected option under its dotted name. Sub-inputs of options that aren't selected are ignored.

### Frontend layout (ComfyUI_frontend `main` @ `adc55d7`, `src/core/graph/widgets/dynamicWidgets.ts`)

- `dynamicComboWidget` creates a COMBO widget whose values are the option keys.
- When an option is selected, `updateWidgets` adds that option's inputs named `<widget>.<key>`: required first, then optional, in spec order. The new widgets are inserted **immediately after the combo widget**.
- Socket-only sub-inputs (IMAGE, MASK, ...) become graph input slots named `<widget>.<key>`, with no widget.
- Nested dynamic combos insert their own sub-widgets after themselves, so `widgets_values` holds them depth-first, e.g. SaveVideo `["video/ComfyUI", "auto", "auto"]` = filename_prefix, format, format.codec.
- Only the selected option's widgets exist, so values for other options are never serialized.

### Scale

- 152 of the 962 node types on stock v0.37.2 have a dynamic combo (125 of them are API nodes). This includes core **`SaveVideo`** (`format` → nested `codec` → nested `encoding`) and `ResizeImageMaskNode`.
- 401 of the 572 templates use one (708 node uses); 248 sub-inputs are linked.
- Kinds of sub-input found in option specs:

| Kind | Count |
|---|---|
| Primitive widgets | 993 |
| `COMBO` | 382 |
| Autogrow | 161 |
| Socket types | ~160 |
| Nested dynamic combos | 9 |
| Seeds with `control_after_generate` | 28 |

- `SaveVideo` also declares a top-level optional `codec` (legacy). Once `format.codec` consumes its widget, nothing is left for the top-level `codec`, so it's correctly left out.

### Current converter behaviour

- Mode B treats the dynamic key like a plain widget: `getExpectedType` returns `COMFY_DYNAMICCOMBO_V3`, which is compatible with anything. It takes the selected key but never consumes the sub-widget values, so every later widget shifts.
- Linked sub-input slots (`model.images.image_1`) are dropped, because only keys from `/object_info` and the Phase 86 autogrow groups are handled.

### Why the corpus shows only 4 failures

`ApiPromptValidator` looks up only top-level keys, so it can't see a sub-input that is missing (C4), an invalid sub-combo value (C5), or a mistyped linked sub-input (C3). Fixtures such as `text_to_video_wan.json` (SaveVideo) currently pass while sending no `format.codec`.

### App form (`WorkflowParser.parse`)

- `getOptionsFromMetadata` reads only legacy combos (`[[options], {...}]`). V3 `["COMBO", {"options": [...]}]` inputs, which make up most combos on newer nodes, appear as free-text fields.
- Dotted keys aren't resolved through the selected option.
- The dynamic combo key itself is shown as editable text. Changing it in the app would send the sub-inputs of the old option under the new key, and the server would reject the prompt (missing required sub-inputs).
</research>

<decisions>
## Implementation Decisions

- **D-01 Validator first:** teach `ApiPromptValidator` to resolve dotted keys through the selected dynamic option, recursively, and through autogrow templates:
  - C4 covers the selected option's required sub-inputs;
  - C5 covers sub-combos, including V3 `COMBO`;
  - C3 covers linked sub-inputs.
  Add fixtures that exercise nested, autogrow-in-option and linked-sub-input shapes. Re-baseline before touching the converter, so this phase's effect can be measured.
- **D-02 Converter (Mode B):** at a dynamic key K, consume one widget value as the selected key, resolved like a combo against the option keys, and emit `K = key`. Then walk the selected option's sub-inputs, required and then optional, in spec order, as `K.sub`:
  - nested dynamic combo → recurse;
  - autogrow → copy linked `K.sub.*` graph slots, as in Phase 86;
  - graph slot `K.sub` linked → emit the link, and if the slot has a `widget`, still consume its widget value (Phase 87 rule);
  - widget type → consume the next widget value and emit it;
  - socket-only and unlinked → nothing.
  Use the sub-input's own spec for type compatibility and combo resolution. If the key matches no option, emit the key only and log it; the server will report it.
- **D-03 Refactor:** make `getExpectedType`, `isCompatible` and `resolveComboValue` work on a spec (`JsonArray`), not only on a top-level key, so sub-inputs reuse them. Teach `resolveComboValue` the V3 `COMBO` options.
- **D-04 Form:**
  - `WorkflowParser` resolves metadata for dotted keys through the selected option.
  - It supports V3 `COMBO` options, so these become `SelectionInput` and not text.
  - It **omits the dynamic combo key itself** from the editable form. The key is still sent as converted.
  - Choosing a different option in the app would need the node's sub-inputs rebuilt from `/object_info`; that is left to a possible later phase.
- **D-05:** `control_after_generate` values inside options (28 seeds) belong to Phase 95. Fixtures that fail only for that reason are listed under Phase 95 in `known-failures.json`.
- **D-06:** each fix removes or narrows its `known-failures.json` entries; the corpus test must pass.
- Out of scope: switching dynamic options in the app, `COMFY_DYNAMICSLOT_V3`, Phase 95 work.

### Claude's Discretion
- Helper structure (recursive function vs explicit stack)
- The exact fixtures picked, within the shapes listed in `94-01-PLAN.md`
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`: Mode B loop, `getExpectedType`/`isCompatible`/`findNextCompatibleWidget`/`resolveComboValue`, Phase 86 autogrow block
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt`: `parse`, `getOptionsFromMetadata`
- `app/src/test/java/com/example/comfyui_remote/domain/corpus/ApiPromptValidator.kt`
- `app/src/test/resources/workflow-corpus/` (MANIFEST.md regeneration steps, known-failures.json)
- ComfyUI `comfy_api/latest/_io.py` (`DynamicCombo`, `finalize_prefix`, `parse_class_inputs`) and frontend `src/core/graph/widgets/dynamicWidgets.ts`
</canonical_refs>
