# Phase 100 UI Audit

**Date:** 2026-09-28
**Device:** Fairphone 6, Android 16, 1116×2484 at 480 dpi (3×); status bar 110 px (37 dp), navigation bar 72 px.
**Build:** `21d123b`.
**Captured:** 22 screenshots (portrait light, portrait dark, landscape light). They stay in the session scratchpad because they contain personal images and prompts.

Severity: **B** breaks (can't be used, or can't be read), **I** inconsistent, **P** polish. "Fix" is the phase that owns the issue.

## App-wide

| # | Issue | Evidence | Sev | Fix |
|---|---|---|---|---|
| A1 | **Status-bar icons have the wrong contrast in both themes.** Light theme gives white icons on a light background; dark theme gives dark icons on a dark background. | Clock and icons barely visible on every screenshot. `Theme.kt:55` sets `isAppearanceLightStatusBars = darkTheme` (inverted) | B | 100 |
| A2 | **Double top padding** on every screen with a `TopAppBar`: an extra 37 dp gap above the title. | TopAppBar titles are centred at y≈316 px; with a single status-bar inset they'd be at ≈206 px. Settings, with no top bar, is correct at 158 px | I | 100 |
| A3 | **Landscape is unusable** on Connection and Settings, and cramped everywhere else. | Landscape Connection: the Secure switch is drawn over the host field, and Port, Connect and Disconnect can't be reached. Settings: "Change Folder" is off-screen. Workflows: one card fits between the 37 dp extra gap and the ~80 dp tab bar | B | 100 (Connection, Settings, insets); 102/105 (tab bar height) |
| A4 | **Tab bar has no labels**; five 28 dp icons. "More" is really History, and it also highlights on Settings. | The icons are ambiguous (network, tree, clipboard, photos, menu) | I | 102 |
| A5 | **Tabs keep deep state oddly.** The Workflows tab reopened a media viewer from earlier; Back from the workflow list goes to Connection. | Seen while navigating | I | 102 |
| A6 | Emoji used as status icons: "⚠️ Missing Models", "📦 model", "✓", "✖". | Workflow cards, form cards, import dialog | I | 101, 103, 105 |
| A7 | Hard-coded colours: bright `Color.Green` connection dot; black and white scrims. | Connection (light and dark) | I | 100 |

## Per screen

| Screen | Issues | Sev | Fix |
|---|---|---|---|
| **Connection** | No top bar, only a centred headlineMedium. Doesn't scroll (A3). "Disconnect" and "Go to Workflows" are both filled buttons, so there's no main action. `Color.Green` dot. It's a tab of its own while the connection could be a status indicator. | B/I | 100 (layout, status chip), 102 (tab role) |
| **Workflows** | Extra top gap (A2). Cards show "Created: date" plus "📦 model" chip, with Rename/Delete icons on every card (clutter). FAB overlaps the last card in landscape. No search or sort. | I | 103 |
| **Import dialog** | Title "Import Workflow (API Format)" and the note "Please use 'API format'" are out of date: graph format has been supported since Phase 30. Filled `TextField`s (every other form is outlined). Import is enabled only after a paste, with no hint. | I | 103 |
| **Templates** | Extra top gap. Non-mirrored back arrow. Hand-built loading scrim. Otherwise good (thumbnails, chips, badge). | P | 100 (arrow), 103 |
| **Form (DynamicFormScreen)** | Custom title row that scrolls away with the content, so Back and Info disappear. Every input is a full-width outlined box labelled with raw node names ("CLIPTextEncode (text)", "KSampler (denoise)"). The key settings (seed, size, steps) are buried among loaders. `SaveImage (filename_prefix)` gets a tall multiline box with info and clear icons. Generate and Queue sit at the very bottom after every field, and the result below them. The "Open in Gallery" chip hangs off the result image's edge. Status cards stack at the top. | B/I | 101 |
| **Queue** | Title "Local Queue", though it now also holds model downloads. Back arrow on a tab. A `BottomAppBar` with a play FAB is stacked on top of the tab bar, so about 250 dp of chrome at the bottom. The "Model downloads" header is black, not primary like other section headers. | I | 102, 105 |
| **Gallery** | Extra top gap. FAB overlaps the grid's last row. Four top-bar icons (bookmark, filter, date, refresh) with no labels. Multi-delete has no confirmation. | I | 104 |
| **History ("More")** | Title "Execution History". Settings is reached only through its gear icon. Cards: History is the only screen with elevation; "Tap to Restore" is a text link inside the card; items without a workflow name show as "History: 09-28 05:18". | I | 102, 104 |
| **MediaDetail** | A light strip behind the status bar above the black viewer (no edge-to-edge). The tab bar shows inside the viewer. Hard-coded 32 dp sheet padding. | I | 100 (insets), 102 (tab bar), 104 |
| **Settings** | No top bar or back button (it's pushed from History). Doesn't scroll (A3). The "System / Light / Dark" segmented button is fine. The history-limit slider looks odd: the thumb sits at the far left as a bar, with tick dots across. | B/I | 100 |

## Not UI (noted only)

- Several recent Krea-2 Int8 outputs look corrupted (heavy pattern noise) in the gallery, history and viewer. That's likely the model or quantization output, not the app; not investigated.

## Ranking for Phase 100

1. A1 status-bar contrast.
2. A3 landscape Connection and Settings.
3. A2 double inset padding.
4. Connection and Settings as reference screens (top bar, back, scroll, status chip, button hierarchy).
5. Non-mirrored back arrows.

Everything else is in the phase shown above.
