# Phase 81: Image-to-Image Workflow Node Support — Context

**Date**: 2026-08-25
**Domain**: Verifying (and fixing, if broken) the app's existing `LoadImage`-based image-to-image workflow execution path.

We clarified HOW to approach this phase. No new capabilities scoped beyond verifying/fixing what already exists.

## Prior State (from codebase research — Phase 64, 2026-01-30)

Image-to-image support is **already substantially implemented**, built the same day this TODO was written:

- `InputField.ImageInput` (`app/src/main/java/com/example/comfyui_remote/domain/InputField.kt`) — sealed-class input type with `value` (server filename) and `localUri` (local preview before upload).
- `WorkflowParser.kt` — generates an `ImageInput` form field when a node's `class_type == "LoadImage"` and field name is `"image"`.
- `GraphToApiConverter.kt` — special-cases `LoadImage` / `ETN_LoadImageBase64` node types (`isManualLoadImage`, line ~68/157/256-259) with a fallback that keeps the node and maps its first widget to the `"image"` input even when `/object_info` metadata is missing.
- `ImageSelector.kt` (`app/src/main/java/com/example/comfyui_remote/ui/components/`) — already supports **both** gallery picker (`PickVisualMedia`) and camera capture.
- `ImageRepository.kt` — uploads via `ContentResolver` → `MultipartBody.Part` → `ComfyApiService`'s `POST /upload/image`.
- `WorkflowPatchingService.kt` — patches the uploaded server filename into the workflow JSON (Graph and API formats), covered by `WorkflowPatchingServiceTest.kt`.
- `MainViewModel.executeWorkflow()` orchestrates: parse `inputImages` → upload → patch → execute.

Phase 64's own `SUMMARY.md` flagged as unresolved: *"User to test with actual ComfyUI server"* and *"Monitor for other node types that might need similar fallbacks"* — this was never followed up on, which is why the TODO (and now Phase 81) exists.

**Known gap (not in scope to fix here, see Decisions):** only the literal `LoadImage` / `ETN_LoadImageBase64` class_types trigger `ImageInput` UI. Other image-input node types (ControlNetApply, VAEEncode/InpaintModelConditioning, IPAdapter, etc.) are not recognized — those workflows currently get no image picker at all.

## Decisions

### Scope
- **This phase is verification-first, not a from-scratch build.** Confirm the existing `LoadImage` img2img flow (upload → patch → execute) actually works end-to-end against a real ComfyUI server — Phase 64 never got that confirmation.
- **If verification finds a genuine bug in the existing `LoadImage` flow** (upload fails, patching produces a malformed workflow, wrong image gets applied, etc.) — **fix it inline, within this phase.**
- **If verification surfaces a gap in node-type coverage** (ControlNet, inpainting/VAEEncode, IPAdapter, or other image-input node types not recognized) — **do not build support for it here.** Note it explicitly (e.g. as a deferred idea / new TODO) and let it become its own future phase. This phase's fix scope is bounded to what Phase 64 already claimed to deliver (`LoadImage`/`ETN_LoadImageBase64`), not new node-type coverage.

### Test method
- **Live ComfyUI server is available.** Verification should be a real end-to-end execution (pick/upload an image → run a `LoadImage`-based img2img workflow → confirm correct output), not just code review or unit tests.

### Test execution
- **User will run the live test themselves** on their device and report back results (success, failure, error messages/screenshots) — not driven remotely via adb by the executor.
- Executor's job: prepare/confirm the app builds and installs correctly, walk the user through what to test (which workflow, what to look for), then act on their report — fixing any bug found, or documenting any node-type gap found.

## Deferred Ideas
- **Broader image-input node-type support** (ControlNet, VAEEncode/inpainting, IPAdapter, custom nodes with non-`LoadImage` image widgets) — explicitly out of scope for Phase 81. If verification confirms this gap is real and wanted, it should become its own future phase (e.g., "Extend image-input detection beyond LoadImage").

## Canonical Refs
- `app/src/main/java/com/example/comfyui_remote/domain/InputField.kt` — `ImageInput` type definition
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt` — form-field generation, `LoadImage` detection (~line 51)
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` — `LoadImage`/`ETN_LoadImageBase64` fallback handling (~lines 68, 157, 256-259)
- `app/src/main/java/com/example/comfyui_remote/ui/components/ImageSelector.kt` — gallery + camera picker UI
- `app/src/main/java/com/example/comfyui_remote/data/ImageRepository.kt` — upload implementation
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowPatchingService.kt` — filename patching into workflow JSON
- `app/src/test/java/com/example/comfyui_remote/WorkflowPatchingServiceTest.kt` — existing coverage
- `.gsd/phases/64/64-SUMMARY.md`, `.gsd/phases/64/64-RESEARCH.md` — original implementation record and known open items
- `.gsd/TODO.md:25`, `.gsd/JOURNAL.md:30` — origin of this phase
