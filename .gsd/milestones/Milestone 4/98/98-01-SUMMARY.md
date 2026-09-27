# Plan 98.1 Summary: Check File Values Against Empty Server Lists

**Completed:** 2026-09-27. Device check passed.

## Delivered (`21d123b`)

- `PromptValidator`: an empty server option list now counts as "the server has no files" when either:
  - the value is a file name; or
  - the input is an upload input (`image_upload`, `video_upload` or `audio_upload`, via the new `isUploadInput`, which reads both spec forms).

  This applies only while `checkFileValues` is on. Other empty combos are still skipped.
- Message: `File not on the server: 'x' (the server has none for this input)`, with `; pick another file or upload it` added for upload inputs. Severity ERROR. The non-empty message is unchanged.
- KDoc updated.
- 3 tests in `PromptValidatorTest`:
  - an empty V3 `COMBO` with `video_upload`, and a legacy empty list with `image_upload`, are reported with the exact message;
  - a non-file value on an empty non-upload combo is not reported;
  - `checkFileValues = false` reports nothing.

## Verification

- `testDebugUnitTest`: 200 tests, 0 failures. `WorkflowCorpusTest` green, and `known-failures.json` still `{}`. `assembleDebug` / `installDebug` OK.
- **Device (2026-09-27, Fairphone 6, ComfyUI 0.37.0):**
  1. "Video Upscale: Real-ESRGAN", then Generate. The pre-flight dialog showed "Server will likely reject this prompt", "1 error", and under LoadVideo: "file: File not on the server: 'gan_input.mp4' (the server has none for this input); pick another file or upload it". Cancel queued nothing (the server list stayed empty).
  2. **Instead of queueing a real job:** the live `/object_info` has only two empty upload inputs, `LoadAudio.audio` and `LoadVideo.file` (the input folder has no audio or video). The 53 other empty combos are flagged only when their value looks like a file, which the server rejects too.

     The check that a normal workflow gets no dialog was not repeated on the device, because it would queue a GPU job. The rule can't fire for inputs whose files exist.
