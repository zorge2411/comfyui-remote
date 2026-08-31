# Phase 82: Prompt Field Ordering - Context

**Gathered:** 2026-08-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Ensure the positive prompt text input always renders as the topmost field in the dynamically-generated workflow form, regardless of the order nodes appear in the workflow's JSON. Today, `WorkflowParser.parse()` (`app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt`) builds the `InputField` list purely by iterating the workflow JSON's node keys in file order — there is no concept of "positive" vs "negative" prompt anywhere in the codebase (confirmed via grep: zero matches for "positive"/"negative" outside this discussion). This phase adds that concept and uses it to reorder one field.

</domain>

<decisions>
## Implementation Decisions

### Detection method
- **D-01:** Identify the positive prompt via **graph topology**, not title/name string-matching. Trace the link feeding a sampler node's (KSampler or equivalent) `positive` input back to the text-producing node (CLIPTextEncode or similar) that supplies it. The negative prompt is found the same way via the sampler's `negative` input key. This mirrors the link-following approach `GraphToApiConverter.kt` already uses for subgraph expansion — reuse that pattern/utilities where applicable.
- Rejected: title/field-name heuristics (e.g. matching on "positive" in node title, or field name `text`) — explicitly rejected as the primary mechanism because it silently breaks on workflows with custom/renamed node titles. Not used even as a secondary fallback (see D-03).

### Scope of "topmost"
- **D-02:** The positive prompt becomes the **absolute first field** in the entire form — above model selectors, seed, steps, CFG, negative prompt, everything. Not just "ahead of the negative prompt while other fields stay put." All other fields retain their existing relative order (today's JSON-iteration order) after the positive prompt is pulled to the top.

### Fallback behavior
- **D-03:** If topology detection can't confidently identify a single positive prompt (no sampler node found, unusual/custom sampler, multiple samplers each with their own positive prompt, etc.), **leave field order unchanged** — render in today's original JSON order. Do NOT fall back to a title/name heuristic guess. Predictability over best-effort guessing was the explicit preference.

### Claude's Discretion
- Exact algorithm for "sampler node" identification (e.g. which class types count as samplers, how to walk `inputs` link references back through the graph) is left to research/planning — the topology-tracing decision is locked, the traversal implementation is not.
- How ambiguity is detected (e.g. what exactly counts as "multiple samplers with different positive prompts" triggering the no-reorder fallback) is left to planning, guided by D-03's intent: when in doubt, don't reorder.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing field-ordering / parsing logic
- `app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt` — current `parse()` builds `List<InputField>` purely from JSON key iteration order (lines 9-122); this is what needs the positive-prompt reorder applied, likely as a post-processing step after the existing loop, or by tracking node/sampler links during the same pass.

### Existing link-tracing precedent (topology approach)
- `app/src/main/java/com/example/comfyui_remote/domain/GraphToApiConverter.kt` — already does link-following / subgraph expansion (see Phase 85's fix around `isSubgraph`/`expandGraphOnce`, ~line 502, and `convert()`'s pre-scan ~line 78-79). Whatever link-tracing utilities exist here should be reused/adapted rather than reimplemented from scratch for finding the sampler's `positive` link target.

No external specs/ADRs for this phase — requirements fully captured in decisions above (originates from a plain TODO item, not a written spec).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GraphToApiConverter.kt`'s link-resolution logic (used for subgraph flattening in Phase 85) is the closest existing precedent for "given a node input that's a link reference `[nodeId, slotIndex]`, resolve to the producing node." Should inform how positive-prompt topology tracing is implemented.
- `InputField.StringInput` (referenced in `WorkflowParser.kt` line 102-108) is the current representation for prompt-like text fields — no subtype currently distinguishes "positive prompt" from other strings.

### Established Patterns
- `WorkflowParser.parse()` returns a flat `List<InputField>` in JSON-iteration order; `DynamicFormScreen.kt` renders that list directly in order (per Phase 81 investigation, no separate sort/reorder step exists today). The reorder logic will need to either mutate this list post-construction or track "this is the positive prompt node ID" during the parse pass and hoist it at the end.

### Integration Points
- `WorkflowParser.kt::parse()` is the natural integration point — same file already builds the `InputField` list and has access to the full node graph (`jsonObject`) needed to trace sampler links.

</code_context>

<specifics>
## Specific Ideas

No specific worked examples given beyond the TODO's original wording: "positive prompt text input must be the most top one in workflow." The topology-based approach was chosen specifically so it generalizes across the user's various workflow files without per-workflow tuning.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (Multi-sampler / multi-positive-prompt workflows were discussed only as a fallback-triggering edge case, not as a capability to build — see D-03.)

</deferred>

---

*Phase: 82-Prompt Field Ordering*
*Context gathered: 2026-08-31*
