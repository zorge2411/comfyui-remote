# Phase 31: Sync Gallery & History

> **Status**: PLAN APPROVED
> **Objective**: Synchronize local gallery with server history on startup.

## Problem

The app's gallery is only populated by images generated during the current session or manually saved. It references remote files but stores metadata locally. If the user generates images elsewhere or restarts the app, the gallery is empty despite the server having history.

## Solution

Trigger a sync process on connection that:

1. Fetches full history from `GET /history`.
2. Compares with local database to find missing items.
3. Inserts missing items into `GeneratedMediaEntity`, calculating the correct URL for `AsyncImage` to load.

## Technical Design

### 1. Data Layer (`GeneratedMediaDao` & `MediaRepository`)

Add an optimized query to fetch only existing IDs for quick comparison.

```kotlin
// GeneratedMediaDao.kt
@Query("SELECT promptId FROM generated_media WHERE promptId IS NOT NULL")
suspend fun getAllPromptIds(): List<String>
```

```kotlin
// MediaRepository.kt
suspend fun getAllPromptIds(): List<String> = mediaDao.getAllPromptIds()
```

### 2. ViewModel Layer (`MainViewModel`)

Implement `syncHistory()` logic:

```kotlin
fun syncHistory() {
    viewModelScope.launch {
        // Optimize: Fetch all IDs first so we do 1 read instead of N reads or N failed inserts
        val existingIds = mediaRepository.getAllPromptIds().toSet()
        val history = api.getHistory()
        
        history.forEach { (id, data) ->
             if (id !in existingIds) {
                 // Extract filename, subfolder, type
                 // Insert into DB
             }
        }       
    }
}
```

### 3. Trigger Points

- Call `syncHistory()` inside `connect()` or when `WebSocket` state becomes `Connected`.
- Consider run-once-per-session flag to avoid spamming if getting disconnected frequently? No, `existingIds` check makes it cheap.

## Verification Plan

### Automated

- None (Integration/UI feature).

### Manual

1. **Pre-condition**: App has 0 images. Server has 5 history items.
2. **Action**: Open app, connect.
3. **Observation**: Gallery tab shows 5 images.
4. **Action**: Generate new image on Desktop. Refresh App (or Reconnect).
5. **Observation**: Gallery shows 6 images.
