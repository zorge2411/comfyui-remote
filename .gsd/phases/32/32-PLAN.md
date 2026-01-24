# Phase 32: Pull to Sync Gallery & History

**Objective**: Implement pull-to-refresh on Gallery and History screens.

## Technical Design

### ViewModel State

Add the following to `MainViewModel`:

```kotlin
private val _isSyncing = MutableStateFlow(false)
val isSyncing = _isSyncing.asStateFlow()
```

Update `syncHistory()`:

```kotlin
fun syncHistory() {
    viewModelScope.launch {
        _isSyncing.value = true
        try {
            // ... existing sync logic ...
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }
}
```

### UI Implementation

Use `PullToRefreshBox` around the list/grid in `GalleryScreen` and `HistoryScreen`.

## Tasks

- [ ] Implement `isSyncing` state in `MainViewModel`
- [ ] Update `syncHistory` with state management
- [ ] Add `PullToRefreshBox` to `GalleryScreen`
- [ ] Add `PullToRefreshBox` to `HistoryScreen`
- [ ] Verify functionality
