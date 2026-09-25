---
phase: 53
plan: 1
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt
  - app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterTest.kt
autonomous: true
user_setup: []

must_haves:
  truths:
    - "Imported workflows preserve node titles as _meta.title"
    - "DynamicFormScreen displays user-friendly node names"
  artifacts:
    - "GraphToApiConverter includes _meta generation logic"
    - "Unit tests pass verifying _meta presence"
  key_links:
    - "WorkflowParser uses _meta.title for UI labels"
---

# Plan 53.1: Enhance Workflow Import Metadata

<objective>
Improve the `GraphToApiConverter` to preserve node titles from the source graph by mapping them to `_meta.title` in the API output. This ensures that imported server workflows display correct, user-friendly labels in the Dynamic Form (e.g., "Positive Prompt" instead of "CLIPTextEncode").

Purpose: Allow server workflows to be imported and used locally with the same rich UI experience as template workflows.
Output: Updated converter logic and passing unit tests.
</objective>

<context>
Load for context:
- app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt
- app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt (reference for _meta usage)
- app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterTest.kt
</context>

<tasks>

<task type="auto">
  <name>Update GraphToApiConverterTest</name>
  <files>app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterTest.kt</files>
  <action>
    Add a test case or update existing tests to assert that the converted JSON contains `_meta` objects with `title` fields.

    Example assertion logic:
    - Include a node in the input graph with `"title": "Custom Title"`.
    - Assert that the output API node has `"_meta": { "title": "Custom Title" }`.
  </action>
  <verify>.\gradlew testDebugUnitTest --tests "com.example.comfyui_remote.domain.GraphToApiConverterTest" (Should FAIL initially or pass if logic existed)</verify>
  <done>Tests fail with missing _meta, or pass after implementation</done>
</task>

<task type="auto">
  <name>Implement _meta preservation in GraphToApiConverter</name>
  <files>app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt</files>
  <action>
    Modify `convert` function loop (lines 35-115):
    1. Extract the `title` field from the source graph node (`nodeElement`).
       - Fallback to `classifier` or `type` if title is missing, but preferably use the explicit "title" field if present.
    2. Create a `_meta` JsonObject.
    3. Add title to `_meta`.
    4. Add `_meta` to `apiNode`.

    ```kotlin
    val title = if (node.has("title")) node.get("title").asString else type
    val meta = JsonObject()
    meta.addProperty("title", title)
    apiNode.add("_meta", meta)
    ```
    
    AVOID: Overwriting existing inputs. Just append the `_meta` key to the `apiNode` object.
  </action>
  <verify>.\gradlew testDebugUnitTest --tests "com.example.comfyui_remote.domain.GraphToApiConverterTest"</verify>
  <done>Tests pass</done>
</task>

</tasks>

<verification>
After all tasks, verify:
- [ ] Unit tests pass
- [ ] Manual check: Import a workflow from server and check titles in DynamicFormScreen
</verification>

<success_criteria>

- [ ] Imported workflows have readable titles in the Form UI
- [ ] "Model Selection" and "Prompt" fields are correctly labeled
</success_criteria>
