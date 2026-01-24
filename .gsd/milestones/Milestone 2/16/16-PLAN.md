---
phase: 16
plan: 1
wave: 1
---

# Plan 16.1: Icon Generation & Selection

## Objective

Generate 5 premium icon concepts, allow user selection, and implement the chosen icon into the Android project.

## Context

- .gsd/SPEC.md
- .gsd/ROADMAP.md
- .gsd/phases/16/RESEARCH.md
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

## Tasks

<task type="auto">
  <name>Generate Icon Concepts</name>
  <files>
    d:\Antigravity\ComfyUI frontend\.gsd\phases\16\candidates\
  </files>
  <action>
    Create a 'candidates' directory.
    Use `generate_image` to create 5 distinct icons based on RESEARCH.md concepts:
    1. Neon Flux
    2. Glass Flow
    3. Minimal Node
    4. Comfy Gradient
    5. Digital Canvas
    Save them as `concept_1.png` to `concept_5.png`.
  </action>
  <verify>
    Check if 5 images exist in the candidates directory.
  </verify>
  <done>
    5 images generated and available for review.
  </done>
</task>

<task type="checkpoint:decision">
  <name>Select Icon</name>
  <files>N/A</files>
  <action>
    Present the 5 icons to the user via notify_user.
    Ask user to select one (e.g., "Concept 2").
  </action>
  <verify>
    User response received.
  </verify>
  <done>
    One concept selected.
  </done>
</task>

<task type="auto">
  <name>Implement Selected Icon</name>
  <files>
    app/src/main/res/drawable/ic_launcher_foreground.png
    app/src/main/res/values/ic_launcher_background.xml
  </files>
  <action>
    1. Backup existing `ic_launcher_foreground.xml` to `ic_launcher_foreground_backup.xml`.
    2. Convert selected concept to `ic_launcher_foreground.webp` (optimized).
    3. Place in `app/src/main/res/drawable/`.
    4. Ensure `ic_launcher.xml` references the new drawable (it should if name matches).
    5. Adjust `ic_launcher_background.xml` color if necessary to match the icon's edge/transparent area.
  </action>
  <verify>
    Build the app and verify the icon appears correctly (or use an emulator verify if possible, otherwise relies on build).
  </verify>
  <done>
    App builds with new icon.
  </done>
</task>

## Success Criteria

- [ ] 5 high-quality icon options created.
- [ ] 1 option selected and integrated.
- [ ] App compiles without resource errors.
