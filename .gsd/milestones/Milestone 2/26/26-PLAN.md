---
phase: 26
plan: 1
wave: 1
---

# Plan 26.1: Gallery Zoom & Performance Refinement

## Objective

Fix the laggy and unresponsive zoom function in the image gallery by refactoring the `ZoomableImage` component to use a more efficient state-driven approach and robust boundary calculations.

## Context

- .gsd/ROADMAP.md
- app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt

## Tasks

<task type="auto">
  <name>Refactor ZoomableImage State</name>
  <files>
    <file>app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt</file>
  </files>
  <action>
    - Introduce a `ZoomState` class to manage `scale`, `offset`, and `bounds`.
    - Replace `animateFloatAsState` with `Animatable` for scale and offset.
    - Implement a `Modifier.zoomable` or similar pattern to handle gestural input more efficiently.
    - Ensure `HorizontalPager` is only disabled when scale is significantly > 1f.
  </action>
  <verify>Code builds successfully and visual inspection of logic shows reduced recomposition triggers.</verify>
  <done>ZoomState is implemented and provides stable scale and offset values.</done>
</task>

<task type="auto">
  <name>Implement Aspect-Ratio Aware Bounds</name>
  <files>
    <file>app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt</file>
  </files>
  <action>
    - Calculate image aspect ratio and container aspect ratio.
    - Compute correct pan bounds for each scale level to prevent "dead space" or over-panning.
    - Implement "double-tap-to-zoom" with optimized threshold and target scale (e.g., 2.5x).
    - Ensure snapping back to 1x centers the image properly.
  </action>
  <verify>Visual verification of boundary snapping.</verify>
  <done>Images are bounded correctly regardless of their aspect ratio.</done>
</task>

## Success Criteria

- [ ] Zoom is perceptibly smoother and more responsive.
- [ ] No conflict between zooming and horizontal paging.
- [ ] High-resolution images do not cause frame drops during pan/zoom.
