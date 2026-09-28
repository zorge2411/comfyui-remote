package com.example.comfyui_remote.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/**
 * The form's fixed bottom bar (Phase 101): batch count, Queue and Generate stay visible while the fields
 * scroll. [busyLabel] replaces "Generate" while running, uploading or checking.
 */
@Composable
fun FormActionBar(
    batchCount: Int,
    onBatchChange: (Int) -> Unit,
    enabled: Boolean,
    busyLabel: String?,
    onQueue: () -> Unit,
    onGenerate: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.s),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.l, vertical = Dimens.s)
        ) {
            IconButton(onClick = { onBatchChange(batchCount - 1) }, enabled = batchCount > 1) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer runs")
            }
            Text("×$batchCount", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onBatchChange(batchCount + 1) }, enabled = batchCount < 10) {
                Icon(Icons.Filled.Add, contentDescription = "More runs")
            }
            OutlinedButton(onClick = onQueue, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Queue", maxLines = 1)
            }
            Button(onClick = onGenerate, enabled = enabled, modifier = Modifier.weight(1.4f)) {
                if (busyLabel != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).padding(end = Dimens.xs),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(busyLabel, maxLines = 1)
                } else {
                    Text("Generate", maxLines = 1)
                }
            }
        }
    }
}

@Preview
@Composable
private fun FormActionBarPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    FormActionBar(batchCount = 2, onBatchChange = {}, enabled = true, busyLabel = null, onQueue = {}, onGenerate = {})
}

@Preview(name = "Busy, dark")
@Composable
private fun FormActionBarBusyPreview() = ComfyUI_front_endTheme(themeMode = 2) {
    FormActionBar(batchCount = 1, onBatchChange = {}, enabled = false, busyLabel = "Running…", onQueue = {}, onGenerate = {})
}
