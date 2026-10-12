# Plan 103.1 Summary: Compatibility, Models, Last Result and Last Used

**Completed:** 2026-09-29 (commit `b12f36b`).

## Delivered

- **`domain/WorkflowCompatibility.check(prompt, objectInfo)`** returns `Compatibility.Ready | Warnings(issues) | WillFail(issues)`.
  - It wraps `PromptValidator.validate`.
  - An ERROR `VALUE_NOT_IN_LIST` on an `image` input is downgraded to a warning (D-01).
  - `WillFail` lists errors first.
- **`domain/WorkflowModels.of(promptJson)`** returns `List<ModelRef(folder, baseName)>`.
  - It matches the same extensions as `ModelSources` (gguf, sft, and so on), with the folder from `ModelSources.folderFor`.
  - Distinct, in prompt order.
- **`domain/WorkflowListView.apply(workflows, modelsById, query, sort)`:**
  - the search matches the name or a model, ignoring case;
  - `WorkflowSort` is LAST_USED (`lastUsedAt ?: createdAt`), NAME (case-insensitive, then id) or NEWEST.
- **Room 14 → 15:** `workflows.lastUsedAt` (`MIGRATION_14_15`).
  - `WorkflowDao.markUsed(id, time)` is a targeted UPDATE.
  - `selectWorkflow` calls it for stored workflows (`id != 0`).
- **Media:**
  - `GeneratedMediaDao.getListingsByFileNames(names)` returns visible rows only;
  - `MediaRepository.latestListingsByFileNames` picks the newest per name, queried in chunks of 500.
- **View model:**
  - `workflowModels`, memoised on `jsonContent`;
  - `workflowStatus`: `combine(allWorkflows, _nodeMetadata)` on Default, memoised on json + savedInputs + metadata instance; empty without metadata;
  - `lastResults`, built from `lastImageName` and refreshed when media changes;
  - `workflowSort` and `setWorkflowSort`, persisted in the settings DataStore (`workflow_sort`).
- **Shared prompt builder:** `buildCheckPrompt(workflow, inputs)` is used by `preflight` (behaviour unchanged) and by the badge.
- **`selectWorkflow`:** after looking up the stored row, the preview URL includes the result's subfolder and type.

## Deviations

- **Commits:** the three tasks were committed together in one commit.
- **Model regex:** `WorkflowModels` has its own copy of the model-file regex, because the one in `ModelSources` is private.

## Verification

- `testDebugUnitTest`: 250 tests, 0 failures.
- New tests: `WorkflowCompatibilityTest` 5, `WorkflowModelsTest` 3, `WorkflowListViewTest` 4.
- `assembleDebug` OK.
- The migration is checked on the device in 103.3.
