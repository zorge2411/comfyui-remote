# Phase 111: Switch Dynamic Options in the Form - Context

**Gathered:** 2026-10-12
**Status:** Ready for execution

<domain>
## Phase Boundary

In the workflow form, a node's dynamic combo (e.g. `SaveVideo.format`: auto / mp4 / mkv / webm, then its `codec`, then `encoding`) can be switched. Choosing another option rebuilds that option's sub-fields, keeps values for sub-inputs the old and new option share, and sends the new dotted keys. Phase 94 shows the sub-fields of the *saved* option only and hides the combo itself; that stays the behaviour for workflows without a node list (not connected). Linked sub-inputs, socket-requiring options and `control_after_generate` inside options are out of scope.

</domain>

<research>
## Findings

- **Why it's hidden today.** The stored API prompt has `format` plus `format.codec`, `format.codec.encoding`, ... for the selected option only (server `_io.py`: sub-inputs are read as `<id>.<sub>` from the selected key's option). Changing only `format` would send the old option's sub-inputs and omit the new option's required ones, so `WorkflowParser` skips dynamic-combo fields (Phase 94).
- **What switching must do to the prompt:** set `key`, delete every `key.*` input, then add the new option's inputs recursively (required then optional, spec order), each widget with the value carried over from the old option when a sub-input of the same relative path existed and the value is still valid for the new spec, otherwise the spec default. Nested dynamic combos recurse (default option = config `default` if valid, else first). Defaults follow the frontend's (`default`, else 0 / "" / false / first option), as the converter already does.
- **Sockets.** Options can declare socket inputs. In the stock snapshot (14 dynamic combos, 93 options) 1 required socket (`ResizeImageMaskNode`) and 10 optional ones exist, plus 20 autogrow groups. A required socket cannot be filled from the form, so an option that needs one is not offered; optional sockets and autogrow groups are left unlinked (valid: the server treats them as absent). Links the old option had (`key.images` linked) are dropped on switch, and the form says so.
- **Form state.** The form parses its fields from `workflow.jsonContent` once, and Generate / Queue / pre-flight / Save-as-Template pass that workflow. A switch changes the prompt's shape, so the form must keep a *working JSON* and pass `workflow.copy(jsonContent = workingJson)` everywhere; `addToQueue` already snapshots the JSON and the fields separately, so queued items carry the switched prompt. The stored workflow is not modified (a switch is per run, like any other form edit).
- **Field model.** `InputField` is deserialized by label in `QueueViewModel` and matched exhaustively in `WorkflowExecutor`. A new subclass would need new branches in both. Instead `SelectionInput` gets a defaulted `switchesOptions: Boolean = false` (old queue snapshots deserialize to false), and the executor's existing SelectionInput branch just re-writes the same value.
- **Pending uploads.** `pendingImageUploads` is index-based; the field list can shift on a switch, so switching is disabled while an upload is in flight.
</research>

<decisions>
## Decisions

- **D-01** Pure `domain/DynamicOptionSwitch`: `switchableKeys(objectInfo, classType, path, prompt node inputs)` (option keys whose required inputs are all widgets/combos/dynamic combos, plus the current one), `switch(promptJson, nodeId, path, newKey, objectInfo): String?` per the rules above (null if the node/spec/option isn't found or the option needs a connection), and `mergeValues(old, new)` for the field list.
- **D-02** `WorkflowParser`: with metadata, a dynamic combo whose spec has at least 2 switchable options becomes `SelectionInput(switchesOptions = true, options = switchable keys)`; without metadata or with a single option it stays hidden (unchanged). It is placed directly before its sub-fields (JSON order already does this).
- **D-03** `DynamicFormScreen`: `workingJson` state (reset when the workflow changes); a switching selection calls `DynamicOptionSwitch.switch`, re-parses with the node list, merges values, and shows a short note when links were dropped. All calls that take the workflow use the working copy. Disabled while uploads are pending.
- **D-04** Tests: the SaveVideo snapshot cases (auto → mp4 keeps codec "auto"; h264 re-encode → another container keeps `codec`/`encoding`/`crf` when valid; invalid carried value falls back to default); `ResizeImageMaskNode` options needing a socket are not offered; unknown node/option → null; links dropped. A snapshot-wide test: for every dynamic combo and every switchable option, minimal-prompt switching yields no REQUIRED_INPUT_MISSING / VALUE_NOT_IN_LIST / INVALID_VALUE_TYPE from `PromptValidator` on `key.*` inputs.
</decisions>
