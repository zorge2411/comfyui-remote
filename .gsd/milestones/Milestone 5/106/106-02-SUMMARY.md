# Plan 106.2 Summary: Pinned Group UI

**Completed:** 2026-10-01 (commit `a940e2a`).

## Delivered

- **`PinnableField(pinned, label, onToggle, content)`** in `ui/form/FormSections.kt`:
  - the field, plus a 48 dp pin `IconButton` beside it;
  - Outlined `PushPin` in onSurfaceVariant to pin; filled `PushPin` in primary to unpin;
  - content descriptions "Pin <label>" / "Unpin <label>".
- **Form:**
  - `FormLayout.build` gets `viewModel.pinnedFields(workflow)`, re-laid out when `workflow.pinnedFields` changes;
  - a "Pinned" `SectionHeader` and the pinned fields right under the prompt and negative;
  - node-group fields are wrapped in `PinnableField`;
  - the prompt and Main settings have no pin.
- **State kept:** the form state is keyed on `workflow.id`, so pinning re-lays out without resetting values or the expanded groups.

## Deviations

- **No snackbar on pin/unpin:** it was optional in the plan, and the field visibly moves between sections.

## Verification

- `assembleDebug` OK. On the device in 106.3.
