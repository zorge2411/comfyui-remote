# Phase 87: Fix Widget Value Mapping for MiniMax H3 Workflow

## Goal Description

Fix widget position drift for linked widget-inputs (Bug A) and map custom-frontend combo labels to valid API values (Bug B). See `87-CONTEXT.md`.

## Proposed Changes

**Modify** `GraphToApiConverter.kt`, Mode B block:

1. **Linked widget consumes its value.** In the `slotNames.contains(key)` branch, after the link is added (or fails to resolve), if the graph slot object has a `widget` member and `graphWidgets != null`, call `findNextCompatibleWidget(key)` and discard the result. Non-widget linked slots such as `images` are unaffected.
2. **Combo resolver.** Add `resolveComboValue(key, value)`: read the option list from `required/optional[key][0]` when it is a JsonArray; if `value` is a string not in the list, apply D-02 steps 1-4 (default read from `def[1].default`). Route every value from `findNextCompatibleWidget` through it before `inputs.add(key, ...)`, in both the slot-unlinked and non-slot branches. Log substitutions with `println("CONVERT_DEBUG: ...")`.

## Verification Plan

**Create** `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterWidgetMappingTest.kt` (synthetic data, same fixture style as `GraphToApiConverterAutogrowTest`):

1. `linked widget input still consumes its widgets_values slot`: required `fps` FLOAT, then optional `bit_depth` INT; graph slot `fps` linked with `"widget": {"name": "fps"}` and `widgets_values [24, 8]`; assert `bit_depth` is 8 and `fps` is the link.
2. `non-widget linked slot does not consume a widget value`: `images` linked (no `widget` key); the next widget keeps `widgets_values[0]`.
3. `combo label resolves by substring`: options `["filename","index"]` with "By filename" gives `filename`; `["first","last"]` with "First frame priority" gives `first`; `["match","1k","1.5k"]` with "1K area (~1MP)" gives `1k`.
4. `combo label falls back to default`: options `["image","reference"]`, default `image`, value "I2V or First/Last Frame" gives `image`.
5. `valid combo value untouched` and `unresolvable label with no default is kept`.

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

Live proof (throwaway, not committed): with the scratch copy of "Minimax h3 easy i2v.json" and the saved `/object_info`, run `convert()`, POST to `/prompt`, and confirm the node 2 combo errors and the `bit_depth` error are gone. Delete scratch afterward; the prompt text is sensitive.

## Success Criteria

- [ ] `bit_depth` receives 8 (not 24) for the real workflow's `CreateVideo`
- [ ] Node 2's five combo fields convert to valid API values
- [ ] New tests and the full suite pass; `assembleDebug` succeeds
- [ ] Live `/prompt` no longer reports these two errors; no sensitive content committed
