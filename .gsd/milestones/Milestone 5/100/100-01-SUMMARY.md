# Plan 100.1 Summary: Device Audit and UI Spec

**Completed:** 2026-09-28

## Delivered

- **`100-AUDIT.md`** (`59f9d48`): 22 on-device screenshots (portrait light and dark, landscape light) on the Fairphone 6, Android 16, 480 dpi. Every issue has a severity and an owning phase.
  - **A1** Status-bar icon contrast is inverted in both themes.
  - **A2** Double top padding: +37 dp above every `TopAppBar`, measured as the title centre at 316 px against an expected 206 px.
  - **A3** Landscape: on Connection, the Secure switch is drawn over the host field and Connect can't be reached; Settings' "Change Folder" can't be reached.
  - Plus a per-screen table (Connection, Workflows, Import, Templates, form, Queue, Gallery, History, MediaDetail, Settings).
- **`100-UI-SPEC.md`**: 12 conventions (spacing, top bars, section headers, cards, status banners, dialogs, states, buttons, colour, layout and insets, text, touch targets). **The user approved it as written.**

## Notes

- The app theme was switched to Dark for the dark captures and restored to System (theme_mode 0).
- The user rotated the phone for landscape; no system settings were changed over adb.
- Screenshots stay in the session scratchpad and were not committed (personal images). Three screens without personal content (landscape Connection, dark Settings, Queue) were shared with the user as before-screenshots.
