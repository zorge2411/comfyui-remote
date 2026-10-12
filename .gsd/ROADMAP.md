# ROADMAP.md

> **Current Milestone**: Milestone 5 (Planned)
> **Goal**: Stability first: failures are visible and recoverable, regressions are caught automatically, and the repo is clean. Then the remaining compatibility features, ordered by value.
> **Previous**: Milestone 4 archived in `.gsd/milestones/Milestone 4/` (summary: `Milestone 4-SUMMARY.md`)

## Must-Haves

- [x] Import and sync failures are shown to the user (Phase 97; device check pending)
- [x] Importing the same template or workflow twice doesn't silently create duplicates (Phase 98; device check pending)
- [x] Local-queue items can't get stuck: interrupted items recovered, failed items retryable, every failure has a reason
- [x] Unit tests and the debug build run automatically on every push (GitHub Actions)
- [x] Repo hygiene: stray build/test log files removed from version control, with a short README section on running the tests

## Nice-to-Haves (lower priority, by value)

- [x] Compatibility badge on the workflow list (Phase 100; device check pending) (runs / warnings / will fail), reusing the pre-flight check
- [x] Switch dynamic-combo options (e.g. video format) in the form (Phase 101; device check pending)
- [ ] Custom widget kinds: COLOR, BOUNDING_BOX, VIDEO_EDIT, CURVE
- [ ] PrimitiveNode text replacement (`%date%`-style); legacy Group Nodes

## Phases

### Phase 97: Visible Import and Sync Failures

**Status**: ✅ Done (code); device check pending (`.gsd/phases/97/97-SUMMARY.md`)
**Objective**: Make every import and sync failure visible. `importWorkflowInternal`, `importServerWorkflow` and `fetchServerWorkflows` catch exceptions and only log them, so a bad workflow or an unreachable server looks like nothing happened. Report them through the existing error UI (Workflows screen), with the failing step in the message (fetching, converting, saving). Research found the worse problem: when conversion fails or the server's node list is missing, the raw graph JSON is saved as the workflow, so it looks imported but can never run. Never store an unconverted graph workflow. Also report server workflow list and history sync failures.
**Discovered**: Milestone 4 review (2026-10-12): 6 `WORKFLOW_ERROR` log-only catch blocks in `MainViewModel`

### Phase 98: Duplicate Imports and Queue Recovery

**Status**: ✅ Done (code); device check pending (`.gsd/phases/98/98-SUMMARY.md`)
**Objective**: (A) Importing the same template or server workflow twice asks (Open existing / Import another copy / Replace) instead of silently duplicating; needs a `sourceKey` column and Room migration 11 → 12. (B) Changed from "re-run the pre-flight when a queue item runs": Phase 92 already stores the server's rejection reasons, so that would be redundant. Instead fix the real gaps: items stuck in EXECUTING after an app kill (and undeletable), no Retry for failed items, and a failure with no reason when no server is saved.
**Depends on**: Phase 97

### Phase 99: CI and Repo Hygiene

**Status**: ✅ Done (`.gsd/phases/99/99-SUMMARY.md`)
**Objective**: Add a GitHub Actions workflow (JDK 17, `testDebugUnitTest` + `assembleDebug`, test reports on failure, debug APK as an artifact) on push and pull request; the repo has no `.github/`. Remove the 9 tracked log files in the repo root and ignore that naming; add a "Building and testing" section to the README, including the corpus and `known-failures.json`. The first CI run can't be tried in the cloud container, so execution includes reading the run and fixing it.

### Phase 100: Compatibility Badge on the Workflow List

**Status**: ✅ Done (code); device check pending (`.gsd/phases/100/100-SUMMARY.md`)
**Objective**: Show ready / warnings / will fail (with the first reason) on each workflow card, from `PromptValidator` on the stored workflow against the connected server's node list. Computed off the main thread, cached per workflow, refreshed on reconnect, hidden while disconnected. File inputs that the form replaces at run time (image, video, audio, mask) are not flagged. A corpus test guards against good workflows turning red.
**Depends on**: Phase 98

### Phase 101: Switch Dynamic Options in the Form

**Status**: ✅ Done (code); device check pending (`.gsd/phases/101/101-SUMMARY.md`)
**Objective**: Let the user pick another option of a dynamic combo (e.g. `SaveVideo.format`). Switching rewrites the per-run prompt: delete the old option's `key.*` inputs, add the new option's sub-inputs recursively with carried-over values where the old and new option share a sub-input and spec defaults otherwise; options that need a required socket aren't offered. The form keeps a working copy of the JSON (the stored workflow is unchanged). Needs the node list, otherwise the combo stays hidden as in Phase 94.
**Depends on**: Phase 94

### Phase 102: Custom Widget Kinds and PrimitiveNode Text Replace

**Status**: ⬜ Not Started
**Objective**: Handle widget kinds with their own save rules (COLOR, BOUNDING_BOX, VIDEO_EDIT, CURVE), driven by templates that use them. Apply PrimitiveNode "Run widget replace on values" text replacement. Legacy Group Nodes only if templates in the wild need them.
