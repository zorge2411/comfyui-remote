# ROADMAP.md

> **Current Milestone**: Milestone 4 (Planned)
> **Goal**: Workflow compatibility — more real-world ComfyUI workflows run unmodified from the phone, and when one can't, the app says exactly why before or after queueing.
> **Previous**: Milestone 3 archived in `.gsd/milestones/Milestone 3/` (summary: `Milestone 3-SUMMARY.md`)

## Must-Haves

- [x] Regression corpus of representative graph workflows that the converter test suite runs on every build (Phase 89)
- [ ] Frontend-only / virtual nodes (Reroute, PrimitiveNode, SetNode/GetNode, Note) convert correctly, with type-aware passthrough
- [ ] Every corpus fixture passes: `known-failures.json` is empty (Phases 90, 93–95)
- [ ] Pre-flight check against `/object_info` before queueing (missing node types, missing required inputs, invalid combo values), with no false "missing node" warnings for nodes the converter removes
- [ ] All server `node_errors` shown to the user, per node, not just the first one

## Nice-to-Haves

- [ ] Compatibility badge on the workflow list (runs / warnings / will fail) based on the pre-flight check

## Phases

### Phase 89: Workflow Compatibility Regression Corpus

**Status**: ✅ Done (`.gsd/phases/89/`; corpus in `app/src/test/resources/workflow-corpus/`)
**Objective**: Build a set of sanitized graph workflow fixtures (no personal prompts, paths or server details) covering the shapes fixed in Milestone 3 (subgraphs, autogrow, bypass/mute, linked widgets, localized combos) plus common community workflows, and a test harness that converts each one and checks the result is structurally valid: every link resolves to a node in the output, no frontend-only nodes remain, and linked types match.

### Phase 90: Frontend-Only and Virtual Node Support

**Status**: ⬜ Not Started
**Objective**: Handle nodes that exist only in the editor. Phantom-node passthrough currently takes the first input link without checking its type (`GraphToApiConverter.resolveRealSource`); make it type-aware like the Phase 88 bypass logic, and resolve SetNode/GetNode pairs, which are linked by name rather than by a graph link. Resolve PrimitiveNode into its targets' widget values. Also fix the Phase 88 bypass fallback: when no input matches the output type, drop the link instead of wiring the same-index input (corpus: `3d_hunyuan3d_multiview_to_model`).
**Corpus fixtures to fix**: `3d_hunyuan3d_multiview_to_model`, `audio_ace_step_1_5_checkpoint`, `hidream_e1_1` (C7 part), `utility_topaz_illustration_upscale`
**Depends on**: Phase 89

### Phase 91: Pre-flight Compatibility Check

**Status**: ⬜ Not Started
**Objective**: Before queueing, validate the converted prompt against the server's `/object_info`: report missing node types, missing required inputs and invalid combo values in the app. Replace the current missing-node list, which can include nodes the converter already removed (e.g. Reroute).
**Depends on**: Phase 90

### Phase 92: Full Server Validation Error Reporting

**Status**: ⬜ Not Started
**Objective**: When `/prompt` returns `node_errors`, show every failing node with its title and type, and all of its errors, instead of only the first error of the first node (`MainViewModel` queue error handling).

### Phase 93: Map Subgraph Instance Inputs by Name

**Status**: ✅ Done (`.gsd/phases/93/93-SUMMARY.md`)
**Objective**: Make subgraph expansion match the ComfyUI frontend. (A) Match instance inputs to subgraph inputs by name+type, then by name, not by position; position fails in 136 of 293 template instances (e.g. a width INT reaches `CLIPTextEncode.text`). (B) Apply the instance's promoted widget values (`widgets_values`) to interior nodes when the input isn't linked; today the stale interior values are sent (61 of 788 template values differ, including prompts).
**Discovered**: Phase 89 corpus baseline (2026-09-25); Bug B found while researching Phase 93
**Corpus fixtures to fix**: `image_boogu_image_0_1_turbo_t2i`, `image_mage_flow_t2i_int8`, `image_qwen_Image_2512_controlnet` (C4 part), `video_ltx2_depth_to_video` (C3/C4 part)

### Phase 94: Support COMFY_DYNAMICCOMBO_V3 Inputs

**Status**: ⬜ Not Started
**Objective**: A dynamic combo (e.g. `ResizeImageMaskNode.resize_type`) stores its selected option plus that option's sub-widget values in `widgets_values`, and the API expects dotted keys (`resize_type.megapixels`). The converter doesn't expand them, so every later widget shifts. Expand the selected option's inputs from `/object_info`, like Phase 86 did for autogrow.
**Discovered**: Phase 89 corpus baseline (2026-09-25)
**Corpus fixtures to fix**: `utility_image_stitch`, `image_qwen_Image_2512_controlnet` (C5 part), `template_image_speech_to_video` (part), `video_ltx2_depth_to_video` (C5 part)

### Phase 95: Widget Mapping Gaps for V3 Nodes

**Status**: ⬜ Not Started
**Objective**: Two widget-mapping gaps. (1) The `control_after_generate` value ("fixed"/"randomize") after a seed is only skipped when the next widget rejects it by type; a V3 `COMBO` accepts any string, so it gets consumed and shifts later widgets. Skip it explicitly when the input's config has `control_after_generate`. (2) Inputs added to a node after the workflow was saved have no `widgets_values` entry; fall back to the `/object_info` default instead of omitting a required input.
**Discovered**: Phase 89 corpus baseline (2026-09-25)
**Corpus fixtures to fix**: `templates-character_sheet`, `template_image_speech_to_video` (part), `utility_depth_anything3_image_depth_estimation`, `hidream_e1_1` (C4 part)
