---
phase: 25
plan: 1
wave: 1
---

# Plan 25.1: JSON File Import Support

## Objective

Enhance the workflow import flow to support picking `.json` files from device storage.

## Tasks

- [ ] Add `GetContent` launcher to `ImportWorkflowDialog` <!-- id: 23 -->
- [ ] Implement file content reading using `LocalContext.current.contentResolver` <!-- id: 24 -->
- [ ] Add "Pick File" button to UI and handle auto-population <!-- id: 25 -->
- [ ] Verify: File picker correctly populates fields and imports workflow <!-- id: 26 -->
