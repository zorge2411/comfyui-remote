## Phase 90 Verification

Checked against the code and fresh runs, not the summary's own claims (2026-09-25).

### Must-Haves
- [x] Frontend-only / virtual nodes (Reroute, PrimitiveNode, SetNode/GetNode, Note) convert correctly, with type-aware passthrough. VERIFIED:
  - `GraphToApiConverterVirtualNodeTest` has 23 tests, all passing;
  - C7 = 0 across all 572 templates (was 29).
- [x] `known-failures.json` is empty, and every corpus fixture passes. VERIFIED: the file is `{}` and `WorkflowCorpusTest` passes.
- [x] Bypass fallback drops the link when no input fits. VERIFIED: test `bypassed node with no input of the output type drops the link`; C3 = 0 across templates (was 4).
- [x] Unresolved-source templates accounted for (D-13): 3 fixed, 2 (ernie) filed as Phase 97. The rest are template problems. See `90-SUMMARY.md`.
- [x] Phone check. VERIFIED on the Fairphone 6: `phase90-test` (PrimitiveNode, Set/Get, Reroute, bypassed and muted nodes inside a subgraph template) ran successfully, run `68a3d1b5`; see `90-SUMMARY.md`.

### Commands
- `gradlew.bat testDebugUnitTest --rerun-tasks`: 174 tests, 0 failures, 1 skipped (the opt-in report).
- `AllTemplatesReportTest` with templates 0.1.95 and a full v0.37.2 `/object_info`: 563/572 clean.

### Verdict: PASS
