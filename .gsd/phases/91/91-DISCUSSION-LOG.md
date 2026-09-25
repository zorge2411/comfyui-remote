# Phase 91: Pre-flight Compatibility Check - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-25
**Phase:** 91-pre-flight-compatibility-check
**Mode:** `--auto`: every area auto-selected, and the recommended option picked for each
**Areas discussed:** Validator source, File-name checks, When it runs, Block or warn, Presentation, Missing-node list replacement

---

## Validator source

| Option | Description | Selected |
|--------|-------------|----------|
| Move `ApiPromptValidator` to main, share with the corpus test | One set of checks; corpus tests what users see | ✓ |
| Write a separate app-side checker | Two implementations drift apart | |

[auto] → Selected: "Move to main and share" (recommended)
[auto] Q: "How is C6 handled without the graph?" → Selected: "Graph optional; C6 only in the corpus" (recommended)

## File-name checks

| Option | Description | Selected |
|--------|-------------|----------|
| Live mode checks file values, skipping just-uploaded and `image_upload` inputs | Catches missing models, the most common real failure | ✓ |
| Keep skipping all file values | No false positives, but misses missing models | |

[auto] → Selected: "Live mode checks file values with skips" (recommended)

## When it runs

| Option | Description | Selected |
|--------|-------------|----------|
| Form open + queue time, refetch `/object_info` once on issues | Card is current; queue check sees final values | ✓ |
| Queue time only | No warning until the user taps Generate | |
| Import time only (today) | Stale; depends on the server at import | |

[auto] → Selected: "Form open + queue time with one refetch" (recommended)
[auto] Q: "No `/object_info` available?" → Selected: "Skip and queue as today" (recommended)

## Block or warn

| Option | Description | Selected |
|--------|-------------|----------|
| Warn with Queue anyway / Cancel | Server stays the authority; stale info can't block | ✓ |
| Block on missing node types, warn on the rest | Risk of false blocks after a custom-node reload | |
| Log only | Nothing visible | |

[auto] → Selected: "Warn with Queue anyway / Cancel" (recommended)

## Presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Grouped by node (title + type), plain wording, missing types first | Readable; reusable for Phase 92 | ✓ |
| Flat list of check IDs | Developer-oriented | |

[auto] → Selected: "Grouped by node, plain wording" (recommended)
[auto] Q: "Show converter problems (C2/C3/C7)?" → Selected: "One neutral line per node, details in the log" (recommended)

## Missing-node list replacement

| Option | Description | Selected |
|--------|-------------|----------|
| Stop showing the stored string; keep the column | No Room migration | ✓ |
| Drop the column | Needs a migration for no user gain | |

[auto] → Selected: "Stop showing it; keep the column" (recommended)

## Claude's Discretion
- Class/file names, dialog vs bottom sheet, where the form-screen check runs.

## Deferred Ideas
- Full `node_errors` display (Phase 92), one-tap model replacement, ComfyUI-Manager installs, dropping the column.
