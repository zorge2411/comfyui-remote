# Phase 87: Fix Widget Value Mapping for MiniMax H3 Workflow - Context

**Gathered:** 2026-09-24 (auto mode: decisions made from research)
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix the two server-validation failures seen in Phase 81 UAT when converting a graph workflow with `GraphToApiConverter` (Mode B). Scope: converter widget-value mapping only.

</domain>

<research>
## Findings (real workflow "Minimax h3 easy i2v.json" from the user's server + live `/object_info`; not committed)

Note: the failing workflow is **not** `video_minimax_h3_i2v.json` (that one has no `MiniMaxH3Easy` node); it is "Minimax h3 easy i2v.json", node 2 `MiniMaxH3Easy` and node 11 `CreateVideo`.

**Bug A, `CreateVideo.bit_depth` = 24 (node 11):** graph inputs are `images*, audio*, fps*(w), bit_depth(w)` and `widgets_values = [24, 8]`. `fps` is a widget that was converted to an input and is linked; the frontend still stores its value (24) in `widgets_values`. The converter's linked-slot branch (`slotNames.contains(key)`) resolves the link but never advances `widgetIndex`, so `bit_depth` takes 24 instead of 8. General rule: a graph input slot carrying a `widget` property occupies one `widgets_values` position even when linked. Affects any node with a linked widget followed by more widgets.

**Bug B, five combos receive display labels (node 2):** the ComfyUI-MiniMaxH3-Easy custom frontend extension (`minimax_h3_easy_ui.js`, served at `/extensions/...`) localizes combo widgets and stores the *label* in `widgets_values` (en/zh), with a label-to-value map living only in that JS (e.g. "First frame priority" to `first`, "By filename" to `filename`, "1K area (~1MP)" to `1k`, "General only" to `none`, "I2V or First/Last Frame" to `image`). `/object_info` carries no labels (only `[["image","reference"], {"default":"image"}]`). Widget alignment for this node is otherwise correct (an extra `null` widget is skipped by `isCompatible`).
</research>

<decisions>
## Implementation Decisions

- **D-01 (Bug A):** in the Mode B loop, when a slot is linked and the graph slot has a `widget` property, consume one `widgets_values` entry using the existing `findNextCompatibleWidget` skipping rules, so frontend-only widgets stay handled.
- **D-02 (Bug B):** do not parse third-party extension JS. When a COMBO widget value is a string not present in the `object_info` option list, resolve it in order: (1) case-insensitive exact match; (2) the longest option that appears as a substring of the label lowercased with non-alphanumerics removed (must be unique at that length); (3) the input's `default` from its config if it is in the options; (4) otherwise keep the value (server will report it). Values already valid are never touched.
- **D-03:** log resolved substitutions via the existing `println("CONVERT_DEBUG: ...")` style; no UI change.
- Out of scope: Mode A / no-metadata paths; other custom-widget quirks; live-server auto-fetch of extension mappings.

### Claude's Discretion
Helper names/placement; exact normalization details.
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` Mode B (`findNextCompatibleWidget`, linked-slot branch, Phase 86 autogrow block)
- `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterAutogrowTest.kt` (fixture style to copy)
- `.gsd/phases/81/81-UAT.md` (original error output)
</canonical_refs>

<deferred>
- Parsing extension label maps generically; surfacing "value auto-corrected" warnings in the UI.
</deferred>
