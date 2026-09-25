# Phase 89 Summary: Workflow Compatibility Regression Corpus

**Completed:** 2026-09-25
**Status:** ✅ Done. The Android Gradle run is still pending on the user's machine (see Verification).

## Delivered

**Plan 89.1: fixtures and object_info**
- `scripts/corpus/select_fixtures.py`: reports each workflow's features and any node types missing from object_info.
- `scripts/corpus/export_object_info.py`: exports `/object_info`, trimmed to the fixtures' node types, with file-name combo lists emptied. It preserves input order. It can fetch from a server (`--url`) or read a saved dump (`--input`).
- `app/src/test/resources/workflow-corpus/`:
  - 30 fixtures from `comfyui-workflow-templates-json` 0.1.95, 0.8 MB in total.
  - `object_info.json` from stock ComfyUI v0.37.2, 130 node types.
  - `MANIFEST.md` and the MIT license file.
- Every coverage target was met. There are no user workflows and no model file names in the repo.

**Plan 89.2: harness**
- `ApiPromptValidator` (test source set): checks C1–C7. It understands V3 inputs: `COMBO` with options, `COMFY_AUTOGROW_V3` and `COMFY_DYNAMICCOMBO_V3` dotted keys, and `COMFY_MATCHTYPE_V3`. It skips file-name combo values. 13 unit tests.
- `WorkflowCorpusTest`: runs all fixtures and reports every problem at once. `known-failures.json` is enforced in both directions.

## Baseline: 18 of 30 fixtures pass, 12 are known failures

The failures are real converter gaps, grouped into these phases:

| Phase | Gap | Fixtures |
|---|---|---|
| 90 | PrimitiveNode sent to the server; bypass fallback wires a mismatched input when no input matches the output type | 4 |
| 93 (new) | Subgraph instance inputs mapped by position, not name | 4 |
| 94 (new) | `COMFY_DYNAMICCOMBO_V3` sub-widgets not expanded | 4 |
| 95 (new) | `control_after_generate` value consumed by a V3 COMBO; no default for inputs newer than the workflow | 4 |

Some fixtures appear under more than one phase. The Reroute fixtures pass: the existing phantom-node flattening handles them in this corpus.

## Deviations

- **No Android SDK in the cloud session** (dl.google.com is blocked). Tests were verified with a plain-JVM Gradle project that compiles the pure-Kotlin converter sources (`GraphToApiConverter.kt`, `SubgraphDefinition.kt`, `ObjectInfoDTOs.kt`) and all converter and corpus tests. `testDebugUnitTest` and `assembleDebug` still need a run on the user's machine.
- **Bug in my first export:** it saved object_info with sorted keys, which shuffled widget mapping for every node (KSampler got `sampler_name = "7"`). The converter depends on the server's input order, so the export now preserves it, and MANIFEST.md warns about this.
- **Validator adjustments from the baseline run:**
  - File-name combo values are skipped, because the stock server's model lists are empty; for example, VAELoader lists only `pixel_space`.
  - `COMFY_MATCHTYPE_V3` outputs are treated as wildcards.
  Both have unit tests.

## Verification

- JVM run: 37/37 tests pass. That is 23 existing converter tests, 13 validator tests, and the corpus test.
- Harness drift checks:
  - Removing the `utility_image_stitch.json` entry gives a REGRESSION failure.
  - A bogus entry for `flux_schnell.json` gives a STALE failure.
  - Both were reverted.
- `git grep` found no `.safetensors`/`.ckpt` names in `object_info.json`.
- Pending (user): `gradlew.bat testDebugUnitTest` and `gradlew.bat assembleDebug`.
