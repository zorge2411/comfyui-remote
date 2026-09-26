# Plan 99.2 Summary: App-scoped ModelDownloadRepository

**Completed:** 2026-09-26

## Delivered

**`domain/ModelDownloads.kt`** (`73ffc7e`)
- `ModelDownload` moved out of `MainViewModel` and gained `position`, `finished` and `progress`.
- `parse`, and `parseList`, which reads both the list route's array and the queue event's `{jobs}`.
- `upsert`, which replaces by id, or a not-yet-confirmed row for the same file.
- `summary(list, finishedInBatch)` gives "Downloading 2 of 3" with "name, 40%". `finishedSummary(batch)` gives "2 models downloaded, 1 failed, 1 cancelled".

**`data/ModelDownloadRepository.kt`** (`1f78b46`)
- Registered in `ComfyApplication`.
- Builds its API from the live connection's address (`ConnectionRepository.baseUrl`, new), not from saved settings, so it can't run before the address is known.
- On CONNECTED it refreshes the helper info (version, token) and the list. A 404 means absent; other errors keep the state.
- Collects the websocket `messages`: `remote_helper.queue` replaces the list, and `remote_helper.download` upserts one job.
- `finished` (a SharedFlow) fires once per change to done. `errors` (a SharedFlow) carries failed actions.
- Actions: `probe`, `download`, `downloadAll` (in order, skipping files with an active job), `cancel`, and `move`/`retry`/`clearFinished` (version 2 only).
- Requests the server refused keep their error row until the server lists that file.

**`MainViewModel`**
- The Phase 97 state and handlers are gone (−115 lines).
- Its public members now pass through to the repository:
  - `helperAvailable`, `helperHasToken`;
  - `modelDownloads`, a map with the active job winning;
  - `probeModel`, `downloadModel`, `downloadAllModels` (new), `cancelModelDownload`, `refreshModelHelper`.
- It collects `finished` to clear that folder's cache, refresh `/object_info` and bump `modelsVersion`.
- `missingModels` now waits for the saved settings.

## Verification

- `ModelDownloadsTest`: 5 tests.
- Full `testDebugUnitTest`: 197 tests, 0 failures. `assembleDebug` OK.
