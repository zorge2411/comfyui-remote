# Phase 54: Icon Refinement II

**Goal**: Upgrade the app icon implementation to support Android Adaptive Icons (Android 8.0+), fixing the discrepancy between the legacy PNG assets and the unused vector source, and ensuring a consistent premium appearance across all devices.

## User Review Required

> [!WARNING]
> **Adaptive Icon Gap**: The app currently lacks an adaptive icon configuration (`mipmap-anydpi-v26`). This means newer Android devices are managing legacy icons, which may result in suboptimal display (e.g., mismatched shapes).
>
> **Asset Mismatch**: The current high-quality "Premium C" PNG icons do not match the simple green "C" defined in `ic_launcher_foreground.xml`. The plan assumes the "Premium C" (PNG) is the desired look.

## Proposed Changes

### 1. Audit & Setup

- [ ] Confirm `mipmap-anydpi-v26` is missing (Verified).
- [ ] Create missing directory structure.

### 2. Implement Adaptive Icons

- [NEW] `app/src/main/res/values/colors_icon.xml`
  - Define constant for the icon background color (extracted from the PNG edge).
- [NEW] `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`
  - Define `<adaptive-icon>` elements.
  - **Strategy**: Use a localized transparent version of the "Premium C" (or the full PNG scaled within the safe zone) as the foreground, and a matching solid color as background.
  - *Note*: If the current PNGs have no transparency (are solid squares), we will need to create a new foreground drawable or modify the existing `ic_launcher_foreground.xml` to match the premium design (if possible via vector) or use a high-res bitmap.

### 3. Upgrade Vector Source (Optional/Investigate)

- [MODIFY] `app/src/main/res/drawable/ic_launcher_foreground.xml`
  - Attempt to align the vector definition closer to the premium look if creating a purely vector-based adaptive icon is feasible.
  - Otherwise, replace with a roadmap item to commission a proper SVG of the premium design.

## Verification Plan

### Automated Tests

- None (UI visual change).

### Manual Verification

- **Pixel 8 / Emulator (Android 14)**: Verify icon supports theming (if implemented) and adaptive shapes (squircle, circle, etc.).
- **Legacy Device (Android 7 or lower)**: Verify legacy PNGs still load correctly.
- **Visual Check**: Ensure the adaptive icon matches the legacy icon in style and quality.
