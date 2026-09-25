# Phase 80: Improve Android Icon

## Goal Description

Replace the current node-graph launcher icon with a camera aperture/lens motif, on the existing blue background, and add Android 13+ themed (monochrome) icon support that is currently missing.

**Context** (see 80-CONTEXT.md):

- Replace `ic_launcher_foreground.xml`'s node-graph motif with an aperture/lens shape.
- Keep the existing adaptive-icon background: `@color/ic_launcher_background` = `#FF3D5AFE`.
- Foreground stays flat single-color white (no gradient/multi-tone).
- Add a new `ic_launcher_monochrome.xml` themed-icon variant, wired into both `ic_launcher.xml` and `ic_launcher_round.xml`.

**Build/tooling note:** `minSdk = 26` (checked in `app/build.gradle.kts`), which is exactly the version where Android's adaptive-icon XML (`mipmap-anydpi-v26/`) became mandatory and authoritative — every supported device resolves the launcher icon from the XML, never from the flattened `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.png` files. Those legacy PNGs are dead weight for this app. No SVG-to-PNG rasterization tool is available in this environment (no ImageMagick/Inkscape/rsvg-convert/cairosvg/sharp), so regenerating them accurately isn't practical here — and isn't needed. **Decision: delete the legacy PNG mipmaps** rather than attempt an approximate re-render that could look inconsistent with the new vector.

## Proposed Changes

### 1. New Aperture/Lens Foreground Vector

**Modify** `app/src/main/java/com/example/comfyui_remote/../../res/drawable/ic_launcher_foreground.xml` (i.e. `app/src/main/res/drawable/ic_launcher_foreground.xml`):

- Replace the existing node-graph `<path>` elements with a camera-aperture/iris glyph: a ring of overlapping angled blade shapes (6–8 blades) forming a circular opening, OR a simpler concentric lens-ring (outer circle stroke + inner circle stroke + small center dot) if the blade geometry proves too fine-detailed at launcher size — pick whichever renders as a clean, unambiguous silhouette at 48dp on-screen size (test by installing and viewing on the physical device, not just in editor preview).
- Keep the existing `108dp x 108dp` viewport (`android:viewportWidth="108"`, `android:viewportHeight="108"`) so it drops into the same adaptive-icon slot without changing `ic_launcher.xml`/`ic_launcher_round.xml` sizing.
- Keep all shapes within the standard adaptive-icon safe zone (centered, roughly the middle 66dp of the 108dp canvas) so nothing clips when the OS applies a circular, squircle, or teardrop mask.
- Fill/stroke color: `#FFFFFF` (matches current style — flat white, no gradient).

### 2. Themed (Monochrome) Icon Variant

**Create** `app/src/main/res/drawable/ic_launcher_monochrome.xml`:

- Same aperture/lens geometry as the new foreground vector, but as a single-path/single-color drawable meant for OS tinting (Android replaces its color with the user's wallpaper-derived theme color at runtime on Android 13+) — fill color can stay `#FFFFFF`, since the system ignores/retints it.
- Same `108dp x 108dp` viewport and safe-zone placement as the foreground vector.

**Modify** `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

- Add `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>` alongside the existing `<background>` and `<foreground>` elements.

**Modify** `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`:

- Same `<monochrome>` addition, mirroring `ic_launcher.xml`.

### 3. Compat Foreground (No Change Needed)

`app/src/main/res/drawable/ic_launcher_foreground_compat.xml` is an `<inset>` wrapper that references `@drawable/ic_launcher_foreground` directly — it automatically picks up the new aperture design once step 1 lands. No edit required; just confirm it still resolves after the foreground vector changes (build check covers this).

### 4. Remove Dead Legacy PNG Mipmaps

**Delete** (unused at runtime on `minSdk = 26+`, per the tooling note above):

- `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`

If any of the now-empty `mipmap-*dpi/` directories end up with no remaining files, remove the empty directory too.

**Verify nothing else references these PNGs directly** (e.g. notification icons, widget previews, splash screens) before deleting — grep the resource name usage across `app/src/main/`:

```bash
grep -rn "mipmap/ic_launcher\b" app/src/main --include="*.xml" --include="*.kt"
```

Only proceed with deletion if the only references are the adaptive-icon XML system itself (which doesn't reference the PNGs — it references the vector drawables).

## Verification Plan

### Build Verification

```bash
./gradlew.bat assembleDebug
```

Must succeed with no errors after the vector/XML changes and PNG deletions.

### Manual/Visual Verification (device already connected: Fairphone 6)

1. Install: `./gradlew.bat installDebug`
2. Confirm the new aperture/lens icon renders correctly on the home screen/app drawer — capture with `adb exec-out screencap -p > icon_check.png` (or equivalent) and visually inspect: correct blue background, white aperture shape, no clipping.
3. If the device launcher supports icon shape settings (circle/squircle/teardrop), spot-check at least one alternate mask shape for clipping at the edges.
4. If the device is on Android 13+ and supports themed icons (Settings → Wallpaper & style → Themed icons, or launcher-specific toggle), enable it and confirm the icon retints to the wallpaper accent color instead of showing blank/broken.
5. Confirm app still launches normally (icon change shouldn't affect `MainActivity` launch behavior).

## Success Criteria

- [ ] `ic_launcher_foreground.xml` shows an aperture/lens shape instead of the node-graph motif, white on the existing blue (`#FF3D5AFE`) background
- [ ] `ic_launcher_monochrome.xml` exists and is wired into both `ic_launcher.xml` and `ic_launcher_round.xml` via `<monochrome>`
- [ ] Themed icon retints correctly under Android 13+ Material You theming (verified on device if OS/launcher supports it)
- [ ] Legacy per-density PNG mipmaps removed (confirmed unused via grep before deletion)
- [ ] `assembleDebug` builds successfully
- [ ] Icon visually confirmed correct on physical device (Fairphone 6), no clipping under adaptive-icon masking
- [ ] App launch behavior unaffected
