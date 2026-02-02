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

## 2024-05-24 - [Main Thread Blocking in Init]
**Learning:** Found heavy JSON parsing in `MainViewModel.init` running on the main thread (via `viewModelScope` default dispatcher). This delays app startup and UI responsiveness.
**Action:** Always offload file I/O and heavy parsing (like Gson) to `Dispatchers.IO`, even in `init` blocks.

## 2024-05-24 - [Repeated JSON Parsing in WebSocket Handler]
**Learning:** `MainViewModel.handleMessage` (running on Main thread) was parsing the entire workflow JSON (O(N)) for every "executing" message to find a single node title. This caused UI jank during generation.
**Action:** Implemented an in-memory cache for parsed node titles keyed by workflow reference. This reduces the operation to O(1) for subsequent updates. Always cache heavy parsing results if they are needed frequently on the main thread.
