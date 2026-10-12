# Phase 89: Workflow Compatibility Regression Corpus - Context

**Gathered:** 2026-09-25
**Status:** Ready for execution

<domain>
## Phase Boundary

Build a set of real graph-format workflow fixtures plus a `/object_info` snapshot, and a JVM unit test that runs every fixture through `GraphToApiConverter.convert()` and checks that the output is structurally valid. This phase adds tests only: no converter changes. Fixtures that fail today are recorded as known failures for Phases 90–91 to fix.

</domain>

<research>
## Findings

**Fixture source: official ComfyUI workflow templates.** The `comfyui-workflow-templates-json` PyPI package (0.1.95, pulled in by `comfyui-workflow-templates` 0.11.69, checked 2026-09-25) contains 588 template JSON files, 572 of them graph-format (`nodes`/`links`). The license is MIT, "Copyright (c) 2023-present Comfy Org" (LICENSE at github.com/Comfy-Org/workflow_templates). These are the workflows users load from the ComfyUI template browser, so they are realistic and contain no personal data.

Feature coverage across the 572 graph templates (counted over top-level and subgraph nodes):

| Shape | Templates | Examples |
|---|---|---|
| Subgraphs (`definitions.subgraphs`) | 198 | `3d_moge_panorama_to_mesh.json`, `Image_capybara_v0_1_image_edit.json` |
| Linked widget inputs (Phase 87 Bug A shape) | 262 | `3d_pixal3d_multi_views.json` |
| Autogrow dotted inputs (Phase 86) | 224 | `api_anthropic_claude.json` |
| Bypassed nodes, mode 4 (Phase 88) | 121 | `api_bfl_flux_1_kontext_max_image.json` |
| Muted nodes, mode 2 (Phase 88) | 2 | `hunyuan_video_text_to_video.json`, `template_image_speech_to_video.json` |
| Reroute (Phase 90) | 18 | `hidream_e1_1.json`, `image_qwen_Image_2512_controlnet.json` |
| PrimitiveNode (Phase 90) | 16 | `audio_ace_step_1_5_checkpoint.json` |
| Note / MarkdownNote | 51 / 378 | most templates |

Sizes range from 2 nodes (`api_bfl_flux3_t2v.json`) to 211 nodes (`templates-1_click_multiple_character_angles-v1.0.json`). There are 801 distinct node types; the full package is 27 MB, so only a curated subset is committed.

**No SetNode/GetNode in any template.** Those come from the KJNodes custom node pack. Phase 90 will need a hand-written fixture for them; this phase doesn't cover them.

**`/object_info` is required.** `convert()` takes a `ComfyObjectInfo`, and widget mapping (Mode B) depends on each node's input definitions. Test fixtures in the repo so far are small hand-written `object_info` fragments. Real templates need real definitions.

**Test infrastructure.** Converter tests live in `app/src/test/java/com/example/comfyui_remote/domain/` (JUnit 4, Gson, `ComfyObjectInfo(JsonParser...asJsonObject)`); see `GraphToApiConverterModeTest.kt`. `ComfyObjectInfo` is a plain data class (`data/ObjectInfoDTOs.kt`), so it runs on the JVM with no Android dependencies. `app/src/test/resources/` does not exist yet; Android unit tests put it on the classpath automatically.

**Environment.** This cloud container can reach PyPI and public GitHub via git, but `download.pytorch.org` is blocked (403) and the user's ComfyUI server is on their LAN. Earlier phases ran Gradle on the user's Windows machine (`gradlew.bat`).
</research>

<decisions>
## Implementation Decisions

- **D-01 Fixtures:** use a curated subset (target 20–30 files, under 5 MB in total) of `comfyui-workflow-templates-json`, pinned to one version. Record the version, and why each file was picked, in `MANIFEST.md`. Include the MIT license text next to the fixtures.
- **D-02 No user workflows:** the user's own workflows (e.g. "Minimax h3 easy i2v.json") are never committed; the prompt text is sensitive, as in Phases 85 and 87.
- **D-03 object_info snapshot:** capture `/object_info` from a stock ComfyUI with no custom nodes, pinned to a commit, and trim it to the node types the fixtures use.
  - Preferred source: run ComfyUI headless on CPU (`--cpu`) and fetch `/object_info`.
  - Fallback, if torch can't be installed within the disk budget: have the user run the same export script against their own server.
  - Either way, replace every combo option list that comes from a model folder (checkpoints, LoRAs, VAEs, etc.) with `[]`, so no local file names are committed. Record the ComfyUI version or commit in `MANIFEST.md`.
- **D-04 Fixture rule:** every non-frontend node type in a selected fixture must exist in the snapshot. Otherwise the check reports a false "missing node" failure.
- **D-05 Structural checks (no server):**
  - (C1) Every output node's `class_type` exists in `object_info`.
  - (C2) Every link input `[id, slot]` points at a node in the output, and `slot` is less than that node's output count.
  - (C3) Each linked output's type matches the input's declared type. `*`, and types joined with commas, count as compatible.
  - (C4) Every required input is present.
  - (C5) Static combo values are in their option list. Empty (model) lists are skipped.
  - (C6) No muted/bypassed node IDs remain in the output.
  - (C7) No frontend-only types remain: Reroute, PrimitiveNode, Note, MarkdownNote.
- **D-06 Known failures:** fixtures that fail today go in `known-failures.json` as fixture → failing check IDs → reason → target phase. The test asserts each listed fixture **still fails with exactly those checks**, so fixing one forces an update to the list. Unlisted fixtures must pass every check. No `@Ignore`.
- **D-07 One test class** that reports every fixture: collect the failures and assert once at the end with a per-fixture report. It must not stop at the first failure.
- Out of scope: converter fixes (Phases 90 and 91), SetNode/GetNode fixtures (Phase 90), live `/prompt` validation, and CI setup.

### Claude's Discretion
- Script language (Python for tooling, Kotlin for tests)
- Helper names
- Exact fixture picks within the coverage targets in `89-01-PLAN.md`
</decisions>

<canonical_refs>
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt`: `convert()`, `resolveRealSource` (phantom passthrough), node modes
- `app/src/main/java/com/example/comfyui_remote/data/ObjectInfoDTOs.kt`: `ComfyObjectInfo` shape
- `app/src/test/java/com/example/comfyui_remote/domain/GraphToApiConverterModeTest.kt`: test style to copy
- `.gsd/milestones/Milestone 3/ROADMAP.md`: Phases 85–88 (the bug shapes this corpus must lock in)
</canonical_refs>
