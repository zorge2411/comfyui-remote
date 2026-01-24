# JOURNAL.md

## 2026-01-23: Phase 22 Completion

### Workflow History Implementation

Successfully implemented the dedicated History system.

- **Decisions**:
  - Used `GeneratedMediaEntity` as the basis for history to avoid table duplication.
  - Decided on active prompt caching in `MainViewModel` to bridge the gap between Queueing and Completion.
  - Implemented a temporary `WorkflowEntity` pattern (id=0) for state restoration, allowing the existing `DynamicFormScreen` to work without modifications.
- **Outcome**: The "Saved Workflows" list is now clean, and users can reliably re-run any past generation.
- **Technical Note**: Migration 2->3 was verified stable.
