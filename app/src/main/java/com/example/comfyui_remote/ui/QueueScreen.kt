package com.example.comfyui_remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.QueueViewModel
import com.example.comfyui_remote.data.LocalQueueItem
import com.example.comfyui_remote.data.QueueStatus
import com.example.comfyui_remote.ui.components.EmptyState

// Reusable date formatter to avoid instantiation on every recomposition
private val DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm", java.util.Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onBack: () -> Unit
) {
    val queueItems by viewModel.queueItems.collectAsState()
    val isRunning by viewModel.isQueueRunning.collectAsState()
    val currentItemId by viewModel.currentExecutingItemId.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<LocalQueueItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (queueItems.any { it.status == QueueStatus.COMPLETED }) {
                        TextButton(onClick = { viewModel.clearCompleted() }) {
                            Text("Clear Completed")
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                FloatingActionButton(
                    onClick = {
                        if (isRunning) viewModel.stopQueue() else viewModel.startQueue()
                    },
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Stop Queue" else "Start Queue"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    ) { padding ->
        if (queueItems.isEmpty()) {
            EmptyState(
                icon = Icons.Default.PendingActions,
                title = "Queue is Empty",
                message = "Add workflows to the queue to execute them in sequence.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(queueItems, key = { it.id }) { item ->
                    QueueItemCard(
                        item = item,
                        isExecuting = item.id == currentItemId,
                        onDelete = { showDeleteDialog = item }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Queue Item") },
            text = { Text("Are you sure you want to delete '${item.workflowName}' from the queue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(item)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QueueItemCard(
    item: LocalQueueItem,
    isExecuting: Boolean,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isExecuting) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.workflowName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Batch: ${item.batchCount} • Status: ${item.status.name}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = DATE_FORMATTER.withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(item.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            if (isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (item.status != QueueStatus.EXECUTING) { // Don't show delete if executing (though isExecuting covers it mostly)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error 
                    )
                }
            }
        }
    }
}
