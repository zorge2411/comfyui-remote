# Phase 90 Summary: Frontend-Only and Virtual Node Support

**Completed:** 2026-09-25 (code, verified locally). **Pending:** the phone check (no device was connected).

## Delivered

**Plan 90.1: link resolution as the frontend does it** (`bcad6a4`)
- Reroute, PrimitiveNode, Note, MarkdownNote, SetNode and GetNode are frontend-only unless `/object_info` defines them. They are never sent and never reported as missing nodes.
- `resolveRealSource` carries the final target type:
  - Bypassed nodes use a port of `ExecutableNodeDTO._getBypassSlotIndex`: same index first, then an exact match; both types must fit, otherwise drop.
  - The same-index fallback that wired CLIP_VISION into CLIP_VISION_OUTPUT is gone.
- Frontend-only nodes pass through from the same-index input. Unknown pass-through nodes (UUID or contentless) then try the first type-compatible input.
- A widget input whose link doesn't resolve sends its saved widget value. `graphToPrompt` writes widget values first and replaces them only with links that resolve.
- 9 tests in `GraphToApiConverterVirtualNodeTest`.

**Plan 90.2: PrimitiveNode and SetNode/GetNode** (`82df6e7`)
- A widget input linked straight from a PrimitiveNode gets `widgets_values[0]` through the combo resolver. This applies whatever the primitive's mode (`applyToGraph` has no mode check).
  - The target's widget and control slots are still consumed.
  - A target reached through a Reroute keeps its own saved value, as in the frontend.
- A GetNode resolves through the first SetNode with the same name, in node order. With no setter, the link drops; duplicate names are logged.
- The validator and fixture survey now treat SetNode/GetNode as frontend-only.
- 11 tests.

**Plan 90.3: measurement and investigation** (`d6fc508`)
- `AllTemplatesReportTest` is an opt-in report (`COMFY_TEMPLATES_DIR`, `COMFY_OBJECT_INFO`), documented in the corpus MANIFEST.
- Found by the investigation: a **muted or bypassed subgraph instance is no longer expanded.** The frontend never runs its inner nodes, and bypasses the instance like any node. We had been sending those inner nodes as active, with their inputs missing.
- 3 tests.

## Where research changed the plan

- **D-04:** frontend-only nodes pass through by *index* (`getInputLink(slot)`), not by type search. The type search applies only to unknown phantom nodes.
- **D-10:** a GetNode uses the first SetNode in *node-array order*, not the lowest id.
- **New rule:** an unresolved link on a widget input sends the saved value. On its own this cleared the 3 PrimitiveNode corpus fixtures in 90.1, a plan earlier than planned. Plan 90.2 then made the primitive's own value win.
- **Not in the plan:** keeping muted/bypassed subgraph instances unexpanded. It came out of D-13 and was in scope (bypass/mute resolution).

## Results

- Corpus: `known-failures.json` 4 → **empty**. Full suite green: 174 tests, 23 of them new; the opt-in report is skipped.
- All templates: 572 graph workflows from `comfyui-workflow-templates-json` 0.1.95, against a full `/object_info` from stock ComfyUI v0.37.2 (`830232b`, `--cpu`, 962 node types, run locally in the scratchpad). The "before" run is at `dcd7ebe`.

| Metric | Before (= Phase 95) | After 90.1+90.2 | After 90.3 |
|---|---|---|---|
| Prompts passing every check | 542 | 560 | **563** |
| C7 frontend-only | 29 | 0 | **0** |
| C3 type mismatch | 4 | 0 | **0** |
| C4 missing inputs | 37 | 28 | **8** |
| C5 invalid values | 1 | 1 | 1 |
| C1 unknown nodes | 2 | 2 | 2 |

No template got worse; 21 templates now pass.

## Investigation (D-13)

| Template | Cause | Outcome |
|---|---|---|
| `video_wan2_2_14B_s2v` (23 → 0) | Interior nodes of bypassed `Video S2V Extend` instances (e.g. 174, 165, 172) were sent | Fixed (90.3) |
| `flux1_dev_uso_reference_image_gen` (4 → 0) | Bypassed `USO Style Reference` instances (113, and nested 55/56) | Fixed (90.3) |
| `image_qwen_image_instantx_inpainting_controlnet` (2 → 0) | Bypassed `Grow and Blur Mask` / `Scale image and mask` instances (220, 219) | Fixed (90.3) |
| `image_ernie_image`, `image_ernie_image_turbo` (2 each) | An unlinked subgraph input backed by a promoted widget (`width`/`height`) also feeds `PreviewAny.source`. The frontend sends the widget value to that socket; we only apply promoted values to widget inputs. | Subgraph I/O, not bypass/mute or virtual nodes: filed as **Phase 97** |

What remains is not a converter bug:
- 4 required inputs left unconnected in the templates themselves (`api_flux_vto`, `image_anima_lllite_image_inpainting`, `video_minimax_h3_i2v`, `video_wan_vace_inpainting`), as Phase 95 found;
- 2 C1 for a custom node (`AILab_QwenVL`);
- 1 C5 `ByteDanceSeedreamNodeV3.model.size_preset`, not investigated.

## Limits

- After flattening, SetNode/GetNode matching is global. KJNodes' per-subgraph scoping isn't reproduced, and there's no corpus fixture for it.
- PrimitiveNode text replacement ("Run widget replace on values") isn't supported; a log line is written and the raw value is sent.

## Verification

- `gradlew.bat testDebugUnitTest`: green (174 tests, 0 failures, 1 skipped (the opt-in report); 23 new in `GraphToApiConverterVirtualNodeTest`).
- `gradlew.bat assembleDebug`: succeeds.
- **Pending (user):** `installDebug`, then on the phone queue a workflow with a PrimitiveNode or a bypassed node or group. For example, `hidream_e1_1` or `video_wan2_2_14B_s2v` from the template browser, if the server has the models, or one of your own. It should queue without node errors.
