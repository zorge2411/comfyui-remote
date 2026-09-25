# Phase 82: Prompt Field Ordering

## Goal Description

Ensure the positive prompt's text field always renders as the very first field in the dynamically-generated workflow form, regardless of where its node falls in the workflow JSON's key order.

Per `82-CONTEXT.md`, the positive prompt is identified via **graph topology**, not title/name matching: find the sampler node's `positive` link and trace it back to the node that produces it. If that can't be done unambiguously, leave the field order exactly as it is today (no guessing).

**Confirmed format (`GraphToApiConverterTest.kt` lines 108-123):** the workflow JSON `WorkflowParser.parse()` consumes is already in ComfyUI API-prompt format — each node is a top-level `{nodeId: {class_type, inputs: {...}}}` entry, and a **linked** input's value is a 2-element `JsonArray` `[sourceNodeId: Int, sourceSlotIndex: Int]` (e.g. `inputs30.get("model")` → `[10, 0]`), while a **widget** input's value is a `JsonPrimitive`. `WorkflowParser.parse()` currently only reads primitives (`if (!fieldValue.isJsonPrimitive) return@forEach`, line 26) — link-typed fields are skipped entirely today. This phase reads them for exactly one purpose: finding the sampler's `positive` link source.

**Sampler detection:** rather than hardcode a class-type allowlist (KSampler, KSamplerAdvanced, SamplerCustom, ...), detect any node whose `inputs` object has both a `positive` key and a `negative` key, each holding a `JsonArray` link. This is the actual ComfyUI convention (CONDITIONING pair) and generalizes to custom/community sampler nodes without a maintained list.

## Proposed Changes

### 1. Add a positive-prompt lookup pass in `WorkflowParser.parse()`

**Modify** `app/src/main/java/com/example/comfyui_remote/domain/WorkflowParser.kt`.

Before the existing node-iteration loop (or in a preliminary pass over the same `jsonObject`), add a helper that scans all nodes for sampler-shaped nodes and collects the resolved positive-link source node ID:

```kotlin
private fun findPositivePromptNodeId(jsonObject: JsonObject): String? {
    val positiveSourceIds = mutableSetOf<String>()
    jsonObject.entrySet().forEach { (_, element) ->
        if (!element.isJsonObject) return@forEach
        val node = element.asJsonObject
        val inputsObj = node.get("inputs")?.asJsonObject ?: return@forEach
        val positive = inputsObj.get("positive")
        val negative = inputsObj.get("negative")
        if (positive != null && positive.isJsonArray && negative != null && negative.isJsonArray) {
            val link = positive.asJsonArray
            if (link.size() >= 1) {
                positiveSourceIds.add(link[0].asString)
            }
        }
    }
    return if (positiveSourceIds.size == 1) positiveSourceIds.first() else null
}
```

Notes:
- `link[0].asString` — GSON's `asString` on a numeric `JsonPrimitive` returns its literal text form (`"10"`), which matches the top-level node-id keys (also strings) used elsewhere in this file (`nodeId` parameter is already `String` throughout). No `Int`/`String` mismatch to handle.
- `positiveSourceIds.size == 1` is the ambiguity gate: zero matches (no sampler-shaped node found) or more than one distinct source (multiple samplers disagreeing) both return `null`, satisfying `82-CONTEXT.md` D-03 (no-reorder fallback).

### 2. Reorder the built `inputs` list before returning

**Modify** the end of `WorkflowParser.parse()` (currently `return inputs` at line 121):

```kotlin
val positiveNodeId = findPositivePromptNodeId(jsonObject)
if (positiveNodeId != null) {
    val (positiveFields, rest) = inputs.partition { it.nodeId == positiveNodeId }
    if (positiveFields.isNotEmpty()) {
        return positiveFields + rest
    }
}
return inputs
```

- Uses `List.partition` (stdlib) — no new dependency.
- If the positive-source node produced zero `InputField`s (e.g. its own `text` field is itself a link to a third node, so nothing primitive was captured for it), `positiveFields` is empty and the original `inputs` order is returned unchanged — same no-reorder fallback as the ambiguous case.
- All fields belonging to the positive-source node move to the front as a group (preserves their relative order), not just a single field — a node could have more than one primitive input (e.g. `text` plus a `token_normalization` combo). D-02 says "the positive prompt field," but grouping avoids splitting one node's fields across the form, which CONTEXT.md doesn't rule out and is the least-surprising behavior.

## Verification Plan

### New unit tests

**Modify** `app/src/test/java/com/example/comfyui_remote/domain/WorkflowParserTest.kt` (exists — currently covers Float input detection, generic loaders, and the Phase 81 `LoadImage` picker fix; uses backtick-named test functions, e.g. `` `parse detects Float inputs`() ``). Follow the same naming/fixture style.

Cover, using synthetic (non-real, non-sensitive) node graphs in the same API-prompt shape as `GraphToApiConverterTest.kt`'s fixtures:

1. **`parse_PositivePromptFoundViaTopology_MovedToFront`** — a workflow with a `KSampler`-shaped node (`inputs.positive`/`inputs.negative` both link arrays) linking to two separate `CLIPTextEncode`-shaped nodes (positive and negative), where the positive-source node is declared *after* other nodes in JSON key order. Assert the returned `List<InputField>` has the positive node's field(s) first, followed by the rest in original order.
2. **`parse_NoSamplerNode_OrderUnchanged`** — a workflow with no node exposing both `positive` and `negative` link inputs (e.g. a plain img2img workflow with just `LoadImage` → some non-sampler node). Assert the returned order exactly matches today's plain JSON-iteration order (same as pre-change behavior).
3. **`parse_MultipleSamplersDifferentPositiveSources_OrderUnchanged`** — two sampler-shaped nodes whose `positive` links point at two *different* source nodes. Assert order is unchanged (ambiguous case, D-03).
4. **`parse_PositiveSourceNodeHasNoPrimitiveFields_OrderUnchanged`** — a sampler-shaped node's `positive` link points at a node whose own `text` input is itself a link (not a primitive), so it produces zero `InputField`s. Assert order is unchanged (the `positiveFields.isEmpty()` fallback path).
5. **Regression** — reuse (or adapt) any existing `WorkflowParser` fixture/test to confirm plain widget-value parsing (seed/steps/cfg/selection/model/image fields) is unaffected by the new pass.

### Regression check

```bash
./gradlew.bat testDebugUnitTest
```

Full suite must stay green, including all existing `GraphToApiConverter*Test` classes and `WorkflowPatchingServiceTest` (unaffected by this change, but confirms no collateral breakage).

### Build verification

```bash
./gradlew.bat assembleDebug
```

`installDebug` / live-device confirmation is **not required** for this phase's own verification — the reordering is pure data-transformation logic fully covered by unit tests. (Phase 81's live-device retest remains separately pending and is not blocked by this phase.)

## Success Criteria

- [ ] `WorkflowParser.parse()` identifies the positive prompt via sampler-node topology (`positive`/`negative` link keys), not title or field-name string matching
- [ ] When identified unambiguously, the positive-source node's field(s) are moved to the absolute front of the returned `List<InputField>`
- [ ] When no sampler-shaped node is found, multiple sampler nodes disagree on the positive source, or the positive-source node yields zero `InputField`s, the original JSON-iteration order is preserved unchanged
- [ ] New unit tests cover all four scenarios above and pass
- [ ] Full `testDebugUnitTest` suite passes (no regressions)
- [ ] `assembleDebug` succeeds
