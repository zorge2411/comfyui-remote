package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/** Connection state as an icon and label in theme colours (UI spec §9), replacing the coloured dot. */
@Composable
fun ConnectionStatus(state: WebSocketState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, icon, color) = when (state) {
        WebSocketState.CONNECTED -> Triple("Connected", Icons.Outlined.CloudDone, scheme.primary)
        WebSocketState.CONNECTING -> Triple("Connecting…", Icons.Outlined.Sync, scheme.tertiary)
        WebSocketState.RECONNECTING -> Triple("Reconnecting…", Icons.Outlined.Sync, scheme.tertiary)
        WebSocketState.ERROR -> Triple("Connection error", Icons.Outlined.CloudOff, scheme.error)
        WebSocketState.DISCONNECTED -> Triple("Disconnected", Icons.Outlined.CloudOff, scheme.outline)
    }
    StatusChip(label, icon, color, modifier)
}

@Composable
private fun StatusChip(label: String, icon: ImageVector, color: Color, modifier: Modifier) {
    AssistChip(
        onClick = {},
        enabled = true,
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color, leadingIconContentColor = color),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = color)
    )
}

@Preview
@Composable
private fun ConnectionStatusPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    Column(modifier = Modifier.padding(Dimens.l), verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
        WebSocketState.values().forEach { ConnectionStatus(it) }
    }
}
