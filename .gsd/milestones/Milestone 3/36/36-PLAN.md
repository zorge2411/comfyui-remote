---
phase: 36
plan: 1
wave: 1
depends_on: []
files_modified: 
  - app/src/main/java/com/example/comfyui_remote/network/ComfyApiService.kt
  - app/src/main/java/com/example/comfyui_remote/data/ImageRepository.kt
autonomous: true
must_haves:
  truths:
    - "Can upload an image file to ComfyUI server via API"
    - "Returns the filename implementation needs for the node"
  artifacts:
    - "ImageRepository class"
---

# Plan 36.1: API & Repository Implementation

<objective>
Implement the `uploadImage` API endpoint and the `ImageRepository` logic to handle file URI to Multipart conversion.
</objective>

<context>
Load for context:
- app/src/main/java/com/example/comfyui_remote/network/ComfyApiService.kt
</context>

<tasks>

<task type="auto">
  <name>Update ComfyApiService</name>
  <files>app/src/main/java/com/example/comfyui_remote/network/ComfyApiService.kt</files>
  <action>
    Add `ImageUploadResponse` data class:
    ```kotlin
    data class ImageUploadResponse(val name: String, val subfolder: String, val type: String)
    ```
    Add method:
    ```kotlin
    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("overwrite") overwrite: RequestBody? = null
    ): ImageUploadResponse
    ```
    Note: Requires `okhttp3.MultipartBody` and `RequestBody`.
  </action>
  <verify>Check file compiles</verify>
  <done>Interface and data class exist</done>
</task>

<task type="auto">
  <name>Create ImageRepository</name>
  <files>app/src/main/java/com/example/comfyui_remote/data/ImageRepository.kt</files>
  <action>
    Create class `ImageRepository`.
    Add function:
    ```kotlin
    suspend fun uploadImage(
        api: ComfyApiService,
        uri: Uri,
        contentResolver: ContentResolver,
        fileName: String? = null
    ): ImageUploadResponse
    ```
    Logic:
    1. Open InputStream from Uri.
    2. Read bytes.
    3. Create RequestBody (MediaType "image/*").
    4. Create MultipartBody.Part.createFormData("image", fileName, body).
    5. Call api.uploadImage.
  </action>
  <verify>Check file compiles</verify>
  <done>Class exists and handles Multipart logic</done>
</task>

</tasks>

<verification>
- [ ] Manual test: Will be performed in next phase or via temp call in ViewModel.
</verification>
