---
phase: 24
plan: 1
wave: 1
---

# Plan 24.1: Dynamic Discovery & History Robustness

## Objective

Use `/object_info` to support any node type and `/history/{id}` to ensure no generation results are lost due to connection drops.

## Context

Analysis of provided Python reference shows gaps in our "recon" logic (history fallback) and opportunities for dynamic UI.

## Proposed Changes

1. **API**: Add `getHistoryById(id)` to `ComfyApiService`.
2. **Discovery**: Fetch `object_info` on connect.
3. **Parser**: Update `WorkflowParser` to prioritize metadata for UI generation.
4. **Recovery**: Implement "Check History" logic when a prompt finishes but WS message was missed.
