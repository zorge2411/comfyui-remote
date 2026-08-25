# Phase 80: Improve Android Icon — Context

**Date**: 2026-02-08
**Domain**: The Android app's launcher icon (adaptive icon: background + foreground vector, mipmap PNGs across densities, round variant, and themed/monochrome variant).

We clarified HOW to implement this. No new capabilities were added beyond the icon redesign itself.

## Current State (for reference)

- Adaptive icon: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` composes a solid-color background (`@color/ic_launcher_background` = `#FF3D5AFE`, "Modern Blue") with a foreground vector (`app/src/main/res/drawable/ic_launcher_foreground.xml`).
- Foreground is a symmetric 8-spoke "node graph" motif: central white circle, 8 radiating connector lines (4 cardinal + 4 diagonal), 8 small dots at the line ends — representing ComfyUI's node-based workflow system.
- Legacy PNG mipmaps exist at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi for both `ic_launcher.png` and `ic_launcher_round.png`.
- No monochrome/themed icon (`ic_launcher_monochrome.xml`) exists — the icon does not adapt to Material You wallpaper theming on Android 13+.

## Decisions

### Visual concept
- **Replace** the node-graph motif entirely (not a refinement).
- New theme: **camera/image-generation motif** — signals "this app makes images," moving away from the node-graph metaphor.
- Specific shape: **aperture / lens** — camera aperture blades or lens-ring glyph. Chosen for a clean geometric silhouette that's simple to render as a vector and reads clearly as "imaging" at a glance.

### Rendering style
- **Keep the existing background**: solid blue `#FF3D5AFE` (`@color/ic_launcher_background`) — no palette change.
- **Foreground**: flat single-color white aperture/lens shape (matches the current minimal, single-fill style — no gradient, no multi-tone blades).
- This is a drop-in replacement for `ic_launcher_foreground.xml` content; the adaptive-icon composition (`ic_launcher.xml`, `ic_launcher_round.xml`) and background color resource stay as-is.

### Themed icon (Android 13+)
- **In scope**: add `ic_launcher_monochrome.xml` — a single-color (theme-tintable) version of the new aperture shape, referenced from `ic_launcher.xml`'s `<monochrome>` element so the icon adapts to Material You wallpaper theming.

## Deferred Ideas
- None raised — user stayed within the icon-redesign scope for this phase.

## Canonical Refs
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon composition, needs `<monochrome>` element added
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — round variant, same composition pattern
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — current node-graph vector, to be replaced with aperture/lens vector
- `app/src/main/res/drawable/ic_launcher_foreground_compat.xml` — legacy-API compat foreground, must match the new design
- `app/src/main/res/values/colors.xml` — `ic_launcher_background` color definition (`#FF3D5AFE`), stays unchanged
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.png` — legacy flattened PNGs, must be regenerated from the new vector for pre-API26 devices

No external specs/ADRs apply — this is a self-contained visual asset change.
