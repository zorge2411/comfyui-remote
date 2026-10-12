package com.example.comfyui_remote.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/**
 * Why server actions are unavailable (Phase 102). Nothing while connected; "Connecting…" without actions
 * while a connection is being made; otherwise Reconnect and Connection.
 */
@Composable
fun NotConnectedBanner(
    state: WebSocketState,
    onReconnect: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        WebSocketState.CONNECTED -> {}
        WebSocketState.CONNECTING, WebSocketState.RECONNECTING -> StatusBanner(
            StatusKind.Info,
            "Connecting…",
            modifier = modifier,
            message = "Server actions become available once the app is connected."
        )
        WebSocketState.ERROR, WebSocketState.DISCONNECTED -> StatusBanner(
            StatusKind.Warning,
            "Not connected",
            modifier = modifier,
            message = "Server actions are unavailable until the app reconnects.",
            actions = {
                TextButton(onClick = onOpenConnection) { Text("Connection") }
                TextButton(onClick = onReconnect) { Text("Reconnect") }
            }
        )
    }
}

@Preview
@Composable
private fun NotConnectedBannerPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    NotConnectedBanner(WebSocketState.DISCONNECTED, onReconnect = {}, onOpenConnection = {})
}
