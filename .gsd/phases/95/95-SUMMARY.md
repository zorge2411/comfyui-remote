# Phase 95 Summary: Widget Mapping Gaps for V3 Nodes

**Completed:** 2026-09-25 (code). **Pending:** the Android Gradle run on the user's machine.

## Delivered

**Plan 95.1: control widgets and socket slots** (`6205a97`)
- Control values after seed-like inputs are skipped using the frontend's rules:
  - INT: 1 slot when `control_after_generate` is set, or when it's absent and the input is named `seed`/`noise_seed`;
  - FLOAT: 1 slot;
  - COMBO: 2 slots, the control plus its filter list.
- A slot is skipped only if it really holds a control value, because older saves may lack it.
- Unlinked socket inputs no longer take widget values at top level (Phase 94 already covered sub-inputs).
- 8 tests in `GraphToApiConverterControlWidgetTest`.

**Plan 95.2: named values, defaults, special kinds** (this commit)
- **Named values:** `widgets_values_named` is used when present. A widget missing from the map was added after saving and gets its default. That last rule was refined during execution: the original plan fell back to the positional value, which a test showed can be shifted.
- **Defaults:** a widget with no saved value gets the frontend default instead of being left out:

| Type | Default |
|---|---|
| INT / FLOAT | `spec.default`, else 0 |
| STRING | `spec.default`, else `""` |
| BOOLEAN | `spec.default`, else false |
| COMBO | `spec.default`, else the first option |

- Found during execution, from the frontend source:
  - `forceInput`/`defaultInput` inputs are sockets (22 inputs on stock ComfyUI);
  - a spec `widgetType` sets the widget kind (e.g. `FLOAT,INT` rendered as FLOAT);
  - `IMAGECOMPARE` is display-only: not in `widgets_values`, sent as `["", ""]` (43 template uses of `ImageCompare.compare_view`);
  - hidden widgets (e.g. SaveVideo's legacy `codec`) do exist and are saved, so they correctly get defaults too. The Phase 94 test was updated to match.
- Validator: 3D asset file values (`glb`, `fbx`, …) are server files and aren't checked.
- 7 tests.

Against the pre-95 converter, 11 of the 15 new tests fail. The other 4 are guard tests: older saves, explicit `false`, `steps` not skipped, and the combo filter list.

## Results

- Corpus known failures: 7 → **4**, all Phase 90 (PrimitiveNode ×3, bypass fallback ×1).
- All 572 official templates against v0.37.2 `/object_info` (scratch run):

| Metric | Before Phase 94 | After 94 | After 95 |
|---|---|---|---|
| Prompts passing every check | 123 | 426 | **542** |
| C5 invalid values | 324 | 162 | **1** |
| C4 missing inputs | 1595 | 109 | **37** |
| C7 frontend-only | 29 | 29 | 29 (Phase 90) |
| C3 type mismatch | 4 | 4 | 4 (Phase 90) |
| C1 unknown nodes | 2 | 2 | 2 (custom nodes, not a bug) |

## What's left: candidates after Phase 90

The remaining 37 C4 are in 9 templates:
- **Required inputs left unconnected in the template itself:** `api_flux_vto`, `image_anima_lllite_image_inpainting`, `video_minimax_h3_i2v`, one input in `video_wan_vace_inpainting`. ComfyUI would reject these too; not a converter bug.
- **Links whose source doesn't resolve,** mostly around bypassed or muted groups: `video_wan2_2_14B_s2v` (23), `flux1_dev_uso_reference_image_gen` (4), `image_ernie_image(_turbo)` (`PreviewAny.source`), `image_qwen_image_instantx_inpainting_controlnet`. Look at these during Phase 90 (bypass/mute/virtual-node resolution) before adding a new phase.
- **1 C5:** `ByteDanceSeedreamNodeV3.model.size_preset`, not investigated.
- **Custom widget kinds** (COLOR, BOUNDING_BOX, VIDEO_EDIT, CURVE, …) keep the old positional behaviour. No template failure was traced to them, but their save rules differ per widget.

## Verification

- Plain-JVM suite: 89/89 pass.
- Pending (user): `gradlew.bat testDebugUnitTest`, `assembleDebug`, `installDebug`. On the phone, a Gemini, Grok or SaveVideo workflow should run with the values shown in ComfyUI.
