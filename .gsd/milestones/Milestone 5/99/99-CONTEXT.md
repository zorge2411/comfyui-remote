# Phase 99: CI and Repo Hygiene - Context

**Gathered:** 2026-10-12
**Status:** Ready for execution

<domain>
## Phase Boundary

Unit tests and the debug build run automatically on every push and pull request; stray log files leave version control; the README says how to run the tests. No new test content, no release signing/publishing, no lint gate (can be a later phase).

</domain>

<research>
## Findings

- No `.github/` directory. Build: AGP 8.7.3, Gradle 8.9 wrapper (`gradlew` tracked, mode 755, wrapper jar tracked), Kotlin 2.1.0, KSP, compileSdk 35. Needs JDK 17.
- Tests are plain JVM unit tests (`testDebugUnitTest`, `isReturnDefaultValues = true`), including the 572-template corpus (~1.2 MB of resources). No instrumented tests run in CI.
- Hosted `ubuntu-latest` image (checked today, runner-images Ubuntu 24.04): Android SDK preinstalled at `ANDROID_HOME`, platform `android-35` and build-tools 35.0.x present, default Java 17 (21 and 25 also installed, so set Java explicitly).
- Current action majors (checked on GitHub releases today): `actions/checkout` v7, `actions/setup-java` v6, `gradle/actions` (setup-gradle) v6, `actions/upload-artifact` v7. `setup-gradle` validates the wrapper jar and caches Gradle by default.
- Nine tracked log files in the repo root (`build_run_log.txt`, `debug_fix_verification_log.txt`, `final_verification_log.txt`, `normalization_build_log.txt`, `test_log.txt`, `test_log_2/3/4.txt`, `test_run_log.txt`, ~24 KB total). Nothing references them. `.gitignore` has `*.log` and `build_log*.txt`, which match none of them.
- README has no build/test section.
- I can't run GitHub Actions or Gradle with the Android SDK here, so the first CI run is the real test; the plan includes reading its result through the GitHub tools and fixing it.
</research>

<decisions>
## Decisions

- **D-01** One workflow `.github/workflows/android.yml`: triggers `push` and `pull_request`; `concurrency` cancels superseded runs per ref; `permissions: contents: read`; `timeout-minutes: 30`.
- **D-02** Steps: checkout → setup-java (temurin 17) → setup-gradle → `./gradlew testDebugUnitTest assembleDebug --stacktrace`. Upload `app/build/reports/tests` and `app/build/test-results` on failure; upload the debug APK on success (retention 7 days) so a phone install doesn't need a local build.
- **D-03** Pin by major tag (v7/v6/v6/v7), not SHA: the repo is personal and the actions are first-party or Gradle's own; pinning by SHA would add upkeep. Mention in the summary.
- **D-04** Remove the 9 log files with `git rm`; widen `.gitignore` to `*_log.txt`, `*_log_*.txt`, `test_log*.txt`, `*_verification_log.txt`. Leave `app/release/` (baseline-profile output) and the agent/tooling directories alone: out of scope, mention in summary.
- **D-05** README gets "Building and testing": prerequisites (JDK 17, Android SDK), the commands for Windows and Linux/macOS, a single test class / the corpus run, what the corpus and `known-failures.json` are (must stay `{}`; a new failure either gets fixed or recorded), where reports land, and the CI badge/APK artifact.
</decisions>
