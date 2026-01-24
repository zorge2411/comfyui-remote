# Research Phase 15: Background Persistence

## 1. Current State

- **Service**: `ExecutionService` is a Foreground Service intended to keep the process alive.
- **Manifest**: Declares `foregroundServiceType="dataSync"` (correct for Android 14).
- **Socket**: `ComfyWebSocket` has **NO** reconnection logic. If the connection drops or the server restarts, the app enters an ERROR state and stays there.
- **Compatibility**: `startForeground` in `ExecutionService.kt` uses the old API (< 29), which doesn't specify the `type`. Android 14 requires specifying the type if one is declared in the manifest.

## 2. Requirements

### A. Robust Connectivity

- The app must attempt to reconnect if the WebSocket drops unexpectedly.
- Should use exponential backoff (e.g., 1s, 2s, 5s, 10s...) to avoid hammering the server.
- Should distinguish between "User Disconnect" (intentional) and "Network Error" (unintentional).

### B. Android 14 Compliance

- `ExecutionService` must call `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on Android 14+ (SDK 34).
- Request `FOREGROUND_SERVICE_DATA_SYNC` permission (done in Manifest).

### C. Optional: WakeLock

- To ensure CPU doesn't sleep during long generations while screen is off, a partial WakeLock is recommended.

## 3. Implementation Strategy

### 1. Robust WebSocket (`ComfyWebSocket.kt`)

- Implement a `ReconnectionManager` or simple loop inside the `connect` flow.
- Or, let `ConnectionRepository` handle the retry loop by observing `onFailure`.
- Prefer keeping `ComfyWebSocket` dumb (handle socket) and `ConnectionRepository` smart (handle lifecycle/retry).

### 2. Service Update (`ExecutionService.kt`)

- Update `startForeground` to use the API 29 overload with type.
- Add "Reconnecting" state to notification.

### 3. Repository Logic (`ConnectionRepository.kt`)

- When `WebSocketState.ERROR` or `DISCONNECTED` occurs (and not user initiated), trigger a delayed reconnect.
