# Plan 106.3 Summary: Motion, Device Check and Close

**Completed:** 2026-10-01 (commit `18ee539`; device check the same day).

## Delivered

- **`ui/components/SharedImage.kt`:** `Modifier.sharedImage(key, sharedTransitionScope, animatedVisibilityScope)`, a no-op without a key or scopes.
- **Card → form:**
  - the card's `Thumbnail` image and the form preview share `"workflow-thumb-<workflowId>"`;
  - the Workflows route passes the scopes and `viewModel.lastResults.value[workflow.id]` to `selectWorkflow`, which then sets the subfolder-correct preview URL and the media id synchronously, so both sides use the same image.
- **Preview → viewer:** an outer Box around the preview carries `"image-<mediaId>"`, matching the viewer's pager element. No viewer change.
- **Wiring:** `WorkflowListScreen` and `DynamicFormScreen` take nullable scopes; `MainActivity` passes `this@SharedTransitionLayout` / `this@composable` on both routes.

## Verification

See `106-VERIFICATION.md`. 255 tests pass; `assembleDebug` OK.
