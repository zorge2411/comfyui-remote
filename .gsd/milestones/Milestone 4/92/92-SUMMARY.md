# Phase 92 Summary: Full Server Validation Error Reporting

**Completed:** 2026-09-26. Verified locally and on the phone.

## Delivered

**Plan 92.1: `ServerErrorReport`** (`ff4c6ed`)
- A pure-Kotlin parser for:
  - `/prompt` error bodies: every node in `node_errors` with every reason, `extra_info.input_name`, and titles from the sent prompt's `_meta.title`;
  - errors without `node_errors`: the summary plus `details`;
  - unparseable bodies: the HTTP code plus the body, cut at 500 characters;
  - 200 responses that still list `node_errors`, i.e. skipped outputs;
  - websocket `execution_error`: the node plus "ExceptionType: message".
- `format()` gives readable per-node text.
- 7 tests using ComfyUI v0.37.2 payload shapes.

**Plan 92.2: wiring** (this commit)
- **Generate:** the `HttpException` catch shows `ServerErrorReport` text. This replaces the first-error-only message.
- **`PromptResponse.node_errors`:** a partial acceptance sets `serverWarning`, and the form shows a dismissible "⚠️ Some outputs were skipped" card.
- **`execution_error`:** now sets `errorMessage` to the failing node (title from `_executionCache`) and the exception.
- **`ErrorCard`:** optional `onCopy`. With it, the whole message scrolls (max 240dp) and a Copy button appears. Existing callers are unchanged. The form's execution error card uses it.
- **Local queue:**
  - `LocalQueueItem.errorMessage`, with Room **10 → 11** (`MIGRATION_10_11`: `ALTER TABLE local_queue ADD COLUMN errorMessage TEXT`), registered next to the existing migrations. The database also uses `fallbackToDestructiveMigration`, so the migration is essential.
  - `updateStatusAndError`: EXECUTING clears the reason; FAILED stores the report or the exception message.
  - `QueueScreen` shows the reason under FAILED items: 3 lines, tap to expand.

## Verification

- Plain-JVM suite passes, including `ServerErrorReportTest` (7).
- **Not compiled in the cloud session:** `MainViewModel`, `QueueViewModel`, `ComfyApiService`, the Room files, `ErrorCard`, `DynamicFormScreen` and `QueueScreen`.
- Pending (user): `gradlew.bat testDebugUnitTest`, `assembleDebug`, `installDebug`, then the device checks:
  - (a) Queue anyway on a prompt with two broken nodes → the card lists both nodes and every reason; Copy works;
  - (b) a runtime failure → the card names the node and the exception;
  - (c) a failing local-queue item → its reason shows in the Queue screen;
  - (d) the existing database upgrades from 10 to 11 without losing workflows or queue items.

## Milestone 4

All five must-haves are now delivered in code. Phases 90, 91, 92, 95 and 96 still await device checks.

## Device check (2026-09-26)

Passed on the Fairphone 6 over wireless adb. Build from `master` `a9c45ec` (the agent's branch plus the local-master merge): `testDebugUnitTest` 185/185, `assembleDebug` and `installDebug` OK. The database upgraded from 10 to 11 in place, with no data loss (`user_version` 11).

- Queue anyway on the missing-model workflow: the server returned `value_not_in_list` for 3 nodes, `CLIPLoader` 63, `UNETLoader` 67 and `VAELoader` 64. The card listed all three, and Copy worked.
- A local-queue failure reason showed in the Queue screen.
