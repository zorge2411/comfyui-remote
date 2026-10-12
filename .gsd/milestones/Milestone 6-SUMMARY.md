# Milestone: Milestone 6: Stability, then Remaining Compatibility Features

## Completed: 2026-10-12

**Goal:** failures are visible and recoverable, regressions are caught automatically, and the repo is clean; then the remaining compatibility features, ordered by value.

> **Merge note.** This milestone was built on its own branch while `master` developed the UI/UX overhaul (also numbered Milestone 5, Phases 100–106). On merging, this work was renumbered to Milestone 6 / Phases 107–112, Room migration 11 → 12 became 17 → 18, and where the two overlapped `master`'s version was kept: the compatibility badge (this milestone's Phase 110, now `master`'s Phase 103) and the gallery sync-error banner. Phase 110's logic and screens were therefore dropped in the merge; its research remains in `110/`.

## Deliverables

- ✅ Import and sync failures are shown to the user, with the failing step; a graph-format workflow that can't be converted is never stored (it used to be saved as raw JSON that could never run).
- ✅ Re-importing the same template or server workflow asks (Open / Copy / Replace) instead of duplicating (Room 11 → 12, `sourceKey`).
- ✅ Local-queue items can't get stuck: items interrupted by an app kill are recovered, failed items can be retried, every failure has a reason. (Re-scoped from "re-check at run time": Phase 92 already stores the server's reasons.)
- ✅ GitHub Actions runs the unit tests and builds the debug APK on every push and pull request; the APK is a downloadable artifact.
- ✅ Repo hygiene: 9 tracked log files removed and ignored; README "Building and testing".
- ✅ Nice-to-haves: (compatibility badge: delivered by master's Phase 103 instead); dynamic-combo option switching in the form; colour and bounding-box fields; PrimitiveNode text replacement.
- ➖ Dropped with evidence: CURVE / VIDEO_EDIT editors and legacy Group Nodes (1 / 0 / 0 of 572 templates need them; their values already pass through unchanged).

## Phases Completed

| Phase | What | Verified |
|---|---|---|
| 97 | Visible import and sync failures | CI; device check pending |
| 98 | Duplicate imports and queue recovery (Room 11 → 12) | CI; device check pending |
| 99 | CI and repo hygiene | CI green (run 1) |
| 100 | Compatibility badge on the workflow list | CI; device check pending |
| 101 | Switch dynamic options in the form | CI; device check pending |
| 102 | Custom widget kinds and PrimitiveNode text replace | CI; device check pending |

## Metrics

- Duration: 2026-10-12 (one day)
- App/test/CI changes: 35 files, +1,819/−111 lines (excluding planning docs)
- JVM test suite: 100+ → 190 tests, now run on every push
- Corpus unchanged: 565 of 572 official templates convert to clean prompts; `known-failures.json` still `{}`

## Lessons Learned

- **CI paid for itself in two pushes.** The first run was the first full compile of the Android code in the cloud; it confirmed Phases 107 and 108 and caught a real compile error in Phase 110 (an enum used as a value) that the plain-JVM mirror cannot see.
- **Re-check the roadmap against the evidence before building.** Phases 108 and 112 were narrowed after research: the queue re-check was redundant after Phase 92, and the converter already handled the custom widget kinds. Measuring the 572 templates first avoided building editors nothing uses.
- **Property initialisation order matters in a large ViewModel.** `compatibility` had to be declared after `nodeMetadata` it combines.
- **Dates in converted workflows are frozen at import.** Text replacement that depends on the current date can't be right until replacement moves to run time.

## Known Gaps / Pending

- **Device checks not yet run** for Phases 107, 108, 110, 111 and 112 (steps are in each phase summary under `milestones/Milestone 6/<N>/`).
- `%date:…%` in a replace-enabled PrimitiveNode shows the import day; replacement only searches top-level nodes; the frontend's `formatDate` token rules were not verified against its source.
- About 40 tracked agent-tooling files (`.agent/`, `.gemini/`, `.jules/`, `.snapshots/`) and `app/release/` remain in the repo.
- Gradle 8.9 is flagged out of date by the CI runner.

## Candidates for Milestone 6

- Run-time (queue-time) text replacement so dates are right.
- CURVE / VIDEO_EDIT editors if templates start using them.
- Lint / detekt gate in CI; Gradle and AGP upgrade.
- Compatibility filter/sort on the workflow list; badges for server workflows.
