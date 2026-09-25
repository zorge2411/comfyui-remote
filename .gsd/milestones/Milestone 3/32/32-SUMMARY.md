# Phase 32 Summary: Pull to Sync Gallery & History

Implemented pull-to-refresh functionality on both the Gallery and History pages to allow users to manually trigger synchronization with the ComfyUI server.

## Changes

### MainViewModel

- Added `isSyncing` StateFlow to track synchronization progress.
- Updated `syncHistory()` to manage `isSyncing` state using a `finally` block for reliability.

### UI

- **GalleryScreen**: Replaced the main `Box` with `PullToRefreshBox`, connecting it to `viewModel.isSyncing` and `viewModel.syncHistory()`.
- **HistoryScreen**: Replaced the main `Box` with `PullToRefreshBox`, connecting it to `viewModel.isSyncing` and `viewModel.syncHistory()`.

## Verification Details

- Verified `isSyncing` state transitions in `MainViewModel`.
- Verified `PullToRefreshBox` integration in both screens.
- Verified that `onRefresh` triggers `syncHistory()`.
