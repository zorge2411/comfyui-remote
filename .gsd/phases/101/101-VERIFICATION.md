## Phase 101 Verification

### Must-Haves

- [x] **The prompt and key settings sit at the top with human labels; other inputs are collapsed per node.** `FormLayoutTest` (5 tests), plus the device check on Z-Image-Turbo.
- [x] **Generate is always visible; progress and result show without scrolling.** Checked on the device, in portrait and landscape.
- [x] **Fixed seeds are respected.** `SeedPolicyTest` (3), plus the server's `/history`: 12345 on two runs; Random gave a new seed, shown as "Last used".
- [x] **Edits are remembered, with a reset.** `FormValuesTest` (3), plus the device check: an edit survived reopening, and Reset cleared `savedInputs`.
- [x] **Database 12 → 13 keeps all workflows.** 8 of 8 on the device.

### Not verified on the device

- The missing-models banner in its new wrapper (no workflow on the phone has missing models).
- Dark theme for the new form (the components are the Phase 100 ones, already checked in dark).

### Verdict: PASS
