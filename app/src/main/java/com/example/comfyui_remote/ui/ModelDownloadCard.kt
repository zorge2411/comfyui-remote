package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.domain.ModelDownload
import com.example.comfyui_remote.ui.components.formatBytes

/**
 * One job of the server's model download queue on the Queue screen (Phase 99). Reorder and Retry need
 * comfyui_remote_helper version 2 ([queueControl]).
 */
@Composable
fun ModelDownloadCard(
    download: ModelDownload,
    queuedCount: Int,
    queueControl: Boolean,
    onMove: (Int) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val downloading = download.status == "downloading"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (downloading) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(download.filename, style = MaterialTheme.typography.titleSmall)
                val state = when (download.status) {
                    "queued" -> download.position?.let { "#$it in queue" } ?: "Queued"
                    "downloading" -> "Downloading"
                    "done" -> "Done"
                    "error" -> "Failed"
                    "cancelled" -> "Cancelled"
                    else -> download.status
                }
                Text("models/${download.directory} • $state", style = MaterialTheme.typography.bodySmall)
                if (downloading) {
                    val progress = download.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        Text(
                            "${formatBytes(download.done)} / ${formatBytes(download.total ?: 0)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        Text(formatBytes(download.done), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (download.status == "error" && download.error != null) {
                    Text(
                        download.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            when {
                download.status == "queued" -> {
                    val position = download.position
                    if (queueControl && position != null) {
                        IconButton(onClick = { onMove(position - 1) }, enabled = position > 1) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { onMove(position + 1) }, enabled = position < queuedCount) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                    IconButton(onClick = onCancel, enabled = download.id != null) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove from queue")
                    }
                }
                downloading ->
                    IconButton(onClick = onCancel, enabled = download.id != null) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel download")
                    }
                queueControl && (download.status == "error" || download.status == "cancelled") && download.id != null ->
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                    }
            }
        }
    }
}
