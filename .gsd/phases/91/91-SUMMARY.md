# Phase 91 Summary: Pre-flight Compatibility Check

**Completed:** 2026-09-25, code done and tested locally (`--auto`). **Phone check pending:** no device was connected (`adb devices` empty).

## Delivered

**Plan 91.1: shared validator and pre-flight checker** (`c1bf7aa`, `cfbd587`, `715046f`)
- `ApiPromptValidator` moved from the test folder to `domain/` in the app. The corpus tests use it from there.
  - `graph` is optional: C6 only runs when a graph is given, which is only in the corpus tests.
  - `Options` presets:
    - `CORPUS`: unchanged behaviour.
    - `FORM`: file names are checked, upload inputs are skipped.
    - `queue(uploaded)`: everything is checked except the given values.
  - An upload input is any spec with a `*_upload: true` config key.
  - `Violation` gained `input`, `value` and `available` for the UI. `detail` and `toString()` are unchanged, so `known-failures.json` matching is unchanged.
- `PreflightIssue` / `PreflightNodeIssues` / `PreflightResult`:
  - kinds in display order: missing node type, missing model or file, invalid value, missing input, conversion problem;
  - `summary()` gives e.g. "2 missing node types, 1 missing model or file".
- `PreflightChecker.check(promptJson, objectInfo, options)`:
  - groups violations by node, with a `_meta.title (class_type)` heading;
  - sorts nodes by their most severe issue, then by id;
  - reports C2, C3, C6 and C7 as one neutral "didn't convert cleanly" line per node, with the raw violations logged under `PREFLIGHT_DEBUG`;
  - returns an empty result for malformed JSON.
- 10 tests in `PreflightCheckerTest`.

**Plan 91.2: pre-flight in the queue paths** (`12d1d68`, `c8d011a`)
- `WorkflowExecutionService.prepare` (patch + inject) and `queue` are split out. `prepareAndQueue` wraps them and sends the same JSON as before.
- `MainViewModel.refreshNodeMetadata()` is awaitable; `fetchNodeMetadata()` now uses it.
- **Generate:** after uploads and before the batch loop, the app checks the prepared prompt once with `Options.queue(...)`.
  - If there are issues, it re-checks against a freshly fetched `/object_info`.
  - If issues remain, it sets `pendingPreflight`, returns the status to IDLE, and queues nothing.
  - "Queue anyway" re-runs with `skipPreflight = true`.
- **Queue button:** the same gate at add time, with `Options.FORM`, since nothing has been uploaded yet.
- `formPreflight(workflow, inputs)`: the stored workflow with the current form inputs, checked against the cached `/object_info` without a refetch.
- No `/object_info` means no check, and the prompt is queued as before.
- The `missingNodes` log warning in `executeWorkflow` is removed.

**Plan 91.3: UI** (`493166c`)
- `ui/components/PreflightIssueList.kt` holds three composables:
  - `PreflightIssueList`, reusable for Phase 92;
  - `PreflightCard`, a collapsible "Server compatibility" card with the summary line;
  - `PreflightDialog`: "This workflow may fail", a scrollable list, and **Queue anyway** / **Cancel**.
- `DynamicFormScreen`:
  - the missing-node card from import is replaced by the live card, recomputed 300 ms after `inputs` or `nodeMetadata` change;
  - the dialog shows while `pendingPreflight` is set;
  - Generate and Queue are never disabled by the card.

## Where research and execution changed the plan

- **Form mode skips upload inputs** (RESEARCH §2). The stored workflow holds the author's example image.
- **Queue button gated at add time** (RESEARCH §3). The background local-queue runner is unchanged, because `QueueViewModel` has no `/object_info` source.
- **Execution finding:** images picked on the form are uploaded right away. Their server names go into `ImageInput.value` (with `localUri` set), not into `uploadedFilenames`. The queue-time skip list therefore also includes `ImageInput.value` wherever `localUri != null` (`MainViewModel.uploadedValues`). Without this, every freshly picked image would be flagged as missing.
- **Uploads after "Queue anyway":** `_inputImages` uploads are re-done when the user confirms. This is the same file and costs an extra upload. It isn't cached in this phase.
- **`missingNodes` column:** still filled at import and no longer shown anywhere. The only UI reader was the form card. There's no Room migration.

## Tests

- `gradlew.bat assembleDebug testDebugUnitTest`: 184 tests, 0 failures, 1 skipped (the opt-in all-templates report). Before this phase: 174.
- Corpus: `WorkflowCorpusTest` passes and `known-failures.json` is still `{}`.
  - `CORPUS` options keep every check identical, and the stock snapshot's file combos are empty. So the Phase 90 all-template numbers (563/572) can't move.
  - The all-template report wasn't rerun: its inputs (unzipped templates and a full `/object_info`) aren't on this machine.

## Phone check (pending)

Run `gradlew.bat installDebug` with the phone connected, then:
- a. Open a workflow whose checkpoint isn't on the server. The "Server compatibility" card shows the missing model, and expanding it lists it under the loader's title.
- b. Tap Generate. The dialog lists the issue.
  - Cancel: nothing is queued.
  - Generate again, then Queue anyway: the server's own error appears, as before.
- c. Pick an installed model. The card disappears, and Generate queues with no dialog.
- d. In an image workflow, pick an image from the gallery. There's no issue about the image.
- e. Optional: the Queue button with the missing model shows the same dialog.

## What's left

- Phase 92: map the server's `node_errors` onto `PreflightResult` and show them with `PreflightIssueList` / `PreflightDialog`-style UI.
- Phase 97: `image_ernie_image(_turbo)` show a "missing input" issue in pre-flight until it lands. That is correct behaviour.
- Possible follow-ups:
  - one-tap "use an installed model instead";
  - caching uploads across "Queue anyway";
  - dropping the `missingNodes` column.
