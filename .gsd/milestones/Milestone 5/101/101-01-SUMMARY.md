# Plan 101.1 Summary: Form Model and Seed Policy

**Completed:** 2026-09-28 (`a64bfcb`)

- **`domain/FormLayout.kt`**
  - Builds `FormModel(prompt, negative, main, groups)`.
  - `prompt` and `negative` are the `text`/`prompt` field of the conditioning source nodes.
  - Main roles in order: Seed, Width, Height, Steps, CFG, Sampler, Scheduler, Denoise, Batch size, Checkpoint, Model. A role is promoted only when exactly one field in the workflow has it (D-02).
  - Everything else goes into per-node groups, with humanized labels ("Filename prefix", "Format › codec").
- **`WorkflowParser`:** `findPositivePromptNodeId` is public, and `findNegativePromptNodeId` uses the same sampler-topology rule.
- **`InputField`:**
  - `SeedInput.fixed: Boolean?` (null or false means random), with the `label` tags unchanged (D-03);
  - new `InputField.key` = "nodeId/fieldName".
- **`domain/SeedPolicy.resolve`:** Fixed seeds are sent as typed, and every other seed gets a new random value. It returns the seeds used.
  - `MainViewModel.executeWorkflow` and `QueueViewModel` use it.
  - `MainViewModel.lastUsedSeeds` is published.
- **`InputFieldDeserializer`** moved from `QueueViewModel.kt` to `domain/` (shared with Plan 101.2).
- **Tests:** `FormLayoutTest` (5: Z-Image-Turbo shape, two samplers, no prompt ids, negative via topology, humanize) and `SeedPolicyTest` (3, including queued JSON with and without `fixed`).
