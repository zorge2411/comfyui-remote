# Phase 102: Custom Widget Kinds and PrimitiveNode Text Replace - Context

**Gathered:** 2026-10-12
**Status:** Ready for execution

<domain>
## Phase Boundary

Roadmap said "driven by templates that use them". Research against the 572 official templates and the full v0.37.2 `/object_info` shows what is real and what isn't, so this phase is narrowed:

- **In:** (A) PrimitiveNode "Run widget replace on values" text replacement in the converter; (B) a colour field for COLOR widgets and a four-number field set for BOUNDING_BOX widgets in the form; (C) tests that pin the converter's handling of COLOR / BOUNDING_BOX / CURVE / VIDEO_EDIT values.
- **Out, with evidence:** CURVE and VIDEO_EDIT editing (1 and 0 templates; they need graphical editors, and their values already pass through unchanged), legacy Group Nodes (0 templates, no `groupNodes` or `workflow>` types anywhere).

</domain>

<research>
## Findings

**Custom widget kinds in the full `/object_info`:** COLOR on 7 node types, BOUNDING_BOX on 6, VIDEO_EDIT on 2 (`VideoTrim`, `VideoCrop`), CURVE on 1 (`CurveEditor`), WEBCAM on 1. All are declared `socketless: true`; some BOUNDING_BOX inputs are also `forceInput` (they are sockets, e.g. `SDPoseKeypointExtractor.bboxes`).

**Templates that use them (572 scanned):** COLOR: `ImageCropToMask` (2), `Painter` (5), `ColorToRGBInt` (2), `RenderSplat` (1). BOUNDING_BOX widget: `ImageCropV2` (1); the others are linked sockets. CURVE: `CurveEditor` (1). VIDEO_EDIT / WEBCAM: none.

**The converter already handles them.** I converted every template that uses these nodes and read the resulting inputs: widget slots stay aligned (the `else -> true` branch of `isCompatible` consumes them) and the saved values come through as the frontend stores them: COLOR `"#000000"`, BOUNDING_BOX `{"x":0,"y":0,"width":1669,"height":2688}`, CURVE `{"points":[[0,0],…],"interpolation":"monotone_cubic"}`. So nothing breaks at conversion; what is missing is the form: COLOR shows as a plain text field, and BOUNDING_BOX / CURVE objects are skipped by `WorkflowParser` (non-primitive → treated as a link), so they can't be edited.

**PrimitiveNode replace.** `Run widget replace on values` (widgetInputs.ts) is false in all 30 PrimitiveNodes of the templates, so no template needs it; it matters for user-made workflows. Frontend semantics (`searchAndReplace.ts`): in the primitive's string value, each `%...%` is replaced: `%date:<format>%` with the formatted current date; `%Name.widget%` with that widget's value on the first node whose property `Node name for S&R` equals Name (else whose title does), with `/ ? < > \ : * | "` and control characters in the value replaced by `_`; unknown patterns (including `%width%` / `%height%`, which SaveImage's server-side formatting handles) are left as they are. The converter today logs "text replacement is not applied".

**Server side:** nothing validates COLOR / BOUNDING_BOX values (`validate_inputs` only checks the types it knows), so a bad value only fails inside the node. The form therefore validates the format itself.
</research>

<decisions>
## Decisions

- **D-01** Converter: apply the replacement when the PrimitiveNode's property is true and the value is a non-empty string. `%date:fmt%` uses the frontend's format tokens (verify against `formatDate` in the frontend source at execution; inject a clock so tests are deterministic). `%Name.widget%` finds nodes across the main graph and subgraph definitions by S&R property, then title, and reads the widget's saved value (by `widgets_values_named`, else by the widget order from `/object_info`). Unresolvable patterns stay as they are.
- **D-02** `InputField.ColorInput` (value `#rrggbb`, also accepts `#rgb`): a swatch, a hex text field and validation; written back by the executor. Parser: kind COLOR on a primitive string.
- **D-03** `InputField.BoundingBoxInput` (x, y, width, height as ints): four number fields; the executor writes the object. Parser: kind BOUNDING_BOX on a JSON object value that has those four keys (linked/forced sockets stay links and are not shown).
- **D-04** Queue snapshots: both new types get labels and branches in `InputFieldDeserializer` and `WorkflowExecutor`; a test round-trips them through the same Gson setup the queue uses.
- **D-05** CURVE and VIDEO_EDIT values are preserved untouched (tests in D-06); no editor.
- **D-06** Tests: converter alignment with synthetic specs for COLOR / BOUNDING_BOX / CURVE / VIDEO_EDIT widgets between ordinary widgets (values and following widgets stay aligned); replacement cases (date, node widget by S&R and by title, sanitised value, unknown pattern, disabled property, non-string); parser / executor round-trips; hex validation.
</decisions>
