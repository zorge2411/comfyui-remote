# Phase 33 Summary: Theme Mode Selection

Implemented support for manual theme mode selection (System, Light, Dark) in the application settings.

## Changes

### Data Layer

- Added `theme_mode` preference to `UserPreferencesRepository`.
- Supports three states: `0` (System), `1` (Light), `2` (Dark).

### ViewModel

- Exposed `themeMode` as a `StateFlow` in `MainViewModel`.
- Added `updateThemeMode(mode: Int)` function.

### Theme Engine

- Refactored `Theme.kt` to accept a `themeMode` parameter.
- The logic now priorities the explicit selection over the system setting unless "System" mode is selected.

### UI

- Added an **Appearance** section to the `SettingsScreen`.
- Implemented mode selection using a `SingleChoiceSegmentedButtonRow`.
- Updated `MainActivity` to reactively update the theme when preferences change.

## Verification Details

- Verified that preference storage works correctly.
- Verified that the UI updates immediately when a different theme is selected.
- Verified that the "System" option follows the OS theme setting.
