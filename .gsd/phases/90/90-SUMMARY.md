# Phase 90 Summary: Frontend-Only and Virtual Node Support

**Completed:** 2026-09-26. Verified locally and on the phone.

## Delivered

**Plan 90.1: bypass rule and subgraph instance modes** (`9f9c12a`)
- Bypass follows the frontend's `_getBypassSlotIndex`. The consuming input's type is carried through chains, and the input chosen is:
  1. the same-slot input, if it fits both types;
  2. otherwise the exact target-type match;
  3. otherwise the first compatible input;
  4. otherwise nothing, and the link is dropped.
- Added `typesCompatible` (`*`, overlapping comma lists).
- Muted and bypassed subgraph instances are no longer expanded. They stay as ordinary muted/bypassed nodes with the full subgraph input and output lists, so nothing inside runs and bypass passes their inputs through.
- Muted and bypassed nodes are never reported as missing.
- 8 tests in `GraphToApiConverterBypassRuleTest`. 4 of them fail on the pre-90 converter; the other 4 guard cases where the old and new rules agree.

**Plan 90.2: virtual nodes** (this commit)
- Reroute, PrimitiveNode, SetNode, GetNode, Note and MarkdownNote are recognised by type. They are never sent and never reported as missing.
- `resolveRealSource` now returns `Link`, `Literal` or dropped:
  - **PrimitiveNode** gives a literal, which the target receives through its combo resolution. The target's widget slot is still consumed and its control slots skipped. "Run widget replace on values" is logged as not applied.
  - **GetNode** follows the SetNode with the same `widgets_values[0]`, first match in the flattened graph.
  - **Other virtual nodes** pass through the input at the same slot; with nothing to pass, the link is dropped.
- Phase 93 follow-up: when a promoted subgraph input has no saved value, interior socket inputs take the interior widget's emitted value (`__promoted_from` references, filled in after all nodes are emitted).
- 9 tests in `GraphToApiConverterVirtualNodeTest`. All 9 fail on the pre-90 converter.

## Results

- **Corpus: `known-failures.json` is empty.** All 32 fixtures pass every check. This completes the Milestone 4 must-have.
- All 572 official templates against v0.37.2 `/object_info` (scratch run):

| Metric | Before 94 | After 94 | After 95 | After 90 |
|---|---|---|---|---|
| Prompts passing every check | 123 | 426 | 542 | **565** |
| C3 | 4 | 4 | 4 | **0** |
| C4 | 1595 | 109 | 37 | **4** |
| C5 | 324 | 162 | 1 | 1 |
| C7 | 29 | 29 | 29 | **0** |

- The remaining 7 are not converter bugs:
  - 2 × C1: `AILab_QwenVL`, a custom node the stock server lacks.
  - 4 × C4: required inputs left unconnected in the template itself (`api_flux_vto`, `image_anima_lllite_image_inpainting`, `video_minimax_h3_i2v`, `video_wan_vace_inpainting`). ComfyUI rejects these too.
  - 1 × C5: `api_bytedance_seedream_5_0_lite_image_edit` saved a size preset the node no longer offers.

## Verification

- Plain-JVM suite passes, including your converter tests from the `master` merge.
- Pending (user): `gradlew.bat testDebugUnitTest`, `assembleDebug`, `installDebug`. Then on the phone:
  - a template with a PrimitiveNode, e.g. "SDXL Simple" (`sdxl_simple_example`) or any ACE-Step audio template;
  - a template with bypassed groups, e.g. `video_wan2_2_14B_s2v`, if your server has the models;
  - your MiniMax H3 workflow as a regression check.

## Device check (2026-09-26)

Passed on the Fairphone 6 over wireless adb. Build from `master` `a9c45ec` (the agent's branch plus the local-master merge): `testDebugUnitTest` 185/185, `assembleDebug` and `installDebug` OK. The database upgraded from 10 to 11 in place, with no data loss (`user_version` 11).

- A template with a PrimitiveNode and the MiniMax H3 workflow both ran.
