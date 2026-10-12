# Phase 93 Summary: Map Subgraph Instance Inputs by Name

**Completed:** 2026-09-25
**Status:** ✅ Done. The Android Gradle run and the optional device check are pending on the user's machine.

## Delivered

**Plan 93.1: input mapping** (commit `13bf196`)
- `GraphToApiConverter.mapInstanceInputs()` binds each instance input to a subgraph input, first by name + type, then by name, using each subgraph input once. This mirrors the frontend's `_rebindInputSubgraphSlots`.
- `expandGraphOnce` uses the mapping in the External→Wrapper and Wrapper→Wrapper branches.
- Definitions that declare no `inputs` keep positional slots. Older synthetic graphs rely on this.
- 5 tests: subset, reordered, duplicate names split by type, unknown name ignored, wrapper→wrapper.

**Plan 93.2: promoted widget values**
- `GraphToApiConverter.promotedWidgetOverrides()` follows the frontend (`_setWidget`, `_applyPromotedWidgetValues`, `ExecutableNodeDTO.resolveInput`):
  - A subgraph input is promoted when any of its interior targets has a widget.
  - Instance `widgets_values` entries are assigned to promoted inputs by position.
  - `proxyWidgetErrorQuarantine` host values take priority.
  - A value is skipped when an external link feeds the input.
  - The value is recorded on every interior target as a `__promoted_widgets` graph-node property. It is never sent to the API.
- In Mode B, the promoted value replaces the widget value, and the `widgets_values` slot is still consumed. It also covers `LoadImage`'s image.
- Nested subgraphs work: an inner instance's promoted value comes from the outer instance first.
- 7 synthetic tests, plus `PromotedWidgetCorpusTest`, which checks that the real `image_mage_flow_t2i_int8` sends the instance's prompt and seed.

## Corpus

- `image_boogu_image_0_1_turbo_t2i` and `image_mage_flow_t2i_int8` now pass.
- `image_qwen_Image_2512_controlnet` and `video_ltx2_depth_to_video` are narrowed to C5 (Phase 94).
- Known failures: 12 → 10. No entry cites Phase 93.
- Validator fix: an empty autogrow group whose template has `min: 0` (e.g. `TextEncodeMageFlowEdit.images`) is no longer reported as a missing input. Added 1 test.

## Verification

- Plain-JVM suite: 51/51 pass (converter, validator, corpus, and the promoted-value fixture test).
- Against the pre-93.2 converter, 6 of the 7 new synthetic tests and the fixture test fail, so they catch the bug. The seventh (empty instance values keep interior values) passes on both by design.
- Smoke test: all 572 official templates convert without an exception (scratch test, not committed).
- Pending (user):
  - `gradlew.bat testDebugUnitTest` and `gradlew.bat assembleDebug`.
  - Optional on device: a template whose instance and interior values differ (e.g. `video_minimax_h3_t2v`) should show the instance's prompt in the app form.
