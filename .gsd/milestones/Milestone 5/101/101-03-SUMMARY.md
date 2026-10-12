# Plan 101.3 Summary: Form UI

**Completed:** 2026-09-28. Device check passed; one item not checked (see below).

## Delivered

**`1c622b1`: the decluttered form**
- `ui/form/FormFields.kt`:
  - `PromptField` (Copy, Clear) and `TextInputField`;
  - `NumberField`, which keeps the raw text, so "1." and an empty field work, and shows an error state until the text parses;
  - `SeedField`, with Random/Fixed segments, a dice button (new seed, set to Fixed), and "Last used: N" with a Use button;
  - `SelectionField` (typed `menuAnchor`).
- `ui/form/FormSections.kt`:
  - `MainSettings`: width/height and steps/CFG side by side;
  - `NodeSection`: collapsible, shows the input count, 48 dp header.
- `ui/form/FormActionBar.kt`: runs stepper, Queue, Generate, and a busy label.
- `DynamicFormScreen.kt` went from 1,010 to 717 lines.
  - **Bars:** a fixed `AppTopBar` (node sheet, and a menu with Reset to workflow defaults and Save as template), and the fixed bottom bar.
  - **Status area**, using `StatusBanner`s with no emoji: missing nodes, missing models (the Phase 97/99 logic as a Warning banner), skipped outputs, the execution error (`ErrorCard`, with Retry calling Generate again), progress, and the latest result.
  - Then the prompt and negative prompt, Main settings, and "Other inputs" as collapsed node sections.
  - Edits and uploads are keyed by nodeId/fieldName, and saved through `saveFormValues`.
  - A late `/object_info` re-parses without losing edits.
  - "View Queue Manager" was removed, since the Queue tab covers it.
- **Deviation from the plan:** the form body is a scrolling `Column`, not a `LazyColumn`. Text fields in a lazy list lose focus and the keyboard when they scroll out of view.

**Device fixes**
- The bottom-bar buttons were cut off on the 372 dp wide phone ("Que", "Generat"): smaller content padding and gaps fixed it.
- The 200 dp latest-result image pushed the prompt below the fold. It's now a compact row with a 96 dp thumbnail, "Latest result" and "Open in gallery".

## Device check (2026-09-28, Fairphone 6, ComfyUI 0.37.0)

1. **Database upgrade:** 12 → 13 in place; 8 of 8 workflows kept; the `savedInputs` column was added. ✅
2. **Z-Image-Turbo layout:**
   - Prompt first;
   - Main settings: Seed, Width/Height (side by side), Steps/CFG (side by side), Sampler, Scheduler, Denoise, Batch size, Model;
   - Other inputs: SaveImage, CLIPLoader (3), VAELoader, UNETLoader (weight_dtype) and ModelSamplingAuraFlow, collapsed;
   - Generate always visible. ✅
3. **Seed:**
   - Fixed at 12345, generated twice: the server's `/history` shows seed 12345 for both runs (`3b69df25`, `1073d43c`, both success).
   - Random: the server got 2560143477890379828, and the form showed the same value under "Last used" with a Use button. ✅
4. **Remembering:** a prompt edit ("TEST") survived leaving and reopening the form, and was stored in `savedInputs`. "Reset to workflow defaults" (with a confirm dialog) restored the original prompt and seed, and cleared the column. ✅
5. **Progress and latest result:** shown at the top (compact card after the fix). ✅
6. **Missing-models banner: not checked on the device.** None of the 8 workflows on the phone has missing models, and importing a template just for the test would add a workflow to the user's recently tidied list. The logic is Phase 97/99 unchanged; only the wrapper is now a `StatusBanner`. ⚠️
7. **Landscape:** the form scrolls, and the top and bottom bars stay usable. ✅

**Test note:** during the check, taps on Generate first went to the keyboard. The number keyboard stayed open, because `input keyevent 111` doesn't close it on this phone. That was the test harness, not the app; closing the keyboard with Back fixed it.

## Also noticed

- FastVideo FastH3 has no Prompt field at the top: its prompt node isn't found through sampler topology, so the prompt stays in a node section. This is the designed fallback; improving prompt detection could be a follow-up.
- Nodes without a `_meta.title` show their class name as the section title ("CLIPLoader", "SaveImage").
- In landscape, the workflow list shows only one card at a time (Phase 103).

## Verification

- `testDebugUnitTest`: 211 tests, 0 failures. `assembleDebug` and `installDebug` OK.
