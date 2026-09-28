package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

enum class AppCardVariant { Default, Highlight }

/**
 * The app's card (UI spec §4): default shape, 16 dp inside, no custom elevation. Tapping the card is its
 * main action; put secondary actions in an overflow menu. Status messages use [StatusBanner] instead.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: AppCardVariant = AppCardVariant.Default,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = when (variant) {
        AppCardVariant.Default -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        AppCardVariant.Highlight -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.cardPadding), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, content = inner)
    } else {
        Card(modifier = modifier, colors = colors, content = inner)
    }
}

@Preview
@Composable
private fun AppCardPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    Column(modifier = Modifier.padding(Dimens.l)) {
        AppCard(onClick = {}) {
            Text("Z-Image-Turbo: Text to Image", style = MaterialTheme.typography.titleMedium)
            Text("qwen_3_4b.safetensors", style = MaterialTheme.typography.bodySmall)
        }
        AppCard(modifier = Modifier.padding(top = Dimens.s), variant = AppCardVariant.Highlight) {
            Text("Running", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(name = "Dark")
@Composable
private fun AppCardDarkPreview() = ComfyUI_front_endTheme(themeMode = 2) {
    AppCard(modifier = Modifier.padding(Dimens.l)) { Text("Storage", style = MaterialTheme.typography.titleMedium) }
}
