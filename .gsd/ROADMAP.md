# ROADMAP.md

> **Current Milestone**: Milestone 4 (all must-haves delivered in code; device checks pending)
> **Goal**: Workflow compatibility — more real-world ComfyUI workflows run unmodified from the phone, and when one can't, the app says exactly why before or after queueing.
> **Previous**: Milestone 3 archived in `.gsd/milestones/Milestone 3/` (summary: `Milestone 3-SUMMARY.md`)

## Must-Haves

- [x] Regression corpus of representative graph workflows that the converter test suite runs on every build (Phase 89)
- [x] Frontend-only / virtual nodes (Reroute, PrimitiveNode, SetNode/GetNode, Note) convert correctly, with type-aware passthrough (Phase 90)
- [x] Every corpus fixture passes: `known-failures.json` is empty (Phases 90, 93–95); 565 of 572 official templates clean, the other 7 not converter bugs
- [x] Pre-flight check against `/object_info` before queueing (missing node types, missing required inputs, invalid combo values), with no false "missing node" warnings for nodes the converter removes (Phase 91; device check pending)
- [x] All server `node_errors` shown to the user, per node, not just the first one (Phase 92; device check pending)

## Nice-to-Haves

- [ ] Compatibility badge on the workflow list (runs / warnings / will fail) based on the pre-flight check
- [x] Browse the server's workflow templates in the app (Phase 96)

## Phases

### Phase 89: Workflow Compatibility Regression Corpus

**Status**: ✅ Done (`.gsd/phases/89/`; corpus in `app/src/test/resources/workflow-corpus/`)
**Objective**: Build a set of sanitized graph workflow fixtures (no personal prompts, paths or server details) covering the shapes fixed in Milestone 3 (subgraphs, autogrow, bypass/mute, linked widgets, localized combos) plus common community workflows, and a test harness that converts each one and checks the result is structurally valid: every link resolves to a node in the output, no frontend-only nodes remain, and linked types match.

### Phase 90: Frontend-Only and Virtual Node Support

**Status**: ✅ Done, device-verified 2026-09-26 (`.gsd/phases/90/90-SUMMARY.md`)
**Objective**: Resolve the graph the way the ComfyUI frontend does before it builds the prompt:
- bypass follows `_getBypassSlotIndex` exactly (target type, drop when nothing matches);
- bypassed/muted **subgraph instances** aren't expanded (they pass through or produce nothing);
- virtual nodes are never sent: PrimitiveNode values become target literals, Reroute passes through the same slot, KJNodes GetNode resolves via the SetNode with the same name, Note/MarkdownNote are skipped;
- Phase 93 follow-up: interior socket inputs fed by a promoted widget get that widget's value.
**Corpus fixtures to fix**: `3d_hunyuan3d_multiview_to_model`, `audio_ace_step_1_5_checkpoint`, `hidream_e1_1`, `utility_topaz_illustration_upscale` (then `known-failures.json` is empty)
**Template-wide targets (Phase 95 run)**: 29 C7 (PrimitiveNode); 4 C3 (bypass); C4 in `video_wan2_2_14B_s2v` (23), `flux1_dev_uso_reference_image_gen`, `image_qwen_image_instantx_inpainting_controlnet` (bypassed subgraph instances), `image_ernie_image(_turbo)` (`PreviewAny.source`, promoted socket)
**Depends on**: Phase 89

### Phase 91: Pre-flight Compatibility Check

**Status**: ✅ Done, device-verified 2026-09-26 (`.gsd/phases/91/91-SUMMARY.md`)
**Objective**: Before queueing, validate the converted prompt against the server's `/object_info`: report missing node types, missing required inputs and invalid combo values in the app. Replace the current missing-node list, which can include nodes the converter already removed (e.g. Reroute).
**Depends on**: Phase 90

### Phase 92: Full Server Validation Error Reporting

**Status**: ✅ Done, device-verified 2026-09-26 (`.gsd/phases/92/92-SUMMARY.md`)
**Objective**: When `/prompt` returns `node_errors`, show every failing node with its title and type, and all of its errors, instead of only the first error of the first node. Research widened the scope:
- partial acceptance (HTTP 200 with `node_errors`) is silently ignored today;
- runtime `execution_error` shows no message;
- local-queue failures store no reason.

### Phase 93: Map Subgraph Instance Inputs by Name

**Status**: ✅ Done (`.gsd/phases/93/93-SUMMARY.md`)
**Objective**: Make subgraph expansion match the ComfyUI frontend. (A) Match instance inputs to subgraph inputs by name+type, then by name, not by position; position fails in 136 of 293 template instances (e.g. a width INT reaches `CLIPTextEncode.text`). (B) Apply the instance's promoted widget values (`widgets_values`) to interior nodes when the input isn't linked; today the stale interior values are sent (61 of 788 template values differ, including prompts).
**Discovered**: Phase 89 corpus baseline (2026-09-25); Bug B found while researching Phase 93
**Corpus fixtures to fix**: `image_boogu_image_0_1_turbo_t2i`, `image_mage_flow_t2i_int8`, `image_qwen_Image_2512_controlnet` (C4 part), `video_ltx2_depth_to_video` (C3/C4 part)

### Phase 94: Support COMFY_DYNAMICCOMBO_V3 Inputs

**Status**: ✅ Done; device check passed 2026-09-26 (`.gsd/phases/94/94-SUMMARY.md`)
**Objective**: A dynamic combo (e.g. `ResizeImageMaskNode.resize_type`, core `SaveVideo.format` → `codec` → `encoding`) stores its selected option plus that option's sub-widget values in `widgets_values`, and the API expects dotted keys (`resize_type.megapixels`). Expand the selected option's inputs from `/object_info`, recursively (like Phase 86 did for autogrow). Also: make the corpus validator see sub-inputs, and make the app form show V3 `COMBO` inputs as dropdowns, resolve dotted fields, and keep the dynamic key from being edited into an invalid state.
**Scale**: 401 of 572 templates, 152 of 962 stock node types.
**Discovered**: Phase 89 corpus baseline (2026-09-25)
**Corpus fixtures to fix**: `utility_image_stitch`, `image_qwen_Image_2512_controlnet`, `template_image_speech_to_video` (part), `video_ltx2_depth_to_video`, plus whatever the Plan 94.1 validator extension reveals (e.g. SaveVideo fixtures)

### Phase 95: Widget Mapping Gaps for V3 Nodes

**Status**: ✅ Done (code) (`.gsd/phases/95/95-SUMMARY.md`)
**Objective**: Read `widgets_values` the way the frontend writes them:
- skip control widgets (`control_after_generate`: 1 slot for INT/FLOAT, 2 for COMBO including the filter list);
- don't let unlinked socket inputs consume widget values;
- use `widgets_values_named` when present (132 templates);
- send frontend defaults for widgets added after the workflow was saved, instead of omitting required inputs.
**Scale**: the main causes of the 162 C5 and 109 C4 violations still left across the 572 templates after Phase 94.
**Discovered**: Phase 89 corpus baseline (2026-09-25); scope widened by the Phase 94 all-template measurement
**Corpus fixtures to fix**: `templates-character_sheet`, `template_image_speech_to_video`, `utility_depth_anything3_image_depth_estimation`, `hidream_e1_1` (C4 part)

### Phase 96: In-App Template Browser

**Status**: ✅ Done (code); device check pending
**Objective**: Show the connected server's workflow templates in the app without downloading files by hand. ComfyUI serves the `comfyui-workflow-templates` library at `/templates/` (`index.json` = 11 categories / 572 templates; thumbnails `/templates/<name>-1.<mediaSubtype>`; workflows `/templates/<name>.json`; checked in ComfyUI v0.37.2 `server.py` and frontend `useTemplateWorkflows.ts`).
**Delivered**:
- `WorkflowTemplateIndex` parser (4 tests; parses the real 572-template index);
- `ComfyApiService.getTemplateFile`;
- `MainViewModel.fetchTemplates` / `importTemplate` (new `WorkflowSource.SERVER_TEMPLATE`);
- `TemplatesScreen`: thumbnail grid, search, category chips, "Local only" filter hiding API-node templates, "API" badge. Tapping a template imports it through the normal converter and opens the form;
- entry points: grid icon in the Workflows top bar, and a "Browse Templates" button on the empty workflow list.
**Requested**: 2026-09-25 (user: "make the templates visible in the app without all the downloading stuff")

### Phase 97: Download Missing Models to the Server

**Status**: ✅ Done (code); device check pending (`.gsd/phases/97/`, `97-VERIFICATION.md`)
**Objective**: When a workflow needs model files the server doesn't have, let the user download them to the server from the phone. The file goes straight from its source to the server, never through the phone.
- **Keep the download links at import.** Workflows list their models as `properties.models` entries (`name`, `url`, `directory`), on nodes, inside subgraph definitions, and in the top-level `models` list. All 80 entries in the corpus have a huggingface.co URL. Today `WorkflowNormalizationService` strips these entries before storing the workflow. Collect them before conversion and store them in a new `modelSources` column (Room migration 11 → 12). Workflows imported earlier need to be imported again; server templates can be fetched again.
- **Work out what's missing.** For each stored link, check `/models/{directory}`. Workflows without links keep the `PromptValidator` "Value not in list" warnings.
- **Download through a companion extension** (decided 2026-09-26, see `97-RESEARCH.md`: Manager 3.x accepts only 6 of the 51 corpus models, and v4 refuses installs over `--listen`). `server/comfyui_remote_helper` streams Hugging Face / GitHub URLs into the model folder with progress, cancel and `HF_TOKEN`. Without the extension, the app shows the link to copy.
- **In the app.** The pre-flight dialog and the missing-items banner get a "Download to server" button per model, with a confirm step that shows the size (HEAD request) and the target folder, then progress. When the download finishes, reload `/object_info` and the model lists so the warnings clear.
**Risks**: gated Hugging Face models (e.g. Flux dev) need `HF_TOKEN` on the server and must show a clear message without it; ComfyUI has no login, so the extension limits downloads to https from huggingface.co / github.com, safe file types and registered model folders.
**Depends on**: Phase 91
**Requested**: 2026-09-26

**Tasks**:
- [x] 97.1 Keep model links at import (wave 1)
- [x] 97.2 Companion server extension (wave 1)
- [x] 97.3 Download from the app (wave 2); device check pending

**Verification**:
- `testDebugUnitTest`, Python unit tests, device check in 97.3
