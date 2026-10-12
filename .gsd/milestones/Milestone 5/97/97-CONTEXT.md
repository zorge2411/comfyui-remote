# Phase 97: Visible Import and Sync Failures - Context

**Gathered:** 2026-10-12
**Status:** Ready for execution

<domain>
## Phase Boundary

Every way a workflow can fail to get into the app, or the server list can fail to load, ends in a clear message to the user. Gallery/history sync failures get the same treatment. Showing the server's own validation errors after queueing is done (Phase 92). Duplicate imports and the queue re-check are Phase 98.

</domain>

<research>
## Findings (read in `MainViewModel`, 2026-10-12)

**Failures that are only logged:**

| Place | What fails | What the user sees |
|---|---|---|
| `importWorkflowInternal` outer catch | anything after parsing: normalization, database insert | nothing; spinner stops |
| `importWorkflowInternal` conversion catch | `GraphToApiConverter` throws, or JSON is invalid | nothing; **the raw graph JSON is saved as the workflow** |
| `importWorkflowInternal` metadata catch | `/object_info` fetch fails | nothing; **the raw graph JSON is saved as the workflow** |
| `importWorkflowInternal`, `meta == null` | no node list at all | nothing; "conversion skipped" in the log, **raw graph JSON saved** |
| `importServerWorkflow` | download fails, `fullpath` is null | nothing |
| `fetchServerWorkflows` | server unreachable, HTTP error | an empty "Server Workflows" section or none at all |
| `syncHistory`, clear-and-refresh | server unreachable | pull-to-refresh just stops |

**The worst case is not the silence but what gets saved.** A graph-format workflow that can't be converted is stored unconverted. The form then shows no fields (`WorkflowParser` expects API-format nodes), and Generate posts graph JSON, which the server rejects with an unrelated error. The workflow looks imported but can never run.

**UI today:** the Workflows screen shows only `isSyncing` and `importStatus` (spinner overlay). `ImportWorkflowDialog` stays open on failure with no message. Phase 96 added `templateImportError`, an AlertDialog on the Templates screen. `ErrorCard` (with Copy) and `ServerErrorReport` exist from Phase 92.

**Not covered here:** the silent `fetchNodeMetadata` failure on connect. With Phase 97's import rule it only matters when someone imports a graph workflow while the node list is missing, and import now retries the fetch and says so.
</research>

<decisions>
## Implementation Decisions

- **D-01 Never save an unconvertible workflow.** A pure function `WorkflowImportPrep.prepare(json, nodeMetadata): Result` decides what gets stored:
  - not valid JSON, or not a JSON object → `Failed(InvalidJson)`;
  - API format (no `nodes`+`links`) → `Ready(json, emptyList())`;
  - graph format with no node metadata → `Failed(NeedsServerNodeList)`;
  - graph format, converter throws → `Failed(ConversionFailed, message)`;
  - otherwise `Ready(converted.json, converted.missingNodes)`.
  `importWorkflowInternal` fetches metadata first (as now) and passes whatever it has.
- **D-02 One failure type.** `ImportFailure(step, name, message)` with steps FETCH, METADATA, CONVERT, SAVE, and a formatter that turns exceptions into plain language:
  - no route, refused, timeout → "Can't reach the server at <address>. Check the connection."
  - HTTP 404 → "The server doesn't have that file."; other HTTP codes → "The server answered HTTP <code>."
  - invalid JSON → "That isn't valid workflow JSON."
  - anything else → the exception message.
  The message always starts with the workflow name and the step, e.g. `Couldn't import "My flow" (converting): …`.
- **D-03 Surface.** `MainViewModel.importFailure: StateFlow<ImportFailure?>`. The Workflows screen shows it in an AlertDialog with Copy and OK, above the import dialog if it is open (so the user can fix and retry). Cleared on OK and when a new import starts.
- **D-04 Server workflow list.** `serverWorkflowsError: StateFlow<String?>`. When loading fails, a card at the top of the Workflows list says why, with a Retry button; a successful fetch clears it.
- **D-05 History sync.** `syncError: StateFlow<String?>`, shown as a Snackbar on the Gallery and History screens when sync or clear-and-refresh fails; cleared when shown.
- **D-06** The failure messages use the same exception-to-text mapping, so there is one place to improve wording later.
- Out of scope: retry/backoff, offline queueing, `fetchNodeMetadata` on connect.

### Claude's Discretion
- Exact dialog layout and wording beyond D-02; helper names.
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/MainViewModel.kt`: `importWorkflowInternal`, `importServerWorkflow`, `fetchServerWorkflows`, `syncHistory`, clear-and-refresh
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` (`convert`, `ConversionResult`)
- `app/src/main/java/com/example/comfyui_remote/ui/WorkflowListScreen.kt`, `ImportWorkflowDialog.kt`, `GalleryScreen.kt`, `HistoryScreen.kt`
- `app/src/main/java/com/example/comfyui_remote/ui/components/ErrorCard.kt` (Copy), `domain/ServerErrorReport.kt`
</canonical_refs>
