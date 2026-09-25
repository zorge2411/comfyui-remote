# Phase 90: Frontend-Only and Virtual Node Support - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-25
**Phase:** 90-frontend-only-and-virtual-node-support
**Mode:** `--auto`: every area auto-selected, and the recommended option picked for each
**Areas discussed:** Virtual node detection, Phantom passthrough matching, PrimitiveNode value source, SetNode/GetNode matching, Bypass fallback, Unresolved-source investigation

---

## Virtual node detection

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit type set, only when absent from `/object_info` | Reroute, PrimitiveNode, Note, MarkdownNote, SetNode, GetNode | ✓ |
| Keep the heuristic (UUID or contentless) | PrimitiveNode/SetNode have `widgets_values`, so they slip through | |

[auto] Q: "How do we recognise frontend-only nodes?" → Selected: "Explicit type set, only when absent from `/object_info`" (recommended)
[auto] Q: "Should resolved virtual nodes appear in `missingNodes`?" → Selected: "No" (recommended; the rest of the UI stays in Phase 91)

## Phantom passthrough matching

| Option | Description | Selected |
|--------|-------------|----------|
| Type match, then the only input, then drop | `*` counts as a match | ✓ |
| Type match, then the first input | Keeps the current gamble | |

[auto] → Selected: "Type match, then the only input, then drop" (recommended)

## PrimitiveNode value source

| Option | Description | Selected |
|--------|-------------|----------|
| Primitive's `widgets_values[0]` | Same as frontend `applyToGraph` | ✓ |
| Target's own saved widget value | Equal in all 3 corpus fixtures | |

[auto] → Selected: "Primitive's `widgets_values[0]`" (recommended). The target's widget slot is still consumed.

## SetNode/GetNode matching

| Option | Description | Selected |
|--------|-------------|----------|
| By `widgets_values[0]` name over the expanded graph; lowest id wins on duplicates | | ✓ |
| Per subgraph scope | Unclear whether KJNodes scopes names that way | |

[auto] → Selected: first option (recommended). Synthetic tests only; no corpus fixture.

## Bypass fallback

| Option | Description | Selected |
|--------|-------------|----------|
| Frontend order (same index first, then others), type match required, else drop | | ✓ |
| Keep the same-index fallback | Causes the `3d_hunyuan3d` C3 | |

[auto] → Selected: first option (recommended). The researcher checks `ExecutableNodeDTO`.

## Unresolved-source investigation

| Option | Description | Selected |
|--------|-------------|----------|
| Root-cause the 4 templates; fix if bypass/mute/virtual, otherwise file a new phase | | ✓ |
| Fix all of them in this phase | Risk of scope creep | |

[auto] → Selected: first option (recommended)

## Claude's Discretion

- Helper names, and whether a pre-pass or `resolveRealSource` handles primitives and Set/Get.
- Log wording.

## Deferred Ideas

- cg-use-everywhere / rgthree virtual nodes.
- App-side randomisation of a primitive's control value.
- The pre-flight missing-node UI (Phase 91).
