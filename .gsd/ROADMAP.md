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

- [ ] Shared UI building blocks used on every screen: top bar, section header, card, dialog, and empty, loading and error states (Phases 100, 105)
- [ ] The workflow form shows the prompt and key settings first; everything else is grouped and collapsible; status cards (missing nodes or models, errors, progress) sit in one place (Phase 101)
- [ ] Clear navigation: a tab bar with defined roles for Queue, History and Gallery, and connection state visible without a dedicated screen (Phase 102)
- [ ] Workflow list cards show what a workflow is and whether it will run: a compatibility badge (carried over from Milestone 4) and the last result (Phase 103)
- [ ] Gallery and media viewer follow the same conventions (Phase 104)
- [ ] No screen breaks in landscape; dark mode and touch targets checked on the device (Phases 100, 105). Connection and Settings are fixed; status-bar contrast and insets are fixed app-wide

## Nice-to-Haves

- [ ] Pin favourite fields per workflow to the top of the form
- [ ] Shared-element / motion transitions between the list, form and result
- [ ] Haptic feedback on Generate and on completion

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

**Status**: 📋 Planned (`.gsd/phases/101/`: 3 plans)
**Objective**: Make `DynamicFormScreen` (about 1,000 lines) quick to use.
- The positive prompt stays first (Phase 82).
- Key settings (seed, width and height, steps, cfg, sampler and scheduler, model) go in a main group.
- The remaining inputs are grouped per node and collapsed by default.
- The missing-nodes and missing-models cards, server errors and progress are merged into one status area.
- The result preview and Generate/Queue actions are easy to reach.
**Plans**:
- [ ] 101.1 FormLayout (main settings and groups) and seed policy (Random/Fixed) (wave 1)
- [ ] 101.2 Remembered form values per workflow, Room 12 → 13 (wave 1)
- [ ] 101.3 Form UI: fixed top and bottom bars, status area with the result, collapsed node sections; device check (wave 2)
**Depends on**: Phase 100

### Phase 102: Navigation and Structure

**Status**: ⬜ Not Started
**Objective**: Rework the tab bar and screen roles.
- Connection becomes a status indicator plus settings, not a tab of its own.
- Define what Queue, History and Gallery are each for, and remove the overlap.
- Decide where Templates and Settings live.
- Make back behaviour consistent.
**Depends on**: Phase 100

### Phase 103: Workflow List and Templates

**Status**: ⬜ Not Started
**Objective**: Workflow cards show the last result image, the models used and a **compatibility badge** (runs / warnings / will fail), reusing `MainViewModel.preflight`. Add search and sort, and polish the template browser to the Phase 100 conventions.
**Depends on**: Phase 100

### Phase 104: Gallery and Media Viewer

**Status**: ⬜ Not Started
**Objective**: Align the gallery grid, the filters and saved-lists drawer, the detail viewer (zoom, swipe, video) and sharing with the Phase 100 conventions, and fix the rough edges found in the audit.
**Depends on**: Phase 100

### Phase 105: Consistency Pass and Device Check

**Status**: ⬜ Not Started
**Objective**: Finish the milestone.
- Apply the shared components to any screen still left: Settings, Queue, History and the dialogs.
- Check on the device: dark mode, touch targets (48 dp), contrast, and landscape on every screen.
- Before/after screenshots.
**Depends on**: Phases 101–104
