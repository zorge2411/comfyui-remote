# Plan 97.1 Summary: Keep Model Links at Import

**Completed:** 2026-09-26

## Delivered

- `domain/ModelSources.kt` (`6dfa916`):
  - `ModelSource(name, url, directory)`;
  - `extract` reads nodes, subgraph-definition nodes and the top-level `models` list, removes duplicates by folder/name, and skips entries without a name, URL or folder;
  - `usedBy` keeps the models whose name, or the base name of a subfolder path, is an input value in the API prompt;
  - `missing` matches the server's file lists on the full name or the base name; a folder with no list counts as unknown;
  - Gson `toJson` / `fromJson`.
- Storage (`996b798`):
  - `WorkflowEntity.modelSources` column.
  - Room version 12 with `MIGRATION_11_12`: adds `modelSources`, and adds `missingNodes` when `PRAGMA table_info` shows it is absent.
  - `importWorkflowInternal` stores the links the converted prompt uses. A workflow imported in the server's API format stores null.

## Verification

- `ModelSourceExtractorTest`: 7 tests, including the `flux_schnell` and `image_mage_flow_t2i_int8` (subgraph) fixtures.
- Full `testDebugUnitTest`: 192 tests, 0 failures. `assembleDebug` OK.
- Database upgrade on the device: pending, checked in Plan 97.3.
