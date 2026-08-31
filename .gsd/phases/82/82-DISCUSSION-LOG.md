# Phase 82: Prompt Field Ordering - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-31
**Phase:** 82-Prompt Field Ordering
**Areas discussed:** Detection method, Scope of "topmost", Fallback behavior

---

## Detection method

| Option | Description | Selected |
|--------|-------------|----------|
| Graph topology | Trace the link into KSampler's `positive` input back to the producing text node; works on any workflow regardless of naming | ✓ |
| Title/name heuristic | Match node title/field name against known patterns like "positive"/"text" | |
| Both — topology first, heuristic fallback | Try topology, fall back to heuristic if links aren't found | |

**User's choice:** Graph topology (recommended option)
**Notes:** None given beyond selecting the recommendation.

---

## Scope of "topmost"

| Option | Description | Selected |
|--------|-------------|----------|
| Absolute top of form | Positive prompt is always the first field shown, everything else follows | ✓ |
| Ahead of negative prompt only | Positive prompt moved just before negative prompt; other fields unchanged | |

**User's choice:** Absolute top of form
**Notes:** None.

---

## Fallback behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Leave order unchanged | If topology can't confidently find one positive prompt, don't reorder | ✓ |
| Best-effort title heuristic | Fall back to title/name matching when topology comes up empty | |

**User's choice:** Leave order unchanged (recommended option)
**Notes:** Predictability preferred over best-effort guessing.

---

## Claude's Discretion

- Exact sampler-node identification and link-traversal algorithm (research/planning to determine, informed by `GraphToApiConverter.kt`'s existing link-resolution logic).
- Precise definition of "ambiguous" that triggers the no-reorder fallback.

## Deferred Ideas

None — discussion stayed within phase scope.
