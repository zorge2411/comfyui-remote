---
phase: 17
plan: 3
wave: 3
---

# Plan 17.3: Premium Gallery - Metadata

## Objective

Provide a "Details" view that shows technical information about the generation (Prompt, Model, Seed, etc.).

## Context

- app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
- app/src/main/java/com/example/comfyui_remote/data/WorkflowExecutionHistory.kt

## Tasks

<task type="auto">
  <name>Metadata Overlay</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
    app/src/main/java/com/example/comfyui_remote/uicomponents/MetadataSheet.kt
  </files>
  <action>
    Create a BottomSheet or Dialog accessible via an "Info" (i) button.
    Display:
    - Date/Time
    - Format/Resolution
    - Prompt (workflow inputs)
    - Seed
    - Model used
    Allow copying Prompt/Seed to clipboard.
  </action>
  <verify>
    User manual verification.
  </verify>
  <done>
    Can view generation details for any image.
  </done>
</task>

## Success Criteria

- [ ] Info sheet displays correct metadata for the active image.
