---
phase: 28
plan: 1
wave: 1
---

# Plan 28.1: Post-Import Auto-Selection

## Objective

Seamlessly transition to the node-editing view immediately after a successful workflow import.

## Tasks

- [ ] Update `WorkflowRepository` and `MainViewModel` to return/handle new workflow IDs <!-- id: 31 -->
- [ ] Implement auto-selection logic in `importWorkflow` <!-- id: 32 -->
- [ ] Trigger navigation to `DynamicFormScreen` on success <!-- id: 33 -->
- [ ] Verify: Importing a file takes user directly to the form view <!-- id: 34 -->
