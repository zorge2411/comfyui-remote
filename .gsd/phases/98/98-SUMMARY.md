# Phase 98 Summary: Duplicate Imports and Queue Recovery

**Status**: Code done; device check pending. Not compiled in the cloud container (no Android SDK): needs a local `gradlew.bat testDebugUnitTest assembleDebug installDebug`.

## Delivered

- `domain/WorkflowDuplicates.kt` (`find`, `uniqueName`) and `domain/QueueRecovery.kt` (stale EXECUTING, retry, delete rules, messages); 9 JVM tests pass.
- Room 11 → 12: `workflows.sourceKey` (explicit `MIGRATION_11_12`, so existing data is kept).
- `MainViewModel`: imports carry a `sourceKey` (template name / server path); a match raises `duplicatePrompt`; `resolveDuplicate(OPEN | COPY | REPLACE)`. Replace keeps id, name, createdAt, lastImageName. "Save as Template" uses `asNewCopy = true` (no prompt, "X (2)" naming).
- `ui/components/DuplicateImportDialog.kt` shown on the Workflows and Templates screens.
- `QueueViewModel`: `startQueue` fails stale EXECUTING items with "Interrupted…"; `retryItem`; "No server saved" reason. `QueueScreen`: Retry on failed cards; stale EXECUTING items are deletable.

## Scope change
The run-time pre-flight re-check was dropped: Phase 92 already stores the server's rejection reasons on the item.

## Notes
- Workflows imported before this phase have no `sourceKey`; they match templates by source + title, and server workflows are not matched until re-imported once (the replace then records the key).
- Stale items are only marked FAILED when the queue starts; until then they already show a delete button.

## Device check
1. Import a template twice: dialog; Open / Copy ("X (2)") / Replace each work.
2. Same for a server workflow.
3. "Save as Template" from an unsaved edit: no prompt.
4. Install over the old build: workflows intact.
5. Queue an item, force-stop mid-run, reopen: item is deletable; start the queue: it shows "Interrupted…", Retry re-runs it.
6. A failed item: Retry runs it again.
