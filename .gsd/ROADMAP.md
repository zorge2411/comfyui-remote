# ROADMAP.md

> **Current Milestone**: Milestone 4 (Planned)
> **Goal**: Workflow compatibility — more real-world ComfyUI workflows run unmodified from the phone, and when one can't, the app says exactly why before or after queueing.
> **Previous**: Milestone 3 archived in `.gsd/milestones/Milestone 3/` (summary: `Milestone 3-SUMMARY.md`)

## Must-Haves

- [ ] Regression corpus of representative graph workflows that the converter test suite runs on every build
- [ ] Frontend-only / virtual nodes (Reroute, PrimitiveNode, SetNode/GetNode, Note) convert correctly, with type-aware passthrough
- [ ] Pre-flight check against `/object_info` before queueing (missing node types, missing required inputs, invalid combo values), with no false "missing node" warnings for nodes the converter removes
- [ ] All server `node_errors` shown to the user, per node, not just the first one

## Nice-to-Haves

- [ ] Compatibility badge on the workflow list (runs / warnings / will fail) based on the pre-flight check

## Phases

### Phase 89: Workflow Compatibility Regression Corpus

**Status**: ⬜ Not Started
**Objective**: Build a set of sanitized graph workflow fixtures (no personal prompts, paths or server details) covering the shapes fixed in Milestone 3 (subgraphs, autogrow, bypass/mute, linked widgets, localized combos) plus common community workflows, and a test harness that converts each one and checks the result is structurally valid: every link resolves to a node in the output, no frontend-only nodes remain, and linked types match.

### Phase 90: Frontend-Only and Virtual Node Support

**Status**: ⬜ Not Started
**Objective**: Handle nodes that exist only in the editor. Phantom-node passthrough currently takes the first input link without checking its type (`GraphToApiConverter.resolveRealSource`); make it type-aware like the Phase 88 bypass logic, and resolve SetNode/GetNode pairs, which are linked by name rather than by a graph link.
**Depends on**: Phase 89

### Phase 91: Pre-flight Compatibility Check

**Status**: ⬜ Not Started
**Objective**: Before queueing, validate the converted prompt against the server's `/object_info`: report missing node types, missing required inputs and invalid combo values in the app. Replace the current missing-node list, which can include nodes the converter already removed (e.g. Reroute).
**Depends on**: Phase 90

### Phase 92: Full Server Validation Error Reporting

**Status**: ⬜ Not Started
**Objective**: When `/prompt` returns `node_errors`, show every failing node with its title and type, and all of its errors, instead of only the first error of the first node (`MainViewModel` queue error handling).
