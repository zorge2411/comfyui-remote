package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/**
 * The app's top bar (UI spec §2): tabs pass no [onBack]; pushed screens get the mirrored back arrow.
 * Keep [actions] to at most three icons, each with a content description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions
    )
}

@Preview(name = "Tab")
@Composable
private fun AppTopBarTabPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    AppTopBar("Workflows") {
        IconButton(onClick = {}) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
    }
}

@Preview(name = "Pushed, dark")
@Composable
private fun AppTopBarPushedPreview() = ComfyUI_front_endTheme(themeMode = 2) {
    AppTopBar("A very long workflow title that has to be shortened", onBack = {})
}
