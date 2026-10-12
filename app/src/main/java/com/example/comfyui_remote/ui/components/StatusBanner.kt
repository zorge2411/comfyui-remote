package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

enum class StatusKind { Error, Warning, Info }

/**
 * An error, warning or info message with an icon instead of emoji (UI spec §5). [content] holds anything
 * below the message (e.g. a list of rows); [actions] are TextButtons shown at the end.
 */
@Composable
fun StatusBanner(
    kind: StatusKind,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer, icon) = when (kind) {
        StatusKind.Error -> Triple(scheme.errorContainer, scheme.onErrorContainer, Icons.Outlined.ErrorOutline)
        StatusKind.Warning -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Outlined.WarningAmber)
        StatusKind.Info -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Outlined.Info)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer)
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = kindLabel(kind), modifier = Modifier.size(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = Dimens.s)
                )
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.xs))
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                content = actions
            )
        }
    }
}

private fun kindLabel(kind: StatusKind) = when (kind) {
    StatusKind.Error -> "Error"
    StatusKind.Warning -> "Warning"
    StatusKind.Info -> "Info"
}

@Preview
@Composable
private fun StatusBannerPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    Column(modifier = Modifier.padding(Dimens.l), verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
        StatusBanner(StatusKind.Error, "Server rejected the prompt", message = "LoadVideo: file not on the server") {}
        StatusBanner(StatusKind.Warning, "Missing models on server", message = "2 files", actions = {
            TextButton(onClick = {}) { Text("Download all") }
        })
        StatusBanner(StatusKind.Info, "Some outputs were skipped")
    }
}

@Preview(name = "Dark")
@Composable
private fun StatusBannerDarkPreview() = ComfyUI_front_endTheme(themeMode = 2) {
    StatusBanner(StatusKind.Warning, "Missing nodes on server", modifier = Modifier.padding(Dimens.l), message = "SetNode, GetNode")
}
