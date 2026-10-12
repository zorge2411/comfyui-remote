# Phase 98 Context: Pre-flight Misses Missing Input Files

**Gathered:** 2026-09-26 (`/plan 98 --auto`: each decision uses the recommended option)

## Problem

In the Phase 97 device check, "Video Upscale: Real-ESRGAN" (`LoadVideo.file = gan_input.mp4`) was queued without a pre-flight dialog. The server rejected it: "Custom validation failed for node — file - Invalid video file: gan_input.mp4".

## Root cause

- The server's `/object_info` gives `LoadVideo.input.required.file = ["COMBO", {"multiselect": false, "options": [], "video_upload": true}]`. The input folder has no videos, so the list is empty.
- `PromptValidator.validate` only runs the list check when `options.isNotEmpty()` (`domain/PromptValidator.kt`, around line 144), so an empty list hides the missing file.
- The guard dates from the Phase 89 test suite (`df49f0f`), whose reference `object_info.json` has 24 empty file lists. The suite's `ApiPromptValidator` calls with `checkFileValues = false`.
- `MainViewModel.preflight` did not skip the check. Both of its skip paths log `Log.w("PREFLIGHT", …)`.

**How ComfyUI decides:** `execution.validate_inputs` rejects any value not in a combo list, including an empty one. Nodes with their own `VALIDATE_INPUTS` (LoadImage, LoadVideo) reject a missing file themselves.

## Decisions

- **D-01:** An empty option list means "the server has no files" only when:
  - the value is a file name (`FILE_VALUE`), or the input spec has `image_upload`, `video_upload` or `audio_upload` set to true;
  - and `checkFileValues` is true.

  Other empty lists are still skipped, because some lists are filled in dynamically.
- **D-02:** Skip logging stays as it is. It already exists, and it wasn't the cause.
- **D-03:** Video and audio upload from the phone is out of scope. The pending-upload filter stays limited to LoadImage `image`.
- **D-04:** No research step (level 0).

## Wanted outcome

Generate or Queue shows the pre-flight dialog naming the missing input file, with a hint to pick another file or upload it.
