# Milestone: Milestone 4: Workflow Compatibility

## Completed: 2026-09-27

**Goal:** more real-world ComfyUI workflows run unmodified from the phone. When one can't, the app says exactly why, before or after queueing.

## Deliverables

- ✅ Regression corpus: 30 official templates plus the stock `object_info`, run by the converter test suite on every build (Phase 89)
- ✅ Frontend-only and virtual nodes (Reroute, PrimitiveNode, SetNode/GetNode, Note, bypass rules, bypassed/muted subgraph instances) convert as the ComfyUI frontend does (Phase 90)
- ✅ `known-failures.json` is empty. 565 of the 572 official templates convert cleanly, and the other 7 are not converter bugs (Phases 90, 93–95)
- ✅ Pre-flight check against `/object_info` before queueing: missing node types, missing inputs, invalid values and missing files, including files missing because the server's list is empty (Phases 91, 98)
- ✅ Every server `node_errors` entry shown per node; partial acceptance, runtime errors and local-queue failure reasons are reported too (Phase 92)
- ✅ Nice-to-have: browse the server's template library in the app (Phase 96)
- ✅ Beyond the original scope: download missing models to the server from the phone through the new `comfyui_remote_helper` extension, with a managed download queue, a Queue-screen section and notifications (Phases 97, 99)
- ⬜ Nice-to-have not done: compatibility badge on the workflow list (carried over)

## Phases Completed

| Phase | Name | Done | Device check |
|---|---|---|---|
| 89 | Workflow compatibility regression corpus | 2026-09-25 | n/a (test suite) |
| 90 | Frontend-only and virtual node support | 2026-09-26 | ✅ 2026-09-26 |
| 91 | Pre-flight compatibility check | 2026-09-26 | ✅ 2026-09-26 |
| 92 | Full server validation error reporting | 2026-09-26 | ✅ 2026-09-26 |
| 93 | Map subgraph instance inputs by name | 2026-09-25 | covered by the corpus |
| 94 | Support `COMFY_DYNAMICCOMBO_V3` inputs | 2026-09-26 | ✅ 2026-09-26 |
| 95 | Widget mapping gaps for V3 nodes | 2026-09-25 | ✅ 2026-09-27 (Z-Image-Turbo run) |
| 96 | In-app template browser | 2026-09-25 | ✅ 2026-09-27 |
| 97 | Download missing models to the server | 2026-09-26 | ✅ 2026-09-26 |
| 98 | Pre-flight misses missing input files | 2026-09-27 | ✅ 2026-09-27 |
| 99 | Model download queue | 2026-09-27 | ✅ 2026-09-27 |

Phase folders are archived in `Milestone 4/` (96 had no folder; its notes are in `Milestone 4/ROADMAP.md`).

## Metrics

- Commits: 85, from `b24b152` (Milestone 3 archived, 2026-09-25) to the Milestone 4 archive commit.
- App and server code (`app/src/main`, `server/`): 36 files, +3,466 / −184 lines.
- Unit tests (`app/src/test/java`): 21 files, +2,292 lines. There are now 200 JVM tests plus 16 Python tests for the helper extension.
- Test fixtures (`app/src/test/resources`): 36 files, +49,792 lines, mostly the corpus templates and the `object_info` snapshot.
- Duration: 3 days (2026-09-25 to 2026-09-27).
- Room database: version 10 → 12 (`errorMessage` on the local queue, `modelSources` on workflows), migrated in place on the device.

## Lessons Learned

- **A corpus of real templates found more than bug reports did.** Phase 89's baseline turned up three new phases (93–95) and widened 90. Measuring all 572 templates after each phase made progress visible (C5 errors 324 → 1).
- **Read the frontend source, not the saved JSON.** Widget order, control slots, bypass rules and promoted widgets only made sense once the code was followed (`_getBypassSlotIndex`, `_rebindInputSubgraphSlots`, `widgets_values_named`).
- **An empty list is not an unknown list.** The pre-flight guard `options.isNotEmpty()` came from test fixtures and hid real missing files on a server with an empty input folder (Phase 98).
- **Check the obvious server tool before building on it.** ComfyUI-Manager looked like the way to download models, but its catalogue check rejects most template models and v4 refuses installs over `--listen`. A 200-line companion extension was simpler and safer (Phase 97).
- **State that outlives a screen belongs in an app-scoped store.** The "Copy link after minimize" bug came from a view model that was recreated and raced its own settings load. Moving the download state into `ModelDownloadRepository` fixed that class of bug (Phase 99).
- **Device checks over wireless adb caught what unit tests couldn't:** the Phase 97 probe size for gated models, the Hugging Face 401 for missing repos, and the empty-list pre-flight gap.

## Carried Over

- Nice-to-have: a compatibility badge on the workflow list (runs / warnings / will fail), which can reuse `MainViewModel.preflight`.
- Not device-checked: the queue controls hidden on a helper v1 server; the "Browse Templates" button on an empty workflow list; a Gemini, Grok or SaveVideo run (API nodes cost money).
- From Milestone 3 (optional): Phase 82 prompt-first ordering on a real workflow; Phase 80 themed icon under Material You.
