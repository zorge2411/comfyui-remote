---
phase: 30
level: 2
researched_at: 2026-01-24
---

# Phase 30 Research

## Questions Investigated

1. What is the difference between ComfyUI "Graph Format" and "API Format"?
2. Can we convert Graph Format to API Format on the client side?
3. What metadata is required for the conversion?

## Findings

### Graph vs API Format

- **API Format**: Flat dictionary where keys are Node IDs. Values contain `class_type` and `inputs`. Used for `/prompt` endpoint.
- **Graph Format**: Object containing `nodes` array, `links` array, etc. Used for saving/loading in UI.
  - Nodes have `inputs` array (defining slots/links).
  - Nodes have `widgets_values` array (defining parameter values).
  - Explicit mapping between widget values and input names is NOT in the Graph JSON; it requires `object_info`.

### Conversion Requirements

To convert Graph -> API:

1. **Node IDs**: Preserved.
2. **Class Type**: Preserved from `type` field.
3. **Link Inputs**: Mapped from `node.inputs` array. Each input has `name` and `link` ID. Use `links` array to resolve upstream node/slot.
4. **Widget Inputs**: Mapped from `node.widgets_values` array.
   - **Crucial**: We need `object_info` (Node Definitions) to map the positional values in `widgets_values` to the named keys required by the API format.

### Existing Infrastructure

- `WorkflowParser` exists but only handles API format.
- `ImportWorkflowDialog` currently validates against Graph format and rejects it.
- We need to access `ObjectInfo` repository (to be verified if it exists and is accessible).

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Conversion Location | Client-side (Kotlin) | Allows immediate feedback and "Run" capability without backend roundtrip just for converting. |
| Metadata Source | Live `object_info` | Must fetch from connected server to ensure verifying against currently installed nodes. |

## Dependencies Identified

| Component | Purpose |
|-----------|---------|
| `ObjectInfoRepository` | Needed to fetch node definitions. |
| `GraphToApiConverter` | New domain service to handle the transformation. |

## Risks

- **Missing Object Info**: If user imports a workflow with nodes not present in the current server instance.
  - *Mitigation*: Warn user about missing nodes, or fail gracefully.
- **Breaking Changes in ComfyUI**: If ComfyUI changes Graph format.
  - *Mitigation*: Follow standard structure (nodes/links), robust error handling.

## Ready for Planning

- [x] Questions answered
- [x] Approach selected
- [x] Dependencies identified
