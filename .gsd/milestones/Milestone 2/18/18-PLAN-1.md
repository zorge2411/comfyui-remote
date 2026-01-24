---
phase: 18
plan: 1
wave: 1
---

# Plan 18.1: Horizontal Gallery Paging

## Objective

Enable horizontal swiping between images and videos in the gallery detail view, providing a fluid "gallery feel".

## Context

- app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
- app/src/main/java/com/example/comfyui_remote/MainActivity.kt (Navigation)

## Tasks

<task type="auto">
  <name>Navigation Update</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/MainActivity.kt
    app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt
  </files>
  <action>
    Modify `MediaDetailScreen` to accept an `initialIndex` or `startMediaId` but also consume the full list of media from the ViewModel.
    Update `GalleryScreen` to pass the correct identifier.
  </action>
  <verify>
    Detail screen still opens the correct image.
  </verify>
  <done>
    Navigation supports opening detail view with context of the full list.
  </done>
</task>

<task type="auto">
  <name>HorizontalPager Implementation</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
  </files>
  <action>
    Implement `HorizontalPager` in `MediaDetailScreen`.
    Load `allMedia` from ViewModel.
    Ensure `initialPage` matches the clicked image index.
    Update `TopAppBar` and `Actions` to refer to `pagerState.currentPage`'s media entity.
  </action>
  <verify>
    Swiping left/right changes the image.
  </verify>
  <done>
    Gallery detail view supports horizontal paging.
  </done>
</task>

<task type="auto">
  <name>Gesture Conflict Resolution</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
  </files>
  <action>
    Ensure `HorizontalPager` doesn't intercept swipes when the image is zoomed in.
    Ensure `Swipe-to-Dismiss` (vertical) still works independently of horizontal paging.
  </action>
  <verify>
    Zooming works and locks paging. Vertical dismiss works.
  </verify>
  <done>
    All gestures (Paging, Zooming, Dismissing) work harmoniously.
  </done>
</task>

## Success Criteria

- [ ] User can swipe between images in full-screen mode.
- [ ] Actions (Share, Info, etc.) apply to the currently visible image.
- [ ] Zooming into an image prevents accidental page turns.
