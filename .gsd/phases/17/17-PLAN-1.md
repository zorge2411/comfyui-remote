---
phase: 17
plan: 1
wave: 1
---

# Plan 17.1: Premium Gallery - Visuals & Gestures

## Objective

Implement high-end visual transitions and standard gestures to make the gallery feel "native" and premium.

## Context

- .gsd/SPEC.md
- app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt
- app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt

## Tasks

<task type="auto">
  <name>Hero Transitions</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/Navigation.kt
    app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
  </files>
  <action>
    Implement Shared Element Transitions between the Gallery Grid and Media Detail view.
    Use `AnimatedContent` or `SharedElement` API (experimental Compose) if available, or a custom expansion animation.
    Ensure the image "flies" from its grid position to fill the screen.
  </action>
  <verify>
    User manual verification (smooth transition).
  </verify>
  <done>
    Clicking an image in grid smoothly expands to full screen.
  </done>
</task>

<task type="auto">
  <name>Swipe to Dismiss</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
  </files>
  <action>
    Wrap the Detail view in a gesture detector or `Swipeable` layout.
    Vertical drag down should allow dismissing the detail view, returning to the gallery.
    Scale down the image slightly as the user drags down.
  </action>
  <verify>
    User manual verification.
  </verify>
  <done>
    Dragging down on detail view closes it.
  </done>
</task>

<task type="auto">
  <name>Double-Tap Zoom</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
  </files>
  <action>
    Enhance the existing ZoomableImage capability.
    Detect double-tap events to toggle between 1.0x (fit) and 2.5x (zoomed in) scale.
    Animate the zoom level change.
  </action>
  <verify>
    User manual verification.
  </verify>
  <done>
    Double-tapping an image zooms in/out smoothly.
  </done>
</task>

## Success Criteria

- [ ] Grid -> Detail transition is seamless.
- [ ] Detail view can be dismissed with a swipe.
- [ ] Zoom interaction feels natural (double tap).
