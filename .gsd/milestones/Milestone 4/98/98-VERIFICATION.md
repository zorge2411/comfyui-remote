## Phase 98 Verification

### Must-Haves

- [x] **A workflow naming an input file the server doesn't have gets a pre-flight error, even when the server's list is empty.** Unit tests, and on the device the Real-ESRGAN dialog named `gan_input.mp4`.
- [x] **No new warnings for non-file combos with empty lists.** Unit test `a non-file value on an empty non-upload list is not reported`.
- [x] **Test-suite results unchanged.** `WorkflowCorpusTest` passes and `known-failures.json` is `{}`.

### Commands

- `gradlew.bat testDebugUnitTest assembleDebug installDebug`: 200 tests, 0 failures.

### Verdict: PASS
