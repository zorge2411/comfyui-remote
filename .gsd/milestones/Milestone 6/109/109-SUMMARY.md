# Phase 109 Summary: CI and Repo Hygiene

**Status**: Done. First CI run (run 1, 4 min 7 s) green on the first try.

## Delivered

- `.github/workflows/android.yml`: on push and pull request; JDK 17 (Temurin), `./gradlew testDebugUnitTest assembleDebug --stacktrace`; concurrency cancels superseded runs; read-only permissions; test reports uploaded on failure, debug APK (`app-debug`, ~21 MB, 7 days) on success. Actions: checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7 (current majors, checked 2026-10-12), pinned by major tag.
- The 9 tracked root log files removed; `.gitignore` now covers `*_log.txt`, `*_log_*.txt`, `test_log*.txt`, `*_verification_log.txt`.
- README "Building and testing": prerequisites, commands, corpus and `known-failures.json`, CI/APK artifact.

## Notable

- This is the first time the whole project has been compiled in the cloud: the green run confirms that the Phase 107 and 108 Android code (MainViewModel, screens, Room 12 schema, dialogs) **compiles** and that every unit test passes. The Phase 107/98 device checks are still needed for behaviour.
- A CI-built debug APK can be downloaded from the run's Artifacts for installing on the phone, with no local build.

## Left alone
`app/release/` (baseline-profile output) and ~40 tracked agent-tooling files (`.agent/`, `.gemini/`, `.jules/`, `.snapshots/`).
