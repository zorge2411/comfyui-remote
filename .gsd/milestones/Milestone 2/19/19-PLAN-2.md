---
phase: 19
plan: 2
wave: 2
---

# Plan 19.2: Storage Integration & Save Action

## Objective

Implement the logic to save media files to the user-selected folder and add a "Save to Device" action to the Media Detail screen.

## Context

- `app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt`
- `app/src/main/java/com/example/comfyui_remote/utils/StorageUtils.kt` [NEW]

## Tasks

<task type="auto">
  <name>Storage Utility Implementation</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/utils/StorageUtils.kt
  </files>
  <action>
    Create [NEW] `StorageUtils.kt`.
    Implement `saveMediaToFolder` using `DocumentFile` and `ContentResolver`.
    Handle file name collisions and media types.
  </action>
  <verify>
    Verify utility can write a test byte stream to a SAF URI.
  </verify>
  <done>
    Generic utility exists to save files to a SAF folder URI.
  </done>
</task>

<task type="auto">
  <name>Media Detail Save Action</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/MediaDetailScreen.kt
  </files>
  <action>
    Add a "Save" icon to the `TopAppBar` or as an option in the "More" menu.
    Trigger `saveMediaToFolder` when clicked, using the `saveFolderUri` from ViewModel.
    Show a snackbar or toast on success/failure.
  </action>
  <verify>
    Clicking "Save" successfully places a file in the selected folder.
  </verify>
  <done>
    Users can save images/videos to their preferred location.
  </done>
</task>

## Success Criteria

- [ ] "Save to Device" works with the custom folder selected in settings.
- [ ] Users get visual feedback (Snackbar) when a file is saved.
- [ ] Subfolder structure handles (optional: flat save or mirror subfolders).
