package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/** The short state label used in top bars (Phase 102). */
fun connectionLabel(state: WebSocketState): String = when (state) {
    WebSocketState.CONNECTED -> "Connected"
    WebSocketState.CONNECTING, WebSocketState.RECONNECTING -> "Connecting…"
    WebSocketState.ERROR, WebSocketState.DISCONNECTED -> "Offline"
}

/** Connection state in a tab's top bar; tapping it opens the connection screen (Phase 102). */
@Composable
fun ConnectionChip(state: WebSocketState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dot: Color = when (state) {
        WebSocketState.CONNECTED -> scheme.primary
        WebSocketState.CONNECTING, WebSocketState.RECONNECTING -> scheme.tertiary
        WebSocketState.ERROR, WebSocketState.DISCONNECTED -> scheme.error
    }
    val label = connectionLabel(state)
    AssistChip(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Server: $label. Opens connection settings" },
        label = { Text(label) },
        leadingIcon = { Box(Modifier.size(8.dp).background(dot, CircleShape)) }
    )
}

@Preview
@Composable
private fun ConnectionChipPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    Column(modifier = Modifier.padding(Dimens.l), verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
        WebSocketState.values().forEach { ConnectionChip(it, onClick = {}) }
    }
}
