# Phase 94 Summary: Support COMFY_DYNAMICCOMBO_V3 Inputs

**Completed:** 2026-09-25 (code); device check passed 2026-09-26.

## Delivered

**Plan 94.1: corpus can see sub-inputs** (`2b1598d`)
- `ApiPromptValidator` resolves dynamic combos through the selected option, recursively, so C3, C4 and C5 now cover dotted sub-inputs. 4 new validator tests.
- New fixtures: `api_google_gemini`, `api_openai_gpt_image_2_image_edit`. The object_info snapshot now has 132 node types; no existing definition changed.
- The re-baseline showed that SaveVideo never sends `format.codec`, among others. Known failures went 7 → 26 (18 fixtures cite Phase 94).

**Plan 94.2: converter** (`da577e1`)
- Mode B now emits every input through one recursive `emitInput()`:
  - dynamic combos expand the selected option's required and then optional inputs as `key.sub`, depth-first;
  - linked sub-inputs and autogrow sub-inputs are copied;
  - socket sub-inputs consume no widget value;
  - linked widget sub-inputs still consume one (Phase 87 rule);
  - Phase 93 promoted values apply by dotted path.
- Combo label resolution (Phase 87) also reads V3 `COMBO` and dynamic option lists.
- 7 tests in `GraphToApiConverterDynamicComboTest`. Against the old converter, 6 of them fail; the unknown-option test passes on both because the old output happens to match.

**Plan 94.3: form** (this commit)
- `WorkflowParser` looks up specs through the selected options for dotted keys and reads V3 `COMBO` options, so these become dropdowns.
- The dynamic combo key is left out of the form. `WorkflowExecutor` only patches listed fields, so the value is still sent.
- `InputField.displayName` shows dotted names as `format › codec`. It is a computed extension, so the Room/Gson storage shape doesn't change.
- 5 parser tests.

## Results

- Corpus known failures: 26 → **7**. None cite Phase 94. What remains: Phase 90 (PrimitiveNode, bypass fallback) and Phase 95 (`control_after_generate`, defaults).
- Across **all 572 official templates**, with the full v0.37.2 `/object_info` (scratch run, not committed):

| | Before | After |
|---|---|---|
| Prompts passing every check | 123 | **426** |
| C4 violations | 1595 | 109 |
| C5 violations | 324 | 162 |
| Converter crashes | 0 | 0 |

  Most of the remaining C4/C5 look like Phase 95 material (seed control values, inputs added after the template was saved).

## Verification

- Plain-JVM suite: 74/74 pass (converter, parser, validator and corpus tests).
- `DynamicFormScreen.kt` only switched its labels to `displayName`. It isn't compiled here (no Android SDK) and is covered by the local `assembleDebug`.
- Pending (user):
  - `gradlew.bat testDebugUnitTest` and `gradlew.bat assembleDebug`, then `installDebug`.
  - **Device check (Plan 94.3):** import a workflow with SaveVideo, or `utility_image_stitch` (ResizeImageMaskNode). Confirm that the format / resize-type field isn't shown, that sub-fields appear as dropdowns or numbers labelled like `format › codec`, and that generation succeeds on your server.

## Notes

- A server older than the one a workflow was saved with may define a node without the dynamic combo. The converter follows whatever `/object_info` the connected server returns, so it adapts either way.
- Switching a dynamic option in the app isn't supported: the key is hidden, not editable. That would be a separate phase if wanted.

## Device Check (2026-09-26)

✅ **Passed.** On the user's phone, the official "Image Stitch 2x2 Grid" template (`utility_image_stitch`, ResizeImageMaskNode with the `resize_type` dynamic combo) ran end to end, which confirms Plans 94.2 and 94.3 on device. The app was built and installed locally for this run, so the Android build of Phases 89–96 compiled.
