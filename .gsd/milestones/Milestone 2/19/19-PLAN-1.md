---
phase: 19
plan: 1
wave: 1
---

# Plan 19.1: Settings Framework & Folder Selection

## Objective

Implement a Settings screen that allows users to select a custom folder for saving generated media, and persist this preference using DataStore and SAF persistent permissions.

## Context

- `app/src/main/java/com/example/comfyui_remote/data/UserPreferencesRepository.kt`
- `app/src/main/java/com/example/comfyui_remote/MainActivity.kt`
- `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`

## Tasks

<task type="auto">
  <name>Preference Persistence</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/data/UserPreferencesRepository.kt
    app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
  </files>
  <action>
    Add `SAVE_FOLDER_URI` key to `UserPreferencesRepository`.
    Expose `saveFolderUri` Flow and `saveSaveFolderUri` function in ViewModel.
  </action>
  <verify>
    Verify that the key is added and ViewModel exposes the new flow.
  </verify>
  <done>
    Storage folder URI can be persisted in DataStore.
  </done>
</task>

<task type="auto">
  <name>Settings Screen & Navigation</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/ui/SettingsScreen.kt
    app/src/main/java/com/example/comfyui_remote/MainActivity.kt
  </files>
  <action>
    Create [NEW] `SettingsScreen.kt` with a "Select Save Folder" button.
    Implement `ACTION_OPEN_DOCUMENT_TREE` launcher with persistent permission logic.
    Add "Settings" route to `MainActivity` NavHost and `NavigationBar`.
  </action>
  <verify>
    Settings screen is accessible from the navigation bar.
    Clicking "Select Save Folder" opens the system file picker.
  </verify>
  <done>
    User can pick a folder and the app requests persistent access.
  </done>
</task>

## Success Criteria

- [ ] Users can navigate to a new "Settings" screen.
- [ ] Users can pick a folder using the system UI.
- [ ] The app remembers the selected folder across restarts.
