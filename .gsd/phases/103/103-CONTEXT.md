# Phase 103 Context: Workflow List and Templates

**Gathered:** 2026-09-29. Milestone 5 (UI/UX Overhaul).
- Conventions: `.gsd/phases/100/100-UI-SPEC.md`.
- Audit rows: `100-AUDIT.md` (Workflows L27, Import dialog L28, Templates L29; app-wide A3, A6, A7).

## User decisions (2026-09-29)

1. **Compatibility badge: automatic while connected.**
   - It checks every workflow against the cached `/object_info`, with no extra network calls.
   - It covers missing nodes, bad links and missing model files.
   - With no metadata (offline), no badge is shown.
2. **Sort options:** Last used (the default), Name and Newest. This adds `lastUsedAt` (Room 14 → 15).
3. **Card:** a list row.
   - An `AppCard` with the last-result thumbnail on the left.
   - On the right: name, model chips and the badge.
   - One overflow menu (Rename, Delete). Tapping the card opens the form.
4. **Server workflows:** a collapsible `SectionHeader` section below the local ones, collapsed by default, filtered by the same search.

## Code survey (read-only, 2026-09-29)

Paths are relative to `app/src/main/java/com/example/comfyui_remote/`.

### `ui/WorkflowListScreen.kt` (339 lines)

- **Top bar:** a raw `TopAppBar` "Workflows" (`:76-89`) with two actions: Browse Templates (GridView) and Sync from Server (Refresh, which calls `syncHistory()` and `fetchServerWorkflows()`).
- **FAB:** Import (`:91-95`). The list has no bottom padding, so the FAB covers the last card.
- **Empty state:** an `EmptyState` (`:98-105`), shown only when both the local and server lists are empty.
- **Lists:** "Local Workflows" and "Server Workflows (Userdata)" headers are ad-hoc `Text`s (`:110`, `:132`).
- **Loading scrim:** hand-built with `Color.Black` (`:157-178`).
- **Dialogs:**
  - Rename is a raw `AlertDialog` (`:194-221`);
  - Delete is a raw `AlertDialog` (`:223-247`) that duplicates `ConfirmDialog`.
- **`WorkflowItem` (`:250-304`):**
  - a raw `Card` with `RoundedCornerShape(8.dp)`;
  - "Created: date";
  - a "📦 baseModelName" chip;
  - two trailing icons (Edit, Delete).
- **Phase 100 components:** only `EmptyState` is used.
- **Import dialog:** `ui/ImportWorkflowDialog.kt` (185 lines) uses filled `TextField`s (`:103`, `:111`) and an outdated "API Format" title and note.

### Workflow data

- **`data/WorkflowEntity.kt`** (table `workflows`) has these fields:
  - `id`, `name`, `jsonContent`, `createdAt`;
  - `lastImageName` (the file name only);
  - `baseModelName` and `baseModels`;
  - `source`, `formatVersion`, `missingNodes`, `modelSources`, `savedInputs`.
  - There is no `lastUsedAt`.
- **DAO:** in `data/AppDatabase.kt:15-28`. `getAll()` orders by `createdAt DESC`.
  - Every write is `insert` (REPLACE). That covers renames, form-value saves (every 500 ms), `lastImageName` and model links.
- **Database:** version 14. The latest migration is `MIGRATION_13_14`, and `fallbackToDestructiveMigration()` is set.
- **ViewModel:** `MainViewModel.allWorkflows` (`:283`) is a raw Flow.

### Pre-flight (`MainViewModel.kt:593-619`)

- **What it does:**
  - `preflight(workflow, inputs)` fetches `/object_info` only if `_nodeMetadata` is null.
  - It calls `workflowExecutionService.buildPrompt(json, emptyMap(), inputs)`.
  - Then it runs `PromptValidator.validate(prompt, metadata)`.
- **Form-state coupling:** it reads `_inputImages` (form state) to drop `VALUE_NOT_IN_LIST` on pending uploads, so it can't be called from the list as it stands.
- **Issue kinds:**
  - ERROR: MISSING_NODE_TYPE, NO_OUTPUT_NODE, BAD_LINK, TYPE_MISMATCH, REQUIRED_INPUT_MISSING, and a missing server file (VALUE_NOT_IN_LIST).
  - WARNING: INVALID_VALUE_TYPE, OUT_OF_RANGE, and other VALUE_NOT_IN_LIST.
- **Metadata caching:** `_nodeMetadata` is filled on connect, on the transition to CONNECTED, and after a model download.
- **Cost:** checking N workflows with metadata cached is CPU-only.
- **Issues dialog:** the form shows issues in a private `PreflightDialog` (`ui/DynamicFormScreen.kt:462`).
- **Tests:** `PromptValidatorTest` (13).

### Models used

- **At import:** `WorkflowNormalizationService.extractModels` (`:96-121`) fills `baseModels`. It misses gguf, sft, pt, pth and bin.
- **`domain/ModelSources.kt`** has the helpers:
  - `MODEL_FILE` (`:86`) matches all of those extensions;
  - `folderFor(classType, input)` (`:122-141`) maps an input to checkpoints, diffusion_models, loras, vae, text_encoders, controlnet and so on.
- **Tests:** `PromptModelsTest` (8).

### Last result

- **Media names are unreliable.** `GeneratedMediaEntity.workflowName` is free text:
  - the selected workflow's name at the time of the WS "executed" event (`MainViewModel.kt:1174`);
  - or it comes from history pnginfo;
  - renames don't update it.
- **`WorkflowEntity.lastImageName`** is set for the selected stored workflow when a result arrives (`:1195-1199`).
- **Media lookup:** `GeneratedMediaDao.getLatestByFilename(filename)` (`:49-50`) exists. There is no per-workflow query.
- **URL bug:** `selectWorkflow` (`:365-387`) builds the preview URL with `MediaUrls.view(base, lastImageName)`, with no subfolder or type, so subfoldered outputs break.
- **URL builder:** `GeneratedMediaListing.constructUrl` (`ui/MediaUtils.kt:7-15`) builds a correct URL from `MediaUrls.view` (tested in `MediaUrlsTest`).

### `ui/TemplatesScreen.kt` (285 lines)

- **Top bar:** a raw `TopAppBar` with a mirrored back arrow and Refresh (`:105-117`).
- **States:**
  - first load is a bare `CircularProgressIndicator` (`:122-124`);
  - an error with no data shows `EmptyState` with Retry (`:125-131`).
- **Search and filters:** an outlined search field, a chip row ("Local only", "All", categories), a count, and `LazyVerticalGrid(Adaptive(160.dp))`.
- **`TemplateCard` (`:229-285`):**
  - a raw `Card` with `RoundedCornerShape(12.dp)` and paddings of 8 and 10;
  - an "API" badge on tertiaryContainer.
- **Import:** a hand-built black/white overlay (`:198-213`). Errors show in a raw `AlertDialog` (`:217-226`).
- **Refresh failure:** a failed refresh while data is shown shows nothing.
- **Tests:** `WorkflowTemplateIndexTest` (4).

### Preferences

`data/UserPreferencesRepository.kt` is a DataStore named "settings". The workflow sort goes here.

### Phase 100 components (`ui/components/`)

- **Components:** `AppTopBar`, `SectionHeader`, `AppCard`, `StatusBanner`, `ConfirmDialog(destructive)`, `EmptyState` (with an action), `ErrorCard`, `LoadingIndicator`, `LoadingOverlay`.
- **`Dimens`:** `xs`–`xl`, `screenPadding`, `minTouch`.

## Decisions

- **D-01: The badge reuses the pre-flight path, but not `preflight()` itself.**
  - A pure `WorkflowCompatibility.check(prompt, metadata)` returns `Ready | Warnings(issues) | WillFail(issues)` from `PromptValidator.validate`.
  - The prompt is built the same way as pre-flight: `buildPrompt(jsonContent, emptyMap(), parseWorkflowInputs(workflow))`, so remembered values count.
  - For the list only: a `VALUE_NOT_IN_LIST` on an `image` input counts as a **warning**, because the user picks the image in the form.
  - `preflight()` keeps its upload filter.
- **D-02: `MainViewModel.workflowStatus: StateFlow<Map<Long, Compatibility>>`.**
  - It combines `allWorkflows` and `_nodeMetadata` on `Dispatchers.Default`.
  - It is memoised per workflow on `(jsonContent, savedInputs)` and the metadata instance, because form saves re-emit the list.
  - It is an empty map without metadata, which means no badges.
- **D-03: Models used.**
  - A pure `WorkflowModels.of(promptJson): List<ModelRef(folder, baseName)>`, built on `ModelSources.MODEL_FILE` and `folderFor`.
  - Computed from `jsonContent`, so there's no backfill.
  - Cards show at most 2 chips, then "+N".
- **D-04: Last result.**
  - Found through `lastImageName`, using a new DAO query `getLatestListingsByFileNames(names)` (visible rows only, the newest per file name).
  - Exposed as `lastResults: StateFlow<Map<Long, GeneratedMediaListing>>`.
  - The thumbnail uses `constructUrl`.
  - `selectWorkflow` uses the same lookup, so its preview URL gets the subfolder and type.
  - Videos show a placeholder icon.
- **D-05: `lastUsedAt: Long?`.**
  - Added with `MIGRATION_14_15`: `ALTER TABLE workflows ADD COLUMN lastUsedAt INTEGER`.
  - Set by a targeted `UPDATE workflows SET lastUsedAt = :t WHERE id = :id`, not by REPLACE, so it doesn't race form saves.
  - Set when a stored workflow is opened (`selectWorkflow`, `id != 0`).
  - "Last used" sorts on `lastUsedAt ?: createdAt`, descending.
- **D-06: Search and sort are a pure `WorkflowListView.apply(workflows, models, query, sort)`.**
  - Search matches the name or a model base name, case-insensitive.
  - The sort is saved in `UserPreferencesRepository`; the query is `rememberSaveable`.
- **D-07: Badges other than Ready open the issue list.**
  - The issue list body of `PreflightDialog` moves into `ui/components/IssueList.kt`.
  - The form dialog and a new read-only `WorkflowIssuesDialog` (Close only) both use it.

- **D-08: Order with Phase 102.** Phase 102 was planned in parallel (2026-09-29) and runs first. It gives Workflows:
  - a `ConnectionChip` in the top bar;
  - "Sync from server" as pull to refresh;
  - a `NotConnectedBanner` with offline gating;
  - an autoConnect preference in `UserPreferencesRepository`.

  103.2 keeps all of these and adds Sort as the third action. 103's preference key sits next to 102's.

## Out of scope

- Fixing `workflowName` on media from queued runs that finish while another workflow is selected.
- Navigation and tabs (Phase 102).
- Export and duplicate of workflows.
