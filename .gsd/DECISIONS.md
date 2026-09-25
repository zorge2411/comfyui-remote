## Phase 35 Decisions

**Date:** 2026-01-24

### Scope

- **Multi-select Delete**: Implement long-press selection in Grid View for batch deletion.
- **Permissions**: Improve handling of revoked permissions (prompt user effectively).
- **UX**: Add Shared Element Transitions between Grid and Detail views.

### Approach

- **Selection**: Long-press triggers selection mode; TopAppBar changes to Context Bar.
- **Storage**: Add specific "Save Successful" snackbar. Add "Reset Folder" option in Settings.
- **Animations**: Use Compose Shared Element Transitions.

### Dependencies

- **Settings**: Add "Reset" action next to "Change Folder".

## Phase 90 Decisions

**Date:** 2026-09-25 (`--auto`: recommended options picked; full detail in `.gsd/phases/90/90-CONTEXT.md`)

### Scope

- Handle the frontend-only nodes Reroute, PrimitiveNode, Note, MarkdownNote, SetNode and GetNode. A type counts as frontend-only only when `/object_info` doesn't define it. None of these reach the server, and nodes the converter handles aren't reported as missing.
- Investigate the links with no source in `video_wan2_2_14B_s2v`, `flux1_dev_uso_reference_image_gen`, `image_ernie_image(_turbo)` and `image_qwen_image_instantx_inpainting_controlnet`. Fix them here if the cause is bypass, mute or a frontend-only node; otherwise file a new phase.
- Out of scope: the pre-flight UI (Phase 91), cg-use-everywhere and rgthree virtual nodes, and app-side randomisation.

### Approach

- Pass-through nodes: use the input whose type matches (`*` counts as a match). Otherwise use the only input if there is just one; otherwise drop the link.
- PrimitiveNode: each target input gets the primitive's `widgets_values[0]` as a value. The target's widget slot is still consumed, with Phase 95 control skipping.
- SetNode/GetNode: matched by name (`widgets_values[0]`) on the expanded graph. If names are duplicated, the lowest id wins; if no SetNode has the name, the link is dropped.
- Bypassed node: try the same-index input first, then the others, and use an input only if its type matches the output type. If none matches, drop the link; the same-index fallback is gone.
- Reason: match the ComfyUI frontend (`widgetInputs.ts`, `ExecutableNodeDTO.ts`, KJNodes `setgetnodes.js`), as in Phases 93–95.

### Constraints

- `known-failures.json` must end up empty. Across all 572 templates, C7 goes 29 → 0 and C3 goes 4 → 0. Record the before/after numbers in `90-SUMMARY.md`.
- Research must check two points against the frontend: whether a muted or bypassed primitive still applies its value, and the order in which a bypassed node's inputs are searched.
