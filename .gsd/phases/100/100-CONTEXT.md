# Phase 100: Compatibility Badge on the Workflow List - Context

**Gathered:** 2026-10-12
**Status:** Ready for execution

<domain>
## Phase Boundary

Each workflow card on the Workflows screen shows, at a glance, whether it should run on the connected server: ready, warnings, or will fail (with the first reason). It reuses `PromptValidator`; no new validation rules. Opening the workflow still shows the full detail (the live missing-nodes banner, the pre-flight dialog). Filtering or sorting by compatibility, and badges on server-side files, are not in this phase.

</domain>

<research>
## Findings

- `PromptValidator.validate(prompt, objectInfo)` already returns `Issue`s with `Kind.severity` ERROR / WARNING, and is cheap enough to run per workflow. `MainViewModel.missingNodeTypes(workflow)` already runs it on a stored workflow for the form banner (Phase 91), so validating the stored `jsonContent` directly is an established pattern.
- Live node list: `_nodeMetadata` (`nodeMetadata` StateFlow) is re-fetched on every transition to CONNECTED (`fetchNodeMetadata()`), producing a new `JsonObject` each time. A flow keyed on it recomputes badges on reconnect with no extra trigger.
- Workflows come from `repository.allWorkflows` (`allWorkflows` flow); the list screen renders `WorkflowItem` cards from it.
- Validating the *stored* JSON, not the filled-in form, has one known false positive: image/video/audio inputs in the stored workflow name a file that usually isn't on this server (the form replaces it at run time). Pre-flight already skips those for pending uploads. Model-file mismatches (checkpoints, LoRAs, VAEs) are real and stay.
- The node list can be stale if the server's custom nodes change while connected; reconnecting refreshes it. Acceptable.
- Server-address changes: metadata from the previous server stays in memory until the next connect replaces it, so the badge is only shown while CONNECTED.
</research>

<decisions>
## Decisions

- **D-01** Pure-Kotlin `WorkflowCompatibility.assess(issues)` → `Result(level, summary)`. Levels: `READY` (no issues), `WARNINGS` (warnings only), `WILL_FAIL` (any error). Summary is the first reason in plain words: missing node types listed ("Missing nodes: A, B"), otherwise the first error as "<node title>: <message>", otherwise "N warning(s)". `UNKNOWN` is not a level of the result: no result means no badge.
- **D-02** `WorkflowCompatibility.check(json, objectInfo)` wraps parse + `PromptValidator.validate` and drops `VALUE_NOT_IN_LIST` issues on input names `image`, `video`, `audio` and `mask` (replaced by the form). Returns null for unparseable JSON.
- **D-03** `MainViewModel.compatibility: StateFlow<Map<Long, Result>>` = combine(`allWorkflows`, `nodeMetadata`, `connectionState`), `mapLatest` on `Dispatchers.Default`; empty map unless CONNECTED and metadata non-null. Cached per workflow by `(id, jsonContent.hashCode())` inside the collector so renames and unrelated list changes don't revalidate everything.
- **D-04** UI: a small badge on `WorkflowItem` (check / warning / error icon + text, theme colours: primary / tertiary / error) and, for non-READY, the one-line summary under the name. No badge while disconnected or loading.
- **D-05** Regression guard: a corpus test runs `check` on every converted corpus workflow against the corpus `object_info.json` and asserts none is `WILL_FAIL`, so a validator or converter change that would badge good workflows red fails CI.
</decisions>
