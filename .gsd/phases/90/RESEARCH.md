# Phase 90 Research: Frontend-Only and Virtual Node Support

**Date:** 2026-09-25
**Discovery level:** 2. The open questions in `90-CONTEXT.md` (D-08, D-12) were answered from the frontend source.
**Sources:**
- Comfy-Org/ComfyUI_frontend `main`: `src/lib/litegraph/src/subgraph/ExecutableNodeDTO.ts`, `src/utils/executionUtil.ts`, `src/extensions/core/widgetInputs.ts`, `src/extensions/core/widgetValuePropagation.ts`.
- kijai/ComfyUI-KJNodes `main`: `web/js/setgetnodes.js`.

## 1. How `graphToPrompt` builds an input (`executionUtil.ts`)

1. It first calls `applyToGraph()` on every **virtual** node. There's **no mode check**, so muted and bypassed primitives still apply.
2. For each non-virtual node that isn't muted or bypassed:
   - it writes **every serialisable widget value** into `inputs[name]` first;
   - then, for each input slot, `resolveInput(i)`. **Only if the link resolves** does it overwrite `inputs[name]` with `[origin_id, slot]`.
3. Finally it deletes any link input whose origin isn't in the output.

**Consequence (new, refines D-06/D-14):**
- A widget input (slot with a `widget` property) whose link doesn't resolve still sends its **saved widget value**.
- The converter currently sends nothing in that case. This covers links from muted nodes, from bypassed nodes with no matching input, from primitives reached through a Reroute, and from GetNodes with no setter.
- A socket input with an unresolved link sends nothing, as today.

## 2. Resolving an output (`ExecutableNodeDTO.resolveOutput(slot, type)`)

`type` is the **final target input's type**, passed down the whole chain.

| Source node | Rule |
|---|---|
| Muted (mode 2) | `undefined`: drop |
| Bypassed (mode 4) | `_getBypassSlotIndex(slot, type)`, then `resolveInput(index)`. If the index is `-1`, drop. |
| Virtual with `resolveVirtualOutput` (Set/Get across graphs) | Resolve at that source |
| Virtual (Reroute, GetNode in the same graph, PrimitiveNode) | `node.getInputLink(slot)`: the input at the **same index as the output slot**. If there's no link, drop. PrimitiveNode has no inputs, so it always drops, and its value arrives through `applyToGraph` instead. |
| Real node | `[id, slot]` |

**`_getBypassSlotIndex(slot, type)`**:
1. If `type` is `*` or `""`: use `slot` if an input exists at that index, else input 0.
2. If the input at the same index satisfies `isValidConnection(in.type, outputType)` **and** `isValidConnection(in.type, type)`, use it.
3. Otherwise, the first input with `in.type == type` (exact).
4. Otherwise, the first input valid against both `outputType` and `type`.
5. Otherwise `-1`, which drops the link.

A **chosen input that is unlinked** resolves to nothing. The search doesn't move on to another input.

**`LiteGraph.isValidConnection(a, b)`**:
- `""` or `*` on either side matches anything;
- equal types match;
- otherwise compare case-insensitively, splitting each side on `,` and matching when any type appears on both sides.

**Differences from our converter:**
- Today, `bypassInputLink` looks for a type match against the **output** type, skips unlinked inputs, then falls back to the same-index input. The frontend checks against both the output type and the target type, and never falls back. This is the fix for `3d_hunyuan3d_multiview_to_model`.
- Phantom passthrough: the frontend uses the same-index input, not a type search. **This refines D-04:**
  1. use the linked input at the output-slot index;
  2. failing that, for unknown non-virtual phantoms only (UUID or contentless, which aren't real frontend virtual nodes), the first linked input valid for the type;
  3. failing that, drop.
  - Existing `testAmbiguityResolution` (slot 0 → input 0) still holds.

## 3. PrimitiveNode (`widgetInputs.ts`, `widgetValuePropagation.ts`)

- `applyToGraph` takes `widgets[0].value`, and applies text replacement if the property `Run widget replace on values` is set. That's rare; log it and send the raw value. It then writes the value into the widget named `input.widget.name` on every **directly linked** target (`getOutputSlotLinks(node.id, 0)`).
- A target reached **through a Reroute** isn't a direct link, so it isn't updated. Its link then resolves to the primitive, which drops. So the target sends its own saved widget value (§1). In practice the two values are the same.
- D-08 is answered: the value applies whatever the primitive's mode.

## 4. SetNode / GetNode (KJNodes `setgetnodes.js`)

- Both are `isVirtualNode = true`. The name is `widgets[0].value`, i.e. `widgets_values[0]`.
- Same graph: `GetNode.getInputLink(slot)` finds the **first SetNode in `graph._nodes` order** with that name, then returns that setter's `inputs[slot].link`. Resolution then continues upstream with the target type.
- Across graphs: `resolveVirtualOutput` searches the node's own graph, then its parent graphs (a Set in a parent is visible to all descendant subgraphs). If the name is duplicated in that scope, it **drops** the link and shows an alert.
- No setter found: drop and show an alert.

**Refines D-10:**
- Use the first SetNode in **node-array order**, not the lowest id.
- Scoping: after `expandGraph` flattens the graph, look in the Get's own former graph first when that can be told apart. Otherwise use the first match over the flattened graph, and **log** duplicates.
- Full scoping of subgraph ancestors is deferred: no corpus fixture has Set/Get, and Set/Get inside subgraphs is rare.

## 5. Measurement tooling

- The all-572-template run in Phases 94 and 95 was a scratch script and isn't in the repo.
- Templates come from `pip download --no-deps comfyui-workflow-templates-json==<ver>`, then `comfyui_workflow_templates_json/templates/` (see `scripts/corpus/select_fixtures.py`).
- Plan 90.3 adds an opt-in JUnit report (`COMFY_TEMPLATES_DIR`) so the numbers can be reproduced.
