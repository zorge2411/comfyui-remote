# Phase 108: Duplicate Imports and Queue Recovery - Context

**Gathered:** 2026-10-12
**Status:** Ready for execution

<domain>
## Phase Boundary

(A) Importing the same template or server workflow a second time asks instead of silently creating a copy. (B) Local-queue items can no longer get stuck: interrupted items are recovered, failed items can be retried, and every failure has a reason. Compatibility badges are Phase 110.

</domain>

<research>
## Findings

**A. Duplicates.** `WorkflowEntity` has no unique key and `WorkflowDao.insert` uses REPLACE on the auto-id, so every import adds a row. Entry points: `importTemplate` (template name), `importServerWorkflow` (server file `fullpath`), file/paste import, and DynamicFormScreen's "Save as Template" (`workflow.id == 0L`, intentionally a new copy of an unsaved edit).

**B. Queue.** The original goal (re-run the pre-flight when an item runs) is mostly redundant after Phase 92: the server's own rejection is already stored on the item with every node and reason. The real gaps in `QueueViewModel`/`QueueScreen`:
- An item left `EXECUTING` by an app kill or crash stays that way: spinner never matches `currentItemId`, and the delete button is hidden for `EXECUTING`, so it can't be removed.
- `FAILED` items have no Retry; the user must rebuild the job.
- `api == null` (no saved server) fails the item with no reason.
</research>

<decisions>
## Decisions

- **D-01** Add nullable `sourceKey` to `WorkflowEntity` (template name, server `fullpath`; null for file/paste and unsaved edits). Room 11 → 12 with an explicit `ALTER TABLE workflows ADD COLUMN sourceKey TEXT` migration (the DB uses destructive fallback, so no migration would wipe user data).
- **D-02** Pure-Kotlin `WorkflowDuplicates`: `find(existing, source, sourceKey, name)` matches `sourceKey` when given, otherwise `source` + `name`; `uniqueName(name, existingNames)` gives "X (2)", "X (3)".
- **D-03** On a duplicate, a dialog offers: Open existing / Import another copy / Replace. Replace keeps id, createdAt and lastImageName. "Save as Template" never prompts (always a new copy, uniquely named).
- **D-04** Queue: at `startQueue`, items left `EXECUTING` become `FAILED` with "Interrupted (the app closed while this was running)". `FAILED` items get Retry (back to `PENDING`, error cleared). `api == null` fails with "No server saved. Set the server address first."
- **D-05** No run-time pre-flight in the queue; explain why in the summary.
</decisions>
