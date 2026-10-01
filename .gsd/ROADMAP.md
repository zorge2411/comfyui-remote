# ROADMAP.md

> **Current Milestone**: Milestone 5: UI/UX Overhaul
> **Goal**: The app is easier to use and consistent across screens. The workflow form puts the important fields first, and every screen follows the same Material 3 conventions (phone portrait; landscape must not break).
> **Previous**: Milestone 4 (Workflow Compatibility), archived in `.gsd/milestones/Milestone 4/` (summary: `Milestone 4-SUMMARY.md`).

## Scope (agreed 2026-09-27)

- **Areas:** workflow form, navigation and structure, gallery and media viewer, workflow list and templates.
- **Look:** polish Material 3. Keep Material You dynamic colour; fix spacing, hierarchy, consistency and states. No new brand identity.
- **Pain points to fix:** the form is cluttered; screens are inconsistent.
- **Form factor:** phone portrait. Landscape must stay usable (today it hides the Connect button). No tablet layouts.

## Must-Haves

- [x] Shared UI building blocks used on every screen: top bar, section header, card, dialog, and empty, loading and error states (Phases 100, 105)
- [x] The workflow form shows the prompt and key settings first; everything else is grouped and collapsible; status cards (missing nodes or models, errors, progress) sit in one place (Phase 101)
- [x] Clear navigation: a tab bar with defined roles for Queue, History and Gallery, and connection state visible without a dedicated screen (Phase 102)
- [x] Workflow list cards show what a workflow is and whether it will run: a compatibility badge (carried over from Milestone 4) and the last result (Phase 103)
- [x] Gallery and media viewer follow the same conventions (Phase 104)
- [x] No screen breaks in landscape; dark mode and touch targets checked on the device (Phases 100, 105). Connection and Settings are fixed; status-bar contrast and insets are fixed app-wide

## Nice-to-Haves

- [ ] Pin favourite fields per workflow to the top of the form (Phase 106)
- [ ] Shared-element / motion transitions between the list, form and result (Phase 106)
- [x] Haptic feedback on Generate and on completion (Phase 105)

## Phases

### Phase 100: UI Audit and Shared Components

**Status**: ✅ Done, device-verified 2026-09-28 (`.gsd/phases/100/`: `100-AUDIT.md`, `100-UI-SPEC.md`)
**Objective**: Build the baseline the other phases rely on.
- Screenshot every screen and state on the phone (light and dark) and list the inconsistencies: top bars, buttons, dialogs, cards, spacing, empty, loading and error states.
- Define the conventions in a short UI spec.
- Build shared composables in `ui/components/`: top bar, section header, card, confirm dialog, empty, loading and error states, spacing tokens. Only the Phase 99 `SectionHeader` and `Format.kt` exist today.
- Fix landscape hiding the Connect button.
**Plans**:
- [x] 100.1 Device audit and UI spec; the user approves the spec (wave 1)
- [x] 100.2 Shared components and theme cleanup (wave 2)
- [x] 100.3 Edge-to-edge insets, Connection and Settings as reference screens, device check (wave 3)
**Depends on**: none

### Phase 101: Workflow Form Declutter

**Status**: ✅ Done, device-verified 2026-09-28 (`.gsd/phases/101/`)
**Objective**: Make `DynamicFormScreen` (about 1,000 lines) quick to use.
- The positive prompt stays first (Phase 82).
- Key settings (seed, width and height, steps, cfg, sampler and scheduler, model) go in a main group.
- The remaining inputs are grouped per node and collapsed by default.
- The missing-nodes and missing-models cards, server errors and progress are merged into one status area.
- The result preview and Generate/Queue actions are easy to reach.
**Plans**:
- [x] 101.1 FormLayout (main settings and groups) and seed policy (Random/Fixed) (wave 1)
- [x] 101.2 Remembered form values per workflow, Room 12 → 13 (wave 1)
- [x] 101.3 Form UI: fixed top and bottom bars, status area with the result, collapsed node sections; device check (wave 2)
**Depends on**: Phase 100

### Phase 102: Navigation and Structure

**Status**: ✅ Done, device-verified 2026-09-30 (`.gsd/phases/102/`); Queue Remove/Stop left for a manual check
**Objective**: Rework the tab bar and screen roles.
- Connection becomes a status indicator plus settings, not a tab of its own.
- Define what Queue, History and Gallery are each for, and remove the overlap.
- Decide where Templates and Settings live.
- Make back behaviour consistent.
- Decided 2026-09-29: labelled tabs Workflows, Queue, Gallery, Settings; History removed; auto-connect on start; Queue shows the server queue too; "Not connected" banner and disabled server actions offline.
**Plans**:
- [x] 102.1 Server queue (parse, repository, cancel/interrupt), auto-connect, connection gate (wave 1)
- [x] 102.2 Tabs and back behaviour, connection chip, Connection as pushed screen, Settings tab (wave 2)
- [x] 102.3 Queue tab with server queue, offline gating; device check (wave 3)
**Depends on**: Phase 100

### Phase 103: Workflow List and Templates

**Status**: ✅ Done, device-verified 2026-09-30 (`.gsd/phases/103/`)
**Objective**: Workflow cards show the last result image, the models used and a **compatibility badge** (runs / warnings / will fail), reusing `MainViewModel.preflight`. Add search and sort, and polish the template browser to the Phase 100 conventions.
**Plans** (planned 2026-09-29, `.gsd/phases/103/`):
- [x] 103.1 Compatibility, models, last result, lastUsedAt (Room 14 → 15) (wave 1)
- [x] 103.2 Workflow list UI: cards, search, sort, server section, import dialog (wave 2)
- [x] 103.3 Template browser polish; device check (wave 3)
**Depends on**: Phase 100; runs after Phase 102 (shares the Workflows top bar)

### Phase 104: Gallery and Media Viewer

**Status**: ✅ Done, device-verified 2026-09-28 (`.gsd/phases/104/`)
**Objective**: Align the gallery grid, the filters and saved-lists drawer, the detail viewer (zoom, swipe, video) and sharing with the Phase 100 conventions, and fix the rough edges found in the audit.
- Filters are simplified and applied locally (no wipe); saved lists are named filters.
- Delete becomes a confirmed "Remove from gallery" that survives syncs (Room 13 → 14).
- Full-screen viewer with Share, Save, Open in form, Info (prompt, seed, settings) and Remove; multi-select Share and Save.
**Plans**:
- [x] 104.1 Local filter and sort, Remove from gallery, media info, sync error (wave 1)
- [x] 104.2 Grid, filter sheet and saved lists (wave 2)
- [x] 104.3 Full-screen viewer, actions, URL builder; device check (wave 3)
**Depends on**: Phase 100

### Phase 105: Consistency Pass and Device Check

**Status**: ✅ Done, device-verified 2026-09-30 (`.gsd/phases/105/`)
**Objective**: Finish the milestone's must-haves.
- Apply the shared components to what's left: the issue list's ✖/⚠ marks, bold titles, the gallery dialogs and the image picker, stray dp values.
- Landscape: the four tabs move to a navigation rail; portrait keeps the bottom bar.
- Haptics: a click on Generate/Queue, a confirm on a finished run, and a "Vibration" switch in Settings.
- Check on the device: every screen in light and dark, portrait and landscape; a touch-target scan (48 dp); contrast; before/after screenshots against the Phase 100 audit.
**Plans**:
- [x] 105.1 Navigation rail in landscape; haptics and the Vibration switch (wave 1)
- [x] 105.2 Consistency sweep: status icons, typography, dialog titles, shapes, spacing tokens (wave 1)
- [x] 105.3 Full device pass (both themes, both orientations, touch-target scan); close the milestone's must-haves (wave 2)
**Depends on**: Phases 101–104

### Phase 106: Pinned Form Fields and Motion

**Status**: 📋 Planned 2026-10-01 (`.gsd/phases/106/`: CONTEXT and 3 plans); next `/execute 106`
**Objective**: The milestone's nice-to-haves, split out of Phase 105.
- A pin icon per form field; pinned fields show in a "Pinned" group under the prompt, per workflow (Room 15 → 16).
- Shared-element motion: the workflow card's thumbnail into the form's result preview, and the result preview into the full-screen viewer.
**Plans**:
- [ ] 106.1 FormLayout pinned group (tested); pinnedFields column, Room 15 → 16; targeted lastImageName write (wave 1)
- [ ] 106.2 Pinned group and pin icons in the form (wave 2)
- [ ] 106.3 Shared-element motion; device check; close (wave 3)
**Depends on**: Phase 105
