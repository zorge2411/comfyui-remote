# Phase 33: Theme Mode Selection

**Objective**: Support Light, Dark, and System theme modes.

## Technical Design

### Preferences

- Add `THEME_MODE_KEY` (Int) to `UserPreferencesRepository`.
  - `0`: System (Follow system)
  - `1`: Light
  - `2`: Dark

### ViewModel

- Add `val themeMode: StateFlow<Int>` to `MainViewModel`.
- Add `fun updateThemeMode(mode: Int)` to `MainViewModel`.

### Theme Engine

- Update `ComfyUI_front_endTheme` in `Theme.kt`:

  ```kotlin
  @Composable
  fun ComfyUI_front_endTheme(
      themeMode: Int = 0, // 0: System, 1: Light, 2: Dark
      dynamicColor: Boolean = true,
      content: @Composable () -> Unit
  ) {
      val isDark = when (themeMode) {
          1 -> false
          2 -> true
          else -> isSystemInDarkTheme()
      }
      // ... rest of logic ...
  }
  ```

### UI

- Add "Appearance" section in `SettingsScreen.kt`.
- Use `SingleChoiceSegmentedButtonRow` for mode selection (System/Light/Dark).

## Tasks

- [ ] Implement `THEME_MODE_KEY` in `UserPreferencesRepository`
- [ ] Add `themeMode` state and setter to `MainViewModel`
- [ ] Refactor `Theme.kt` to accept explicit `themeMode`
- [ ] Update `MainActivity.kt` to pass `themeMode` to theme wrapper
- [ ] Add Theme selection UI in `SettingsScreen.kt`
- [ ] Verify functionality (manual toggle)
