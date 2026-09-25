## Phase 91 Verification

Checked against the code and fresh runs, not the summary's own claims (2026-09-25).

### Must-Haves
- [x] Before queueing, the converted prompt is checked against the server's `/object_info`. VERIFIED:
  - `MainViewModel.executeWorkflow` calls `preflightIssues(prepare(...), Options.queue(...))` before `repeat(batchCount)`;
  - `addToQueue` calls it with `Options.FORM`.
- [x] Missing node types, missing required inputs and invalid combo values (including model files) are reported. VERIFIED: `PreflightCheckerTest`:
  - missing model;
  - unknown class type;
  - missing required input;
  - invalid value;
  - upload skips.
  All 10 pass.
- [x] Shown in the app, replacing the missing-node list. VERIFIED:
  - `DynamicFormScreen.kt` no longer references `missingNodes`;
  - it renders `PreflightCard` from `formPreflight`, and `PreflightDialog` from `pendingPreflight`;
  - `assembleDebug` succeeds.
- [x] No false warnings for frontend-only nodes or muted/bypassed subgraph instances. VERIFIED:
  - the converter doesn't send these nodes (Phase 90, C7 = 0 in the corpus);
  - pre-flight validates the sent prompt only, with no graph, so there's no C6;
  - test `no graph means no C6`.
- [x] Corpus unchanged. VERIFIED: `WorkflowCorpusTest` passes, `known-failures.json` is `{}`, and `ApiPromptValidatorTest` (18) passes.
- [ ] Phone check. PENDING: no device connected. Steps a–e are in `91-SUMMARY.md`.

### Commands
- `gradlew.bat assembleDebug testDebugUnitTest`: 184 tests, 0 failures, 1 skipped.

### Verdict: PASS (code); device check pending
