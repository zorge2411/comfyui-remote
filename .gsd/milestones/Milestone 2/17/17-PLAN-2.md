---
phase: 17
plan: 2
wave: 2
---

# Plan 17.2: Premium Gallery - Actions

## Objective

Implement actions that allow the user to use the generated images outside the app: Sharing, Wallpaper, and Bulk Deletion.

## Context

- app/src/main/AndroidManifest.xml (Permissions)
- app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
- app/src/main/java/com/example/comfyui_remote/uicomponents/MediaGrid.kt

## Tasks

<task type="auto">
  <name>Share Sheet</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
    app/src/main/java/com/example/comfyui_remote/utils/ShareUtils.kt
  </files>
  <action>
    Create `ShareUtils` helper to launch the native Android Share Intent.
    Add a "Share" icon to the top/bottom bar in Media Detail view.
    Share the file URI using `FileProvider`.
  </action>
  <verify>
    User manual verification (taping share opens system dialog).
  </verify>
  <done>
    Can share an image to other apps (WhatsApp, etc.).
  </done>
</task>

<task type="auto">
  <name>Set as Wallpaper</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
    app/src/main/java/com/example/comfyui_remote/utils/WallpaperUtils.kt
  </files>
  <action>
    Implement logic to set the current image as device wallpaper.
    Requires `SET_WALLPAPER` permission (maybe) or use `WallpaperManager` intent (cleaner).
    Add "Set as Wallpaper" to a dropdown menu (overflow menu) in Detail View.
  </action>
  <verify>
    User manual verification.
  </verify>
  <done>
    Can set an image as wallpaper directly from the app.
  </done>
</task>

<task type="auto">
  <name>Multi-select & Bulk Actions</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/GalleryScreen.kt
    app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
  </files>
  <action>
    Add `selectionMode` state to Gallery.
    Long-press on an image enters selection mode.
    Tapping images in selection mode toggles selection.
    Show context bar with "Delete {N} items" and "Share {N} items" buttons.
    Implement bulk delete in ViewModel.
  </action>
  <verify>
    User manual verification.
  </verify>
  <done>
    Can delete multiple images at once.
  </done>
</task>

## Success Criteria

- [ ] Share works.
- [ ] Set Wallpaper works.
- [ ] Bulk delete works.
