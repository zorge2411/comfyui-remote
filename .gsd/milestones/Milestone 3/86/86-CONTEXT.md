# Phase 86: Support COMFY_AUTOGROW_V3 Dynamic Input Type - Context

**Gathered:** 2026-09-24 (auto mode: decisions made from research, no user questions)
**Status:** Ready for planning

<domain>
## Phase Boundary

Make `GraphToApiConverter` (graph-format to API-format, Mode B "inputs is a JSONArray") understand `COMFY_AUTOGROW_V3` inputs so workflows using nodes like `ComfyMathExpression` stop failing `required_input_missing`.

</domain>

<research>
## Findings (live `/object_info` from the user's server, 3 MB, 1119 nodes)

- 46 inputs across many nodes use `COMFY_AUTOGROW_V3` (e.g. `BatchImagesNode.images`, `BatchMasksNode`, `BatchLatentsNode`, `StringFormat.values`, `ComfyMathExpression.values`, `MiniMaxH3ReferenceToVideo.ref_images/ref_videos/ref_video_audios`, `TextEncodeQwenImage21.images`). It appears in both `required` and `optional`.
- Declaration shape: `["COMFY_AUTOGROW_V3", {"template": {...}}]`. Two template variants: (a) `{input, prefix, min, max}` (numbered slots), (b) `{input, names:[a..z], min}` (named slots). `template.input.required` holds one sample slot definition (e.g. `["IMAGE",{}]`, `["FLOAT,INT,BOOLEAN",{}]`, `["*",{}]`).
- Graph JSON represents each expanded slot as its own entry in the node's `inputs` array with a dotted name `<groupKey>.<slotName>` (e.g. `values.a`, `values.b`), per the Phase 86 discovery.
- Two defects in `GraphToApiConverter.kt` Mode B (lines ~261-288):
  1. The loop iterates `allInputKeys` from object_info (`values`), and `slotNames.contains("values")` is false for dotted names, so the dotted slot links are never copied into the API JSON. This is the reported `required_input_missing`.
  2. Because `values` isn't a slot, it falls into the widget branch, and `findNextCompatibleWidget("values")` accepts any widget (`isCompatible` returns true for unknown types), which consumes a `widgets_values` entry that belongs to another input. Verified impact: `StringFormat` (`values` first, then `f_string`) would have its `f_string` template stolen; this is a latent silent bug beyond the reported one.
- The form parser (`WorkflowParser`) is unaffected: link inputs are non-primitive and skipped.

</research>

<decisions>
## Implementation Decisions

- **D-01:** For each object_info input whose type is `COMFY_AUTOGROW_V3`, copy every graph input slot named `<key>.<anything>` that has a link into the API inputs under the same dotted name, resolving the link with the existing `resolveRealSource()` flattening (same as normal linked slots). Do not validate against `names`/`prefix`/`max`; trust the graph.
- **D-02:** Never route an autogrow key through `findNextCompatibleWidget`; it must not consume `widgets_values`.
- **D-03:** Unlinked dotted slots are omitted (server enforces `min`). Widget-valued autogrow slots (non-link template types) are out of scope; none of the 46 declarations in the live server need it beyond link types except possibly `*`/scalar types, which are still link-fed in graphs.
- **D-04:** Out of scope: Mode A (`inputs` already an object: keys copied verbatim, so dotted keys already work), the no-metadata fallthrough path, and rendering autogrow inputs in the form.

### Claude's Discretion
Helper structure inside `GraphToApiConverter.kt`; test naming.

</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` Mode B block (~186-290), `resolveRealSource`
- `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterTest.kt` (fixture style: object_info JSON string wrapped in `ComfyObjectInfo`)
- `.gsd/ROADMAP.md` Phase 86 discovery notes
</canonical_refs>

<deferred>
- Form UI for widget-valued autogrow slots.
- Live re-verification against the real MiniMax H3 workflow (contains sensitive prompt text, not committed).
</deferred>

---

*Phase: 86-Support COMFY_AUTOGROW_V3*
