# Phase 20: Package Updates & Compatibility

> **Status**: 🔄 In Progress
> **Objective**: Update all project dependencies to their latest stable versions and ensure the application remains functional and compatible with recent Android/Compose changes.

## 1. Strategy

We will update dependencies in a bottom-up approach, starting with the build system (AGP, Kotlin) and then moving to libraries. The most significant change is the migration to Kotlin 2.0, which requires switching from the deprecated `kotlinCompilerExtensionVersion` to the new Compose Compiler Gradle Plugin.

## 2. Dependency Updates

### Build System & Core

- **Android Gradle Plugin (AGP)**: `8.4.1` -> `8.7.3` (or latest stable 8.8.x)
- **Kotlin**: `1.9.24` -> `2.1.0`
- **KSP**: `1.9.24-1.0.20` -> `2.1.0-1.0.29`
- **Target/Compile SDK**: `34` -> `35`

### AndroidX & Compose

- **Compose BOM**: `2024.05.00` -> `2024.12.01`
- **Activity Compose**: `1.9.0` -> `1.10.0`
- **Navigation Compose**: `2.7.7` -> `2.8.5`
- **Room**: `2.6.1` -> `2.6.1` (Check for updates, 2.6.1 is likely stable)
- **Lifecycle Runtime Ktx**: `2.8.0` -> `2.8.7`
- **Core KTX**: `1.13.1` -> `1.15.0`

### Third Party

- **Coil**: `2.7.0` -> `2.7.0` (Stay on 2.x for now to avoid major refactor to Coil 3 unless necessary)
- **Retrofit**: `2.11.0` -> `2.11.0`
- **OkHttp**: `4.12.0` -> `4.12.0`

## 3. Implementation Steps

### Step 1: Kotlin 2.0 Migration

- Remove `composeOptions { kotlinCompilerExtensionVersion = ... }` from `app/build.gradle.kts`.
- Add `alias(libs.plugins.compose.compiler)` to `app/build.gradle.kts`.
- Define the plugin in `libs.versions.toml`:

  ```toml
  [plugins]
  compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
  ```

### Step 2: Version Bumps

- Update `libs.versions.toml` with new versions.
- Update `app/build.gradle.kts` SDK versions.

### Step 3: Verification

- Sync Gradle.
- Clean and Rebuild.
- Fix any API changes/deprecations.
- Run UI tests to ensure Compose still renders correctly.

## 4. Risks & Mitigations

- **Kotlin 2.0 Stability**: While stable, might introduce subtle compiler behavior changes. we will rely on full rebuilds and testing.
- **Compose Compat**: Kotlin 2.0 decouples Compose Compiler from Kotlin version, which is good, but we must ensure the plugin is applied correctly.
