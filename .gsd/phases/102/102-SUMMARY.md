# Phase 102 Summary: Custom Widget Kinds and PrimitiveNode Text Replace

**Status**: Code done; device check pending.

## Delivered

- **Text replacement** (`domain/TextReplacement.kt` + converter): PrimitiveNode "Run widget replace on values" now applies the frontend's rules to its string value: `%date:fmt%` (d M h m s y tokens, padded to the token length, 24-hour clock) and `%Name.widget%` (node by `Node name for S&R`, then title; value with `/ ? < > \ : * | "` and control characters replaced by `_`); unknown patterns, `%width%` / `%height%` are left as they are. The converter does a second pass only when some PrimitiveNode has the property on and a `%` in its text (widget values come from the first pass), so no other workflow is affected.
- **Pinned widget kinds**: tests show COLOR, BOUNDING_BOX, CURVE and VIDEO_EDIT values pass through the converter as saved, neighbouring widgets stay aligned, and a linked forceInput box consumes no slot.
- **Form fields**: `ColorInput` (swatch + hex field, red with a hint when invalid; Generate and Queue are blocked while a colour is invalid; `#rgb` is expanded to `#rrggbb`) and `BoundingBoxInput` (x / y / width / height number fields). Parser needs the node list for both. Executor writes them; queue snapshots deserialize both labels. CURVE / VIDEO_EDIT values are untouched and not editable.
- Tests: 8 text-replacement, 3 converter widget-kind, 5 colour/box tests; whole JVM suite green.

## Known limits (stated honestly)
- **Dates freeze at import.** The app converts a workflow once, when it is imported, so `%date:…%` in a replace-enabled primitive shows the import day, not the run day (the frontend replaces at queue time). Fixing it needs a run-time pass; no template uses the feature.
- Only top-level graph nodes are searched for `%Name.widget%` (not nodes inside subgraphs), and their widget values come from the converted prompt.
- The frontend's `formatDate` source wasn't in the local frontend checkout, so the token rules (`yy` as two digits, 24-hour `h`) are from memory of that function and the server's equivalent, not verified against the source.
- Dropped, with evidence (see 102-CONTEXT): CURVE / VIDEO_EDIT editors and legacy Group Nodes.

## Device check
1. A Painter template (`api_bria_eraser`): background colour is a swatch + hex field; change and generate; an invalid hex shows red and disables Generate / Queue.
2. `3d_pixal3d_multi_views` (ImageCropV2): x / y / width / height fields appear; edit and generate.
3. `basic_image_color_adjustment` (CurveEditor): opens and runs unchanged.
4. Hand-made workflow: PrimitiveNode with replace on and `%date:yyyy-MM-dd%` in a text feeding a prompt: the server receives the import day's date.
