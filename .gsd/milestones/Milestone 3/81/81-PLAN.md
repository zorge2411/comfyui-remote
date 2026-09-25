# Phase 81: Image-to-Image Workflow Node Support (Verify + Fix)

## Goal Description

Verify the existing `LoadImage` image-to-image flow (built in Phase 64) actually works end-to-end against a live ComfyUI server, and fix any bugs found — without expanding scope to other node types (ControlNet, inpainting, etc. are explicitly deferred, per 81-CONTEXT.md).

**Code review already surfaced two concrete bugs** (static analysis, before any live test) — see below. These are fixed as part of this plan; the live test then confirms the happy path and catches anything static review couldn't.

## Findings From Code Review

Traced the full pipeline: `ImageSelector` (pick) → `DynamicFormScreen`'s `onImageSelected` (upload) → `MainViewModel.executeWorkflow` → `WorkflowExecutionService.prepareAndQueue` → `WorkflowExecutor.injectValues` (patch into JSON) → `api.queuePrompt`.

**Bug 1 — Race condition: executing before upload completes silently drops the selected image.**

In `app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt` (~lines 327-349), selecting an image does an *optimistic* update setting `value = null` (line 330), then kicks off the upload in `scope.launch { ... }` (lines 334-344) which sets `value = uploadResponse.name` only once the upload finishes. The Generate button (line 456-461) and Queue button (line 442-448) are only gated on `executionStatus`, not on upload-in-flight state. If the user taps Generate/Queue before the upload coroutine completes, `WorkflowExecutor.injectValues` (`app/src/main/java/com/example/comfyui_remote/domain/WorkflowExecutor.kt`, lines 50-54) sees `input.value == null` and skips patching the `image` field entirely — the workflow silently executes with whatever the original/default image value was in the node, with **no error or warning to the user**.

**Bug 2 — Silent upload failure.** Same block, lines 345-347: `else { // Upload failed }` is a bare comment — no error surfaced, no revert of the optimistic `localUri`/`value = null` state. If upload fails (network error, server rejects the file, etc.), the image selector is left showing nothing selected with zero user feedback, and if the user proceeds anyway, the same silent-default-image behavior from Bug 1 occurs.

**Dead code noted, not touched:** `MainViewModel._inputImages`/`setInputImage()` (lines 541-552), `WorkflowExecutionService.uploadImages()`, and `WorkflowPatchingService`'s `uploadedFilenames`-based patch path are never populated from `DynamicFormScreen` (grep confirms `setInputImage` has zero callers in that file) — so `uploadImages()` always receives an empty map and `patchWorkflow()` is always a no-op (`app/src/main/java/com/example/comfyui_remote/domain/WorkflowPatchingService.kt` line 20: `if (imageInputs.isEmpty()) return json`). The actual working mechanism is entirely `injectValues`. This is vestigial from Phase 64's original design and is confusing for future debugging, but removing it is a larger refactor than this phase's bug-fix scope — **flagged as a deferred idea, not removed here** (see CONTEXT.md deferred section; add to TODO.md if you want it cleaned up later).

## Proposed Changes

### 1. Block execution while an image upload is in flight

**Modify** `app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt`:

- Add a new remembered state near the existing `var inputs by remember { ... }` (line 81): `var pendingImageUploads by remember { mutableStateOf(setOf<Int>()) }` (tracks the `index` of any `ImageInput` currently uploading).
- In the `ImageInput` branch's `onImageSelected` callback (~lines 327-349): before the optimistic update, add `index` to `pendingImageUploads`. In both the success branch (after `list[index] = current.copy(value = uploadResponse.name)`) and the failure branch, remove `index` from `pendingImageUploads`.
- Change the Queue button's `enabled` (line 448) and Generate button's `enabled` (line 461) from `executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED` to additionally require `pendingImageUploads.isEmpty()` — e.g. `(executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED) && pendingImageUploads.isEmpty()`.
- Optionally show a small inline indicator (e.g. `CircularProgressIndicator` or disabled/dimmed state) on the `ImageSelector` itself while its index is in `pendingImageUploads`, so the user understands why Generate is disabled — reuse the existing `CircularProgressIndicator` pattern already used for `ExecutionStatus.EXECUTING` (line 464-467) for visual consistency.

### 2. Surface upload failures instead of failing silently

**Modify** `app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt` (~line 345-347):

- Replace the bare `// Upload failed` comment with: revert the optimistic update (`list[index] = current.copy(localUri = null, value = originalValueBeforeSelection)` — or simplest, just clear `localUri` back to what it was before this selection attempt so the selector shows its prior/empty state, not a broken image reference) AND surface the failure via the ViewModel's existing error-message channel.
- **Add** a small public method to `MainViewModel.kt` near `uploadImage()` (line 1467): `fun reportError(message: String) { _errorMessage.value = message }` (or reuse an existing equivalent if one already exists with public visibility — check before adding a duplicate). Call it from the failure branch: `viewModel.reportError("Image upload failed — please try again")`.
- Confirm `_errorMessage`/`errorMessage` (`MainViewModel.kt` lines 394-395) is already observed and displayed somewhere in the UI (e.g. a Snackbar host) — if it is, this wiring is enough; if not, that's a separate pre-existing gap outside this phase's scope, just confirm and note it in the summary rather than building new UI for it.

### 3. Live end-to-end verification (user-run)

Not a code change — a manual test pass against a real ComfyUI server, run by the user (per CONTEXT.md decision), after the two fixes above are built and installed:

1. Import or open a workflow containing a `LoadImage` node.
2. Confirm the `ImageInput` field renders with the gallery/camera `ImageSelector`.
3. Pick an image, and **immediately** tap Generate before the upload could plausibly finish — confirm the button is now disabled/blocked until upload completes (Bug 1 fix).
4. Pick an image normally, wait for it to finish uploading, tap Generate — confirm the workflow actually runs using the *selected* image (verify by checking the output result reflects the input image, e.g. an img2img denoise pass visibly influenced by the source image), not a default/placeholder.
5. If possible, force an upload failure (e.g. airplane mode momentarily, or an invalid/huge file) — confirm an error message now appears instead of silent failure (Bug 2 fix).
6. Report back: pass/fail per step, plus any node-type gap encountered (e.g. tried a ControlNet workflow and it had no image picker at all) — gaps get logged as a new deferred TODO/phase, not fixed here.

## Verification Plan

### Automated

```bash
./gradlew.bat testDebugUnitTest
```

No new unit tests are strictly required for a UI-state race-condition fix (Compose state timing isn't easily unit-testable here), but if `WorkflowExecutor.injectValues` or `WorkflowPatchingService` are touched at all, re-run `WorkflowPatchingServiceTest` to confirm no regression.

### Build Verification

```bash
./gradlew.bat assembleDebug
./gradlew.bat installDebug
```

### Manual (device)

Steps 1-6 under "Live end-to-end verification" above, run by the user against their live ComfyUI server.

## Success Criteria

- [ ] Generate/Queue buttons are disabled while any selected image is still uploading (Bug 1 fixed)
- [ ] Upload failure surfaces a visible error to the user instead of failing silently (Bug 2 fixed)
- [ ] `assembleDebug` / `installDebug` succeed
- [ ] `WorkflowPatchingServiceTest` (and full `testDebugUnitTest` suite) still pass
- [ ] User confirms live test: img2img workflow actually uses the selected/uploaded image, not a default/placeholder
- [ ] Any node-type gap found during live testing (ControlNet, inpainting, etc.) is documented as a new deferred item, not built in this phase
