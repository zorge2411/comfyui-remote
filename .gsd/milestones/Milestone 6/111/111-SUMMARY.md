# Phase 111 Summary: Switch Dynamic Options in the Form

**Status**: Code done; device check pending.

## Delivered

- `domain/DynamicOptionSwitch.kt`: `switchableKeys` (options whose required inputs are all widgets / combos; the current one always included), `switch` (set the key, drop the old `key.*` inputs, add the new option's inputs recursively right after the key, carrying valid shared values by relative path and using spec defaults otherwise; nested combos pick the carried key or the default option; returns dropped links), `keepImages`.
- `InputField.SelectionInput.switchesOptions` (defaulted, so queued items from older builds still load). `WorkflowParser` emits a dynamic combo as a switch when the node list gives at least 2 switchable options.
- `DynamicFormScreen`: `workingJson` + `active` workflow copy used for pre-flight, Generate, Queue, missing-nodes, node sheet and Save-as-Template; the switch injects the current form edits first so shared values carry over; image picks survive; a note shows when connections were dropped; switching is disabled while an upload is in flight. The stored workflow is unchanged.
- Fixed a latent bug found on the way: without the node list, a dynamic combo key with dotted sub-inputs next to it appeared as a free-text field (editing it would send the old option's inputs under a new key). It is now hidden.
- Tests: 11 for the switch (synthetic nested spec; options with required sockets not offered; links dropped; order stays adjacent) plus a snapshot-wide test: every switchable option of every dynamic combo in the stock `object_info` produces no validator issues on its `key.*` inputs. Parser tests updated.

## Device check
1. Open a workflow with SaveVideo (e.g. a text-to-video template), connected: `format` is a dropdown and the `format › …` fields change with it.
2. Edit a sub-field, switch option and back: shared values are kept, new ones show defaults.
3. Generate after switching: the server accepts it and the file matches the container.
4. Add to Queue after switching: the queued item runs with the switched settings.
5. Disconnected: the dynamic combo stays hidden.
6. Reopen the workflow from the list: it shows the saved option again.
