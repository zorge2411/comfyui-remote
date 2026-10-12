# Milestone: Milestone 4: Workflow Compatibility

## Completed: 2026-10-12

**Goal:** more real-world ComfyUI workflows run unmodified from the phone, and when one can't, the app says exactly why before or after queueing.

## Deliverables

- ✅ Regression corpus: 32 official ComfyUI templates (MIT), a stock-ComfyUI `/object_info` snapshot, and a corpus test that converts and checks every fixture on each build. `known-failures.json` is empty.
- ✅ Frontend-only and virtual nodes: Reroute, PrimitiveNode, KJNodes Set/Get and Note resolved like the ComfyUI frontend, never sent to the server.
- ✅ Pre-flight check: `PromptValidator` mirrors the server's validation and runs before Generate and Queue, with Queue anyway / Cancel. The missing-node banner is live.
- ✅ Full server error reporting: every failing node and reason by title, partial-acceptance warnings, runtime errors, and failure reasons for local-queue items.
- ✅ Nice-to-have: in-app template browser (Phase 96).

## Phases Completed

| Phase | What | Verified |
|---|---|---|
| 89 | Workflow compatibility regression corpus | JVM |
| 90 | Virtual nodes, frontend bypass rule, bypassed/muted subgraph instances | device 2026-10-12 |
| 91 | Pre-flight compatibility check | device 2026-10-12 |
| 92 | Full server error reporting (Room 10 → 11) | device 2026-10-12 |
| 93 | Subgraph instance inputs by name; promoted widget values | JVM |
| 94 | `COMFY_DYNAMICCOMBO_V3` inputs | device 2026-09-26 |
| 95 | Widget mapping for V3 nodes (control widgets, named values, defaults) | device 2026-10-12 |
| 96 | In-app template browser | device 2026-10-12 |

## Metrics

- Duration: 2026-09-25 → 2026-10-12
- All 572 official templates against stock v0.37.2 `/object_info`, prompts passing every structural check: **123 → 565**. The other 7 aren't converter bugs: 2 need a custom node, 4 leave a required input unconnected in the template, 1 saved a value the node no longer offers.
- Violations across the templates: missing inputs 1595 → 4, invalid values 324 → 1, editor-only nodes sent 29 → 0, wrong-type links 4 → 0.
- App/test changes since the milestone started: about 44 files, +6,800/−1,850 lines. The test suite grew from 23 to 100+ tests.

## Lessons Learned

- **Read the frontend source.** The ComfyUI frontend and server are the authority for graph semantics. Almost every bug in this milestone was found by reading `ExecutableNodeDTO`, `SubgraphNode`, `dynamicWidgets` or `execution.py`, not by guessing.
- **Measure on the whole template set, not just fixtures.** The all-template runs showed the real size of each problem (e.g. dynamic combos in 401 of 572 templates) and found bugs the 32-file corpus hid. Extend the checker before fixing: the first baseline understated Phase 94 by 14 fixtures.
- **`/object_info` order is load-bearing.** Widget values map to inputs in the server's order, so a snapshot saved with sorted keys breaks every node.
- **A schema bump needs a migration.** The database uses `fallbackToDestructiveMigration`, so a version change without an explicit migration wipes users' workflows.
- **Plain-JVM test runs are possible without the Android SDK** by compiling the pure-Kotlin converter and validator sources, which made it practical to iterate on conversion logic in a cloud session.

## Deferred / Candidates for Milestone 5

- Compatibility badge on the workflow list (runs / warnings / will fail), reusing the pre-flight check.
- Switching a dynamic-combo option (e.g. video format) in the app; the key is currently fixed.
- PrimitiveNode text replacement (`%date%`-style) and legacy Group Nodes.
- Custom widget kinds (COLOR, BOUNDING_BOX, VIDEO_EDIT, CURVE, …) keep positional handling.
- Local-queue items are checked when added, not again when they run.
