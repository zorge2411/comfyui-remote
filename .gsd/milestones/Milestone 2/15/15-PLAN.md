---
phase: 15
plan: 1
wave: 1
---

# Plan 15.1: Background Service & Connectivity

## Objective

Ensure the application maintains a persistent connection to ComfyUI for long-running generations, even when the app is in the background or the screen is off. Implement Android 14 compliant foreground service and robust WebSocket reconnection logic.

## Context

- .gsd/SPEC.md
- .gsd/phases/15/RESEARCH.md
- app/src/main/java/com/example/comfyui_remote/service/ExecutionService.kt
- app/src/main/java/com/example/comfyui_remote/data/ConnectionRepository.kt
- app/src/main/java/com/example/comfyui_remote/network/ComfyWebSocket.kt

## Tasks

<task type="auto">
  <name>Upgrade ExecutionService for Android 14</name>
  <files>app/src/main/java/com/example/comfyui_remote/service/ExecutionService.kt</files>
  <action>
    Update `startForeground` to use the API 29+ signature with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`.

    1. Import `android.content.pm.ServiceInfo`.
    2. Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`.
    3. Call `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`.
  </action>
  <verify>Build verification (`assembleDebug`) to ensure no API compatibility errors.</verify>
  <done>Service executes without crashing on Android 14+.</done>
</task>

<task type="auto">
  <name>Implement Connection Resilience</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/data/ConnectionRepository.kt
    app/src/main/java/com/example/comfyui_remote/network/ComfyWebSocket.kt
  </files>
  <action>
    Add auto-reconnection logic when the WebSocket drops unexpectedly.

    1.  **ConnectionRepository.kt**:
        -   Track `isUserDisconnected` boolean.
        -   Launch a monitoring coroutine that observes `connectionState`.
        -   If `state == DISCONNECTED` or `ERROR`, AND `!isUserDisconnected`:
            -   Wait for a delay (exponential backoff: 1s, 2s, 5s, 10s).
            -   Call `connect()` again.
            -   Reset backoff on successful connection.
    
    2.  **ComfyWebSocket.kt**:
        -   Ensure `onFailure` and `onClosing` correctly emit states that `ConnectionRepository` can react to.
  </action>
  <verify>
    Manual Verification:
    1. Connect to server.
    2. Stop the server (simulate drop).
    3. Observe app state (should show "Connecting..." or similar).
    4. Restart server.
    5. App should auto-reconnect without user intervention.
  </verify>
  <done>App automatically reconnects after network/server failure.</done>
</task>

<task type="auto">
  <name>Update Service Notification</name>
  <files>app/src/main/java/com/example/comfyui_remote/service/ExecutionService.kt</files>
  <action>
    Reflect the "Reconnecting" state in the foreground notification.

    1. Update `buildNotification` to handle a potential `RECONNECTING` state (or map `CONNECTING` to "Reconnecting..." if it's a retry).
    2. Ensure the notification updates dynamically as the repository state changes.
  </action>
  <verify>Manual check of notification tray during reconnection simulation.</verify>
  <done>Notification accurately reflects "Connecting" or "Reconnecting" status.</done>
</task>

## Success Criteria

- [ ] App runs in background without being killed by OS (Foreground Service active).
- [ ] WebSocket reconnects automatically if the server restarts.
- [ ] No crashes on Android 14 due to missing foreground service type.
