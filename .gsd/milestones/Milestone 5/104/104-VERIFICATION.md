## Phase 104 Verification

### Must-Haves

- [x] **Filters apply locally, never wipe the gallery, and show as chips with formatted dates.** `GalleryViewTest` (5), plus the device check (Today/Videos chips, filtered-empty state, saved list applied, item count unchanged).
- [x] **Every Remove or Delete is confirmed; removed items stay removed across sync and Reload.**
  - Device: Remove 1 was confirmed; the item stayed hidden after pull to refresh; Restore brought it back.
  - Reload keeps hidden rows (`deleteVisible`), and since `72912fb` keeps the gallery when the server's history is empty (`HistoryCheckTest` 3).
- [x] **At most three top-bar actions; the FAB never covers items; theme colours in the gallery.** Checked on the device, in light and dark.
- [x] **The viewer is full screen (no tab bar, black, light icons), with Share, Save, Open in form, Info and Remove.**
  - Checked on the device in light, dark and landscape.
  - Info showed the prompt and seed; Open in form restored them (seeds Fixed since `bdffb8b`).
- [x] **One encoded URL builder.** `MediaUrlsTest` (3); Share worked on the device with the new URLs.
- [x] **Database 13 → 14 keeps all media and workflows.** 7 of 7 and 13 of 13 on the device.

### Not verified on the device

- Sharing 2 or more items at once: only one item was visible.
- The video badge, and no wallpaper option for videos: the gallery has no videos.
- The unreachable-server banner.

### Found and fixed during the check

- Reload from server emptied the gallery after a ComfyUI restart (`72912fb`).
- The landscape grid showed one row (`bdffb8b`).
- Open in form restored seeds as Random (`bdffb8b`).

### Open item

- The 6 gallery entries deleted during the Reload test can be restored from the prepared database copy, if the user approves writing to the app's private storage.

### Verdict: PASS
