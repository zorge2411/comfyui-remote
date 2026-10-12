# Milestone: Milestone 5: UI/UX Overhaul

## Completed: 2026-10-01

**Goal:** the app is easier to use and consistent across screens. The workflow form puts the important fields first, and every screen follows the same Material 3 conventions (phone portrait; landscape must not break).

## Deliverables

- ✅ **Shared UI building blocks** used on every screen: top bar, section header, card, dialogs, empty/loading/error states, status banners, spacing tokens. A UI spec and device audit came first (Phases 100, 105).
- ✅ **Workflow form:**
  - the prompt and key settings come first;
  - everything else is grouped per node and collapsed;
  - one status area for missing nodes or models, errors, progress and the result;
  - seed Random/Fixed;
  - values remembered per workflow (Phase 101).
- ✅ **Navigation:**
  - four labelled tabs (Workflows, Queue, Gallery, Settings); History removed;
  - a connection chip and auto-connect;
  - the server's own queue in Queue;
  - a "Not connected" banner, with server actions disabled offline (Phase 102).
- ✅ **Workflow cards:**
  - last-result thumbnail, models used, and a compatibility badge (Ready / warnings / Won't run, with the issue list);
  - search and sort;
  - the template browser on the shared conventions (Phase 103; the badge was carried over from Milestone 4).
- ✅ **Gallery and viewer:**
  - local filters and saved lists;
  - "Remove from gallery" that survives syncs;
  - a full-screen viewer with Share, Save, Open in form and Info (Phase 104).
- ✅ **Device checks:**
  - no screen breaks in landscape; a navigation rail in landscape;
  - dark mode, contrast and 48 dp touch targets checked on the phone;
  - status-bar contrast and edge-to-edge insets fixed app-wide (Phases 100, 105).
- ✅ **Nice-to-have, haptics:** on Generate/Queue and on a finished run, with a Settings switch (Phase 105).
- ✅ **Nice-to-have, pinned form fields:** per workflow, in a "Pinned" group under the prompt (Phase 106).
- ✅ **Nice-to-have, shared-element motion:** workflow card → form preview → full-screen viewer (Phase 106).

## Phases Completed

| Phase | Name | Done | Device check |
|---|---|---|---|
| 100 | UI audit and shared components | 2026-09-28 | ✅ 2026-09-28 |
| 101 | Workflow form declutter | 2026-09-28 | ✅ 2026-09-28 |
| 102 | Navigation and structure | 2026-09-30 | ✅ 2026-09-30 (Queue Remove/Stop live test dropped) |
| 103 | Workflow list and templates | 2026-09-30 | ✅ 2026-09-30 |
| 104 | Gallery and media viewer | 2026-09-28 | ✅ 2026-09-28 |
| 105 | Consistency pass and device check | 2026-10-01 | ✅ 2026-09-30 |
| 106 | Pinned form fields and motion | 2026-10-01 | ✅ 2026-10-01 |

Phase folders are archived in `Milestone 5/`, with the milestone's `ROADMAP.md`.

## Metrics

- **Commits:** 56, from `6e1d806` (Milestone 4 archived, 2026-09-27) to the Milestone 5 archive commit.
- **App and server code** (`app/src/main`, `server/`): 75 files, +5,732 / −3,690 lines. Much of the deletion is the old form, History screen and dialogs being replaced.
- **Unit tests** (`app/src/test`): 15 files, +793 lines. JVM tests went from 200 to 255.
- **Duration:** 5 days (2026-09-27 to 2026-10-01).
- **Room database:** version 12 → 16, migrated in place on the device each time:
  - `savedInputs` (101);
  - `generated_media.hidden` (104);
  - `lastUsedAt` (103);
  - `pinnedFields` (106).

## Lessons Learned

- **Audit first, then build.** The Phase 100 device audit (screenshots of every screen, light and dark) and a short UI spec gave every later phase a checklist, and gave Phase 105 the "before" side of its before/after.
- **Device checks find what tests can't.** Each phase's check found something:
  - the `Won't run` false alarm for Ollama model tags;
  - the server workflow list fetched only on `connect()`;
  - landscape layouts with no room for content;
  - haptics silenced by the system touch-feedback switch.
- **Read the platform's own diagnostics.** `dumpsys vibrator_manager` showed `ignored_for_settings` with usage TOUCH, which explained the "nothing felt" result in one step.
- **Whole-row REPLACE writes from a stale copy undo other changes.** Targeted `UPDATE … WHERE id` writes (`markUsed`, `setPinned`, `setLastImage`) fixed a class of races found while planning Phase 106.
- **Plan phases that share screens in order.** Phases 102 and 103 were planned in parallel and both changed the Workflows top bar; 103 was rebased onto 102 before it ran.
- **Keep the device check approachable.** Screen locks, notification shades and other apps' dialogs interrupted the checks. Asking the user before each step that needs them (rotation, unlock, runs, aeroplane mode) kept them on track.
- **Tooling:** the auto-mode permission check for shell commands failed for stretches. Edits through the file tools kept work moving, with builds and commits batched once commands worked again.

## Carried Over

- **Not device-checked:**
  - a live Remove/Stop on the server queue (dropped);
  - the templates reload-error banner in aeroplane mode (skipped);
  - the Phase 106 pin row in landscape.
- **From Milestone 4 (optional):**
  - queue controls hidden on a helper v1 server;
  - a Gemini, Grok or SaveVideo run (API nodes cost money).
- **From Milestone 3 (optional):** the Phase 80 themed icon under Material You.
- **Ideas not started:** reordering pinned fields; tab-switch animations.
