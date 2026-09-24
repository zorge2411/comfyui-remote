# Phase 86: Support COMFY_AUTOGROW_V3 Dynamic Input Type

## Goal Description

Copy autogrow slot links (`values.a`, `values.b`, ...) into the API JSON and stop autogrow keys from consuming widget values. Decisions and research: `86-CONTEXT.md`.

## Proposed Changes

**Modify** `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`, Mode B block, in the `for (key in allInputKeys)` loop (~line 261):

1. Add a local helper `isAutogrow(key)`: true when `(required?.get(key) ?: optional?.get(key))` is a JsonArray whose first element is the string `"COMFY_AUTOGROW_V3"`.
2. At the top of the loop body, if `isAutogrow(key)`:
   - iterate `graphInputs` in order; for each slot object whose `name` starts with `"$key."` and whose `link` is non-null, call `resolveRealSource(linkId)`; when resolved, add a `[sourceId.toString(), sourceSlot]` JsonArray to `inputs` under the full dotted name (same construction as the existing linked-slot branch);
   - then `continue` so the key never reaches `findNextCompatibleWidget`.
3. Leave all other branches untouched.

## Verification Plan

**Modify** `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterTest.kt` (or a new `GraphToApiConverterAutogrowTest.kt` in the same package, same fixture style) with synthetic nodes only:

1. `convert copies dotted autogrow slot links`: object_info with `MathNode` having `expression` STRING and `values` = `["COMFY_AUTOGROW_V3", {"template": {"input": {"required": {"value": ["FLOAT,INT,BOOLEAN", {}]}}, "names": ["a","b"], "min": 1}}]`; graph with two source nodes linked to slots `values.a` and `values.b`, widgets `["a + b"]`. Assert API inputs contain `values.a` = `[srcA, 0]`, `values.b` = `[srcB, 0]`, and `expression` = `"a + b"`.
2. `autogrow key does not consume a widget value`: node `FormatNode` with `values` (autogrow, declared first) then `f_string` STRING; widgets `["{a}"]`; one linked `values.a`. Assert `f_string` == `"{a}"` and no `values` key with a scalar.
3. `prefix-template variant works`: `images` autogrow with `prefix`/`max`, slots `images.image0`, `images.image1` linked; assert both copied.
4. `unlinked dotted slot is omitted`: `values.b` link null; assert only `values.a` present.

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

Optional live proof (not committed): convert the MiniMax H3 workflow with the real `/object_info` and POST to `/prompt`; confirm the `required_input_missing` for `values.a` is gone (other validation errors from unrelated combo/`bit_depth` fields may remain).

## Success Criteria

- [ ] Dotted autogrow slot links are present in the converted API JSON for both template variants
- [ ] `f_string`-style widget after an autogrow key keeps its own value
- [ ] New tests and the full `testDebugUnitTest` suite pass; `assembleDebug` succeeds
