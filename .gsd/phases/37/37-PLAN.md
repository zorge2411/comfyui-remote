---
phase: 37
plan: 1
wave: 1
depends_on: []
files_modified: 
  - app/src/main/java/com/example/comfyui_remote/domain/InputField.kt
  - app/src/main/java/com/example/comfyui_remote/ui/components/ImageSelector.kt
  - app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt
  - app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
autonomous: true
must_haves:
  truths:
    - "User can pick an image from gallery"
    - "Picked image is uploaded to server"
    - "UI shows the picked image"
  artifacts:
    - "ImageSelector composable"
    - "ImageInput domain model"
---

# Plan 37.1: Image Input Component & Integration

<objective>
Implement the `ImageInput` domain model, the `ImageSelector` UI component, and wire it up in `DynamicFormScreen` and `MainViewModel` to handle uploads.
</objective>

<context>
Load for context:
- app/src/main/java/com/example/comfyui_remote/domain/InputField.kt
- app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt
- app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
</context>

<tasks>

<task type="auto">
  <name>Update InputField Domain</name>
  <files>app/src/main/java/com/example/comfyui_remote/domain/InputField.kt</files>
  <action>
    Add `ImageInput` data class:
    ```kotlin
    data class ImageInput(
        override val nodeId: String,
        override val fieldName: String,
        val value: String? = null, // The server filename
        val localUri: String? = null, // For preview before upload completes or just preview
        override val nodeTitle: String
    ) : InputField("Image", nodeTitle, nodeId, fieldName)
    ```
  </action>
  <verify>Check file compiles</verify>
  <done>Sealed class updated</done>
</task>

<task type="auto">
  <name>Create ImageSelector Component</name>
  <files>app/src/main/java/com/example/comfyui_remote/ui/components/ImageSelector.kt</files>
  <action>
    Create generic `ImageSelector` composable:
    - Params: `currentUri: String?`, `onImageSelected: (Uri) -> Unit`
    - Use `rememberLauncherForActivityResult` with `PickVisualMedia`.
    - Display image using `AsyncImage` (Coil).
    - Show "Select Image" button if empty, or overlay edit icon if present.
  </action>
  <verify>Check file compiles</verify>
  <done>Composable created</done>
</task>

<task type="auto">
  <name>Update MainViewModel</name>
  <files>app/src/main/java/com/example/comfyui_remote/MainViewModel.kt</files>
  <action>
    Add `imageRepository` to constructor.
    Add function:
    ```kotlin
    fun onImageSelected(input: InputField.ImageInput, uri: Uri) {
         viewModelScope.launch {
             // 1. Update UI immediately with local URI (optimistic)
             // 2. Upload to server
             // 3. Update UI with server filename
         }
    }
    ```
    Note: Need to handle state update logic for the input list.
  </action>
  <verify>Check file compiles</verify>
  <done>ViewModel handles image selection</done>
</task>

<task type="auto">
  <name>Update DynamicFormScreen</name>
  <files>app/src/main/java/com/example/comfyui_remote/ui/DynamicFormScreen.kt</files>
  <action>
    Handle `InputField.ImageInput` in the `when` block.
    Call `ImageSelector`.
    Delegate `onImageSelected` to ViewModel.
  </action>
  <verify>Check file compiles</verify>
  <done>UI renders image input</done>
</task>

</tasks>

<verification>
- [ ] Manual test: Verify image picker opens and uploads.
</verification>
