package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/** A list section title with an optional trailing action (UI spec §3). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        action()
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    SectionHeader("Model downloads") { TextButton(onClick = {}) { Text("Clear finished") } }
}
