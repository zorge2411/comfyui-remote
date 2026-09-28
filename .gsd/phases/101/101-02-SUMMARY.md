# Plan 101.2 Summary: Remembered Form Values

**Completed:** 2026-09-28

- **`WorkflowEntity.savedInputs`**, with `MIGRATION_12_13`; Room is now version 13.
- **`domain/FormValues`:**
  - `encode` serializes by runtime type, as the queue does. A first version used the declared `List<InputField>` type, which made Gson write only the `label` tag; the test caught it.
  - `decode` uses the shared `InputFieldDeserializer`.
  - `overlay(parsed, saved)` restores values by nodeId, fieldName and type. A Seed also restores `fixed`. A Selection is restored only if the option is still offered, and an Image only its server file name.
- **`MainViewModel`:**
  - `parseWorkflowInputs(workflow)` applies the overlay;
  - `saveFormValues`, debounced 500 ms, skips history previews (id 0);
  - `resetFormValues` clears the column and returns the workflow's own inputs;
  - `promptNodeIds(json)`.
- **Tests:** `FormValuesTest` (3). Full suite: 211 tests, 0 failures.
