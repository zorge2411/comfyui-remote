## Phase 103 Verification

**Device check:** 2026-09-30, on the Fairphone 6 (adb). Builds `ecb2bcc` and `e55479e`.

### Must-have

- [x] **Workflow list cards show what a workflow is and whether it will run.** VERIFIED on the device:
  - Cards show the last-result thumbnail, or a placeholder when there's no result.
  - Model labels show up to 2, then "+N".
  - The badge shows "Ready"; "Won't run" opens "Problems in <name>" with the issue list and Close.
  - There are no badges while offline.
  - One More menu per card.

### Device checks (103.3 task 2)

- **Migration 14 → 15:** PASS. The database is at `user_version` 15 with `workflows.lastUsedAt`; 13 workflows and 23 gallery items survived the upgrade.
- **Badges:** PASS, after one fix:
  - "Krea2_NSFW character edit" first showed "Won't run": an Ollama node's model tag (`hf.co/…:Q8_0`) on an input the server lists no options for was taken for a missing file.
  - Fixed in `ecb2bcc`, with a regression test. The card now shows "Ready".
  - Z-Image-Turbo shows "Ready".
- **Thumbnails:** PASS. Workflows with a stored last result show it; the others show the placeholder.
- **Sort:**
  - Last used: PASS. Opening a workflow moves it to the top.
  - Name: PASS. Alphabetical, ignoring case.
  - The choice survives a force-stop and restart: PASS.
- **Search:**
  - PASS for names;
  - PASS for models: "int8_convrot" found 5 workflows through their model files;
  - "No matches" with "Clear search": PASS.
- **Server section:** PASS. "On the server (15)" is collapsed by default; Show lists the entries with their paths and Import buttons.
  - After a cold start it was empty until a pull to refresh. Fixed in `e55479e` and verified.
- **FAB:** PASS in portrait; the last card clears the + button.
- **Import dialog:** PASS. Title "Import workflow", outlined Name and Workflow JSON fields, and hint text.
- **Templates:**
  - PASS: back arrow, Reload, chips, "566 templates", AppCard colours, "API" badge.
  - The search placeholder wrapped onto two lines in portrait; shortened to "Search templates".
  - Not run: the reload-error banner (it would need aeroplane mode) and importing a template (it would add a workflow).
- **Landscape:**
  - The first pass found the list cramped: the top bar, search field, offline banner and tab bar left room for about one card, and none at all when offline.
  - Fixed in `ecb2bcc`: the banner and search are the list's first item and scroll away.
  - Re-checked on the device: PASS. The search field scrolls away, and the list uses the height between the top bar and the tab bar (about 1.5 cards).
  - For Phase 105:
    - the labelled tab bar takes about a third of the landscape height (a navigation rail in landscape would free it);
    - the + button can cover a card's ⋮ menu until the list is scrolled.

### Automated

- `testDebugUnitTest`: 251 tests, 0 failures. `assembleDebug` OK.

### Verdict: PASS. Final build `e55479e` plus the shortened template placeholder, installed and checked 2026-09-30.
