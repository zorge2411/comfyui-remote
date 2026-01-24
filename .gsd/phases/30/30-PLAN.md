# Phase 30: Support for Importing Graph Format JSON

## Goal Description

Enable functionality to import workflows defined in the "Graph Format" (typically used by the ComfyUI frontend for saving workflows with metadata) by converting them into the "API Format" required for execution.

**Findings**:

- `GraphToApiConverter` is already implemented.
- `ImportWorkflowDialog` is already implemented with auto-detection.
- `MainViewModel` has the wiring.
- **Missing**: Verification (Unit Tests) and validation of robustness.

## User Review Required

None.

## Proposed Changes

### Verification

- **Create** `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterTest.kt`.
- **Implement** test cases:
  - `convert_BasicGraph_ReturnsApiFormat`: Verify nodes and links are mapped.
  - `convert_Widgets_MappedCorrectly`: Verify widget values are assigned.
  - `convert_UnconnectedSlots_Ignored`: Verify empty slots doesn't break it.

### Refinement (Conditional)

- If tests fail, update `GraphToApiConverter.kt`.

## Verification Plan

### Automated Tests

```bash
./gradlew testDebugUnitTest
```
