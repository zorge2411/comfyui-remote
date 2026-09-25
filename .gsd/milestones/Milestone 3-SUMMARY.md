# Milestone: Milestone 3: Polish & ComfyUI Feature Support

## Completed: 2026-09-25

## Deliverables

- ✅ Graph-format workflow import (`GraphToApiConverter`), including subgraph expansion, UUID node flattening, `COMFY_AUTOGROW_V3` inputs and bypassed/muted node modes
- ✅ Gallery & history sync with the server: startup sync, pull-to-refresh, configurable limits, quick-sync presets, filtered sync with saved lists, clear-and-resync
- ✅ Image-to-image workflows: image upload, gallery/camera picker for `LoadImage`, upload gating and error surfacing
- ✅ MP4/video output support (sync, thumbnails, playback, binary-safe sharing)
- ✅ Batch generation and a persistent local queue
- ✅ Real execution progress indicator (nodes + steps)
- ✅ Resource dropdowns, server workflow population, prompt-first field ordering
- ✅ UX polish: theme mode, connection keyboard handling, back/copy/link-to-gallery actions, new adaptive + themed app icon
- ✅ Real MiniMax H3 easy i2v workflow runs end to end from the phone (Phases 84–88)

## Phases Completed

Phases 29–88 (see `Milestone 3/ROADMAP.md` for the full list, tasks and verification notes). Highlights:

- 30: Graph format JSON import
- 31–32: Gallery & history sync, pull to sync
- 36–39, 64, 81: Image upload and image-to-image support
- 65–70, 85–88: Workflow conversion robustness (flattening, subgraphs, autogrow, widget mapping, node modes)
- 73–74: Batch generation, local queue
- 75: MP4 video support
- 76–79: Sync limits, selective sync, clear & resync, saved filter lists
- 80: Improved Android icon
- 82: Prompt field ordering
- 84: Real progress indicator

## Deferred / Open

- Phase 82 not checked on device (unit-tested only)
- Phase 80 themed-icon retinting on Android 13+ not spot-checked
- Must-Haves were never formally defined for this milestone (`TBD`); deliverables above are reconstructed from completed phases

## Metrics

- Duration: 2026-01-24 → 2026-09-25
- Phases: 29–88
- Focus: Workflow compatibility, sync reliability, UX polish

## Lessons Learned

- Real workflows surface bugs synthetic tests miss: Phases 85–88 were each found by running a real workflow on the device, one failure at a time.
- Real ComfyUI servers declare `LoadImage.image` as a combo of server files; field detection must check the node type before generic combo metadata (Phase 81).
- Metadata caches must refresh on every reconnect, not only on explicit connect (Phase 81 UAT).
