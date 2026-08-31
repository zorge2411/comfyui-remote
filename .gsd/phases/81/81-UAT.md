---
status: diagnosed
phase: 81-image-to-image-workflow-node-support
source: [81-PLAN.md, 81-CONTEXT.md, ROADMAP.md]
started: 2026-08-31T00:00:00Z
updated: 2026-08-31T00:00:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

number: 4
name: Executed result actually uses the picked image
expected: |
  After picking an image and queuing the workflow, the generated result reflects the
  picked image as input (not some default/previous image) - confirm via the output.
awaiting: (complete)

## Tests

### 1. LoadImage field shows the picker
expected: Open a workflow with a LoadImage node. The image field shows a gallery/camera picker (thumbnail preview, tap-to-pick), not a plain filename + dropdown.
result: issue
reported: "yes, shows the picker - i tried the gallery option and my gallery opened - i also tried the camera option and the app crashed"
severity: blocker

### 2. Generate/Queue gated during image upload
expected: Pick a new image for the LoadImage field. While it's uploading, the Generate/Queue button is disabled (can't be tapped) until the upload finishes.
result: pass

### 3. Upload failure surfaces an error
expected: If an image upload fails (e.g. disconnect from server mid-upload), the app shows a visible error instead of silently doing nothing, and the button state is not left permanently stuck disabled.
result: pass
reported: "Tested with flight mode enabled mid-upload — the button stayed disabled while flight mode was on, and an error was eventually shown (not stuck forever)."

### 4. Executed result actually uses the picked image
expected: After picking an image and queuing the workflow, the generated result reflects the picked image as input (not some default/previous image) - confirm via the output.
result: pass
reported: "yes, matches the image - it looks like the same image in fact"

### 5. (discovered during testing) Checkpoint/model dropdown empty after app process restart
expected: N/A - not an originally planned test; surfaced organically while retesting Test 4 after Test 1's crash killed the app process.
result: issue
reported: "so no checkpoints loaded when selecting dropdown - is the folder empty?" — confirmed server has exactly one checkpoint (flux1-dev-fp8.safetensors) via direct /object_info and /models/checkpoints query. Root cause: app process was killed by the Test 1 camera crash; on restart, in-memory node-metadata/available-models caches (populated only during the explicit "Connect" flow) were empty and never refetched, while the UI still looked "connected." An explicit disconnect/reconnect fixed it (user-confirmed).
severity: minor

## Summary

total: 5
passed: 3
issues: 2
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Tapping the Camera option in the LoadImage picker opens the device camera without crashing."
  status: failed
  reason: "User reported: 'i also tried the camera option and the app crashed'. Logcat confirms: java.lang.SecurityException: Permission Denial ... revoked permission android.permission.CAMERA, thrown from ImageSelector.kt's launchCamera() (ImageSelectorKt.ImageSelector$launchCamera(ImageSelector.kt:80)) via cameraLauncher.launch(uri)."
  severity: blocker
  test: 1
  root_cause: "ImageSelector.kt's launchCamera() (app/src/main/java/com/example/comfyui_remote/ui/components/ImageSelector.kt, lines 71-81) launches the TakePicture ActivityResultContract directly without ever checking or requesting the runtime android.permission.CAMERA permission first. The manifest already declares <uses-permission android:name=\"android.permission.CAMERA\" /> (AndroidManifest.xml:12), but no runtime request flow exists — Android 6.0+ requires an explicit runtime grant for dangerous permissions regardless of the manifest declaration."
  artifacts: ["app/src/main/java/com/example/comfyui_remote/ui/components/ImageSelector.kt"]
  missing: ["A rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) flow that requests CAMERA before calling launchCamera(), with a graceful denied-permission UX (e.g. a message or fallback to Gallery) instead of proceeding to launch the camera intent unconditionally."]
  debug_session: ""

- truth: "After the app process restarts (e.g. following a crash) while the server connection appears active, the checkpoint/model dropdown in the workflow form is populated from the server, not empty."
  status: failed
  reason: "User reported an empty checkpoint dropdown after the Test 1 camera crash killed and restarted the app process. Confirmed the server itself has one valid checkpoint (flux1-dev-fp8.safetensors, verified directly via /object_info and /models/checkpoints). An explicit disconnect/reconnect in the app resolved it, confirming the root cause is a stale/empty in-memory cache, not a server-side or parsing issue."
  severity: minor
  test: 5
  root_cause: "MainViewModel's _nodeMetadata and _availableModels StateFlows are only populated inside connect() (via fetchNodeMetadata() / fetchAvailableModels(), MainViewModel.kt lines 668-688, 744-745), which runs once when the user explicitly connects. There is no re-fetch on app/process restart, and the app's persisted host/port/connected-looking UI state gives no visible signal that these in-memory caches are actually empty until the user opens a form field that depends on them."
  artifacts: ["app/src/main/java/com/example/comfyui_remote/MainViewModel.kt"]
  missing: ["Either an automatic re-fetch of node metadata/available models on process restart when a server address is already saved (e.g. from init{} once host/port load), or a visible empty/loading state on the SelectionInput/ModelInput dropdowns that prompts the user to reconnect instead of silently rendering as an empty list."]
  debug_session: ""
