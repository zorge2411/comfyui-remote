# Bolt's Journal - Critical Learnings

## 2024-05-23 - [Initialization]
**Learning:** Initialized Bolt's journal.
**Action:** Record only critical performance insights here.

## 2024-05-23 - [Date Formatting Optimization]
**Learning:** Found `SimpleDateFormat` being instantiated inside a Composable during recomposition. This is an expensive operation. `java.time.format.DateTimeFormatter` is immutable and thread-safe, making it suitable for static/shared reuse.
**Action:** Always prefer `java.time` APIs and static formatters over `SimpleDateFormat` in hot paths or UI code.

## 2024-05-23 - [TimeZone Caching Pitfall]
**Learning:** Defining `static final` (or companion object) `DateTimeFormatter` with `.withZone(ZoneId.systemDefault())` caches the timezone at class initialization. If the user changes the device timezone, the app will continue to use the old one.
**Action:** Define static formatters *without* a zone (just the pattern). Apply `.withZone(ZoneId.systemDefault())` dynamically at the call site for `Instant`, or rely on `LocalDateTime.now()` which implicitly uses the current system zone.

## 2024-05-24 - [LaunchedEffect Scroll Thrashing]
**Learning:** Found `LaunchedEffect(gridState.firstVisibleItemIndex)` being used to trigger side effects (preloading). This restarts the coroutine on every scroll frame, causing significant overhead and defeating the purpose of `snapshotFlow`.
**Action:** Use `LaunchedEffect(Unit)` or stable keys, and rely on `snapshotFlow` within the effect to observe changing state without restarting the job.
