# JOURNAL.md

## 2026-02-05: Phase 74 Completion

## 2026-02-05: Phase 74 Completion - Hotfix

### Queue Crash Prevention

Addressed a reported crash when adding items to the queue.

- **Issue**: Unhandled exception during queue addition. Root cause: Gson `IllegalArgumentException` due to duplicate `fieldName` in `InputField` hierarchy.
- **Fix**: Wrapped `MainViewModel.addToQueue` in `try-catch` (Hotfix 1). Refactored `InputField` class to use abstract properties instead of constructor overrides, resolving the serialization conflict (Hotfix 2).
- **Outcome**: Queue addition is now functional and verified with unit tests.

### Local Queue & Robust Graph Conversion

Implemented a persistent local queue and significant improvements to graph-to-API conversion.

- **Decisions**:
  - **Local Queue**: Used Room with custom `Converters` for enums. Implemented `ExecutionService` as a standard class (removed incompatible DI) to manage persistent queue state.
  - **Graph Conversion**: Implemented "Smart Skipping" and "Flattening". Nodes with missing metadata are bypassed if they have inputs, or skipped if they are dead-ends without output links. This prevents "Missing Node" errors on the server while preserving functional producers.
  - **Deserialization**: Used custom `Gson` deserializer for polymorphic `InputField` list serialization in the local database.
- **Outcome**: Successful build and 24/24 unit tests passing, covering complex subgraph and fallback scenarios.

## 2026-01-30: Todo Added

- Added todo: link from resulting image to gallery

- Added todo: copy all text button in prompt text box
- Added todo: support for image to image workflow nodes
- Added todo: positive prompt text input must be the most top one in workflow

## 2026-09-25: Milestone 3 Complete, Milestone 4 Created

- Closed Milestone 3 (Phases 29–88). Archived phase folders and the full roadmap to `.gsd/milestones/Milestone 3/`; summary in `Milestone 3-SUMMARY.md`.
- Created Milestone 4 — Workflow compatibility: regression corpus (89), frontend-only/virtual nodes (90), pre-flight check against `/object_info` (91), full `node_errors` reporting (92).

## 2026-09-25: Phase 89 Complete

- Built the workflow compatibility corpus from the official ComfyUI templates (MIT), plus an `/object_info` snapshot from stock ComfyUI v0.37.2 running headless on CPU in the cloud session.
- Lesson: `/object_info` input order is load-bearing. The converter maps `widgets_values` by that order, so a snapshot saved with sorted keys breaks every node.
- The baseline exposed 4 converter gap groups in real templates, filed as Phases 90 and 93–95.

## 2026-09-25: Phase 93 Complete

- Subgraph instance inputs now bind by name+type, then name, matching the ComfyUI frontend. Promoted widget values on instances now reach interior nodes.
- Lesson: the frontend source (`ComfyUI_frontend/src/lib/litegraph/src/subgraph/`) is the reference for graph semantics, and reading it turned up the silent wrong-values bug (Bug B) that the structural corpus checks couldn't see.

## 2026-09-25: Phase 94 Complete (code)

- Dynamic combos are expanded into dotted sub-inputs, and the form handles V3 combos. All-template clean prompts went 123 → 426 of 572.
- Lesson: extend the checker before fixing. The first corpus baseline hid 14 more fixtures that also had this bug.

## 2026-09-25: Phase 95 Complete (code)

- Widget mapping now follows the frontend's save rules: control widgets, sockets and forceInput, named values, defaults, and display-only widgets. All-template clean prompts: 426 → 542 of 572.
- Lesson: a `widgets_values_named` map is complete for the widgets saved, so a missing name means a newer widget. Falling back to the positional value there brings the shift back.

## 2026-09-26: Phase 94 Device Check Passed

- The "Image Stitch 2x2 Grid" template (ResizeImageMaskNode dynamic combo) ran end to end from the phone. This is the first local build containing Phases 89–96.

## 2026-09-26: Phase 90 Complete (code)

- Bypass, subgraph instance modes and virtual nodes now resolve like the frontend's `ExecutableNodeDTO`. The corpus known-failures list is empty; 565 of 572 templates convert cleanly.
- Lesson: bypassing a subgraph node skips its whole interior. Expanding it first was the source of most unresolved inputs.

## 2026-09-26: Phase 91 Complete (code)

- The app now checks a prompt against the live server before queueing, in the server's own words. The dialog warns and never blocks.
- Moving the checker into the app turned up a converter bug: list widget values must be wrapped as `{"__value__": [...]}`, or the server reads them as links.

## 2026-09-26: Phase 92 Complete (code) — Milestone 4 must-haves all delivered

- Server errors are shown per node, by title, everywhere: Generate, partial acceptance, runtime errors and the local queue.
- Lesson: the database uses `fallbackToDestructiveMigration`, so every schema bump needs an explicit migration, or users lose their workflows.

## 2026-10-12: Milestone 4 Device Checks Passed

- The user built the app locally and confirmed the device checks for Phases 90, 91, 92, 95 and 96 on the phone. All Milestone 4 must-haves are delivered and verified, so the milestone is ready to complete.

## 2026-10-12: Milestone 4 Complete

- Archived Phases 89–95 and the roadmap to `.gsd/milestones/Milestone 4/`; summary in `Milestone 4-SUMMARY.md`. Phase 96 had no plan folder (built directly) and is recorded in the archived roadmap.
- Templates converting cleanly: 123 → 565 of 572. Next: plan Milestone 5.

## 2026-10-12: Milestone 5 Created

- Stability focus, chosen by the user. Must-haves: visible import failures, no silent duplicate imports, queue re-check, CI, repo hygiene. The four Milestone 4 backlog features follow as lower-priority phases.
- Findings behind it: six log-only error handlers in `MainViewModel`, nine tracked log files in the repo root, and no `.github/` workflows.

## 2026-10-12: Phase 97 Complete (code)

- Import and sync failures are shown to the user. The important fix was behavioural: a workflow in editor format that could not be converted used to be saved as raw JSON and could never run; it is now refused with an explanation.
- Found along the way: `ErrorCard`'s Retry had never been clickable.

## 2026-10-12: Phase 98 Complete (code)

- Re-importing a template or server workflow now asks (Open / Copy / Replace) via a new `sourceKey` column (Room 11 → 12, explicit migration). The queue half was re-scoped: the run-time re-check was redundant after Phase 92, so items stuck in EXECUTING are recovered, failed items can be retried, and a missing server gives a reason.

## 2026-10-12: Phase 99 Complete

- GitHub Actions runs the unit tests and builds the debug APK on every push; the first run was green and also proved Phases 97 and 98 compile. The must-have stability items of Milestone 5 are now all done (device checks for 97 and 98 pending).

## 2026-10-12: Phase 100 Complete (code)

- Workflow cards show Ready / warnings / will fail with the first reason, from the existing pre-flight validator against the connected server; image/video/audio inputs are not flagged because the form replaces them.

## 2026-10-12: Phase 101 Complete (code)

- Dynamic combos (SaveVideo format etc.) are switchable in the form; the prompt is rebuilt for the new option with shared values kept. Also fixed a free-text field that appeared for dynamic combos when no node list was loaded.

## 2026-10-12: Phase 102 Complete (code)

- Research narrowed the phase: the converter already handled the custom widget kinds, so the work was form fields for colour and bounding box, PrimitiveNode text replace, and tests. Group nodes and CURVE/VIDEO_EDIT editors dropped for lack of any template using them. Milestone 5 phases are all implemented; device checks remain.
