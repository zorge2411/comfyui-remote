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
