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
