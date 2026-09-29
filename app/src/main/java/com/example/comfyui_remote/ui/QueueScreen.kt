package com.example.comfyui_remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.QueueViewModel
import com.example.comfyui_remote.data.LocalQueueItem
import com.example.comfyui_remote.data.ModelDownloadRepository
import com.example.comfyui_remote.data.QueueStatus
import com.example.comfyui_remote.data.ServerQueueRepository
import com.example.comfyui_remote.domain.ServerJob
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.components.AppCard
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.ConfirmDialog
import com.example.comfyui_remote.ui.components.ConnectionChip
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.EmptyState
import com.example.comfyui_remote.ui.components.NotConnectedBanner
import com.example.comfyui_remote.ui.components.SectionHeader

// Reusable date formatter to avoid instantiation on every recomposition
private val DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm", java.util.Locale.getDefault())

/**
 * Everything waiting (Phase 102): the ComfyUI server's own queue, the app's local queue and the server's
 * model downloads. A tab: no back, no second bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    mainViewModel: MainViewModel,
    downloads: ModelDownloadRepository,
    serverQueue: ServerQueueRepository,
    onOpenConnection: () -> Unit
) {
    val connectionState by mainViewModel.connectionState.collectAsState()
    val isConnected = connectionState == WebSocketState.CONNECTED
    val queueItems by viewModel.queueItems.collectAsState()
    val isRunning by viewModel.isQueueRunning.collectAsState()
    val currentItemId by viewModel.currentExecutingItemId.collectAsState()

    val jobs by serverQueue.jobs.collectAsState()
    val serverError by serverQueue.lastError.collectAsState()
    val runningPromptId by mainViewModel.runningPromptId.collectAsState()
    val progress by mainViewModel.executionProgress.collectAsState()
    val running = jobs.firstOrNull { it.running }
    val pending = jobs.filter { !it.running }

    // Phase 99: the server's model download queue
    val modelDownloads by downloads.downloads.collectAsState()
    val helperVersion by downloads.helperVersion.collectAsState()
    val queueControl = helperVersion >= 2
    val queuedCount = modelDownloads.count { it.status == "queued" }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(downloads) {
        downloads.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    // The server's queue is polled only while this screen is visible
    DisposableEffect(serverQueue) {
        serverQueue.startPolling()
        onDispose { serverQueue.stopPolling() }
    }

    var deleteItem by remember { mutableStateOf<LocalQueueItem?>(null) }
    var confirmStop by remember { mutableStateOf<ServerJob?>(null) }
    var confirmClearPending by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            var more by remember { mutableStateOf(false) }
            AppTopBar("Queue") {
                ConnectionChip(connectionState, onClick = onOpenConnection)
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear completed") },
                            enabled = queueItems.any { it.status == QueueStatus.COMPLETED },
                            onClick = { more = false; viewModel.clearCompleted() }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val nothingWaiting = jobs.isEmpty() && queueItems.isEmpty() && modelDownloads.isEmpty()
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            NotConnectedBanner(
                connectionState,
                onReconnect = { mainViewModel.connect() },
                onOpenConnection = onOpenConnection,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.s)
            )
            if (nothingWaiting && isConnected) {
                EmptyState(
                    icon = Icons.Default.PendingActions,
                    title = "Nothing waiting",
                    message = "The server is idle. Workflows you add with Queue wait here until you run them."
                )
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.s),
                verticalArrangement = Arrangement.spacedBy(Dimens.s)
            ) {
                // On the server
                item(key = "server-header") {
                    SectionHeader("On the server") {
                        if (isConnected && pending.isNotEmpty()) {
                            TextButton(onClick = { confirmClearPending = true }) { Text("Clear pending") }
                        }
                    }
                }
                serverError?.let { error ->
                    item(key = "server-error") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { serverQueue.refresh() }) { Text("Retry") }
                        }
                    }
                }
                when {
                    !isConnected -> item(key = "server-offline") {
                        HintText("Not connected.")
                    }
                    jobs.isEmpty() -> item(key = "server-idle") {
                        HintText("The server is idle.")
                    }
                    else -> {
                        running?.let { job ->
                            item(key = "server-${job.promptId}") {
                                RunningJobCard(
                                    job = job,
                                    progress = progress.takeIf { job.promptId == runningPromptId },
                                    onStop = { confirmStop = job }
                                )
                            }
                        }
                        items(pending, key = { "server-${it.promptId}" }) { job ->
                            PendingJobRow(
                                job = job,
                                position = pending.indexOf(job) + 1,
                                onCancel = { serverQueue.cancel(job) }
                            )
                        }
                    }
                }

                // In the app
                item(key = "local-header") {
                    SectionHeader("In the app") {
                        if (queueItems.any { it.status == QueueStatus.PENDING } || isRunning) {
                            FilledTonalButton(
                                onClick = { if (isRunning) viewModel.stopQueue() else viewModel.startQueue() },
                                enabled = isRunning || isConnected
                            ) {
                                Icon(
                                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(if (isRunning) "Stop" else "Run")
                            }
                        }
                    }
                }
                if (queueItems.isEmpty()) {
                    item(key = "local-empty") {
                        HintText("Items you add with Queue on a workflow wait here.")
                    }
                }
                items(queueItems, key = { "local-${it.id}" }) { item ->
                    QueueItemCard(
                        item = item,
                        isExecuting = item.id == currentItemId,
                        onDelete = { deleteItem = item }
                    )
                }

                // Model downloads (Phase 99)
                if (modelDownloads.isNotEmpty()) {
                    item(key = "model-downloads-header") {
                        SectionHeader("Model downloads") {
                            if (queueControl && modelDownloads.any { it.finished && it.id != null }) {
                                TextButton(onClick = { downloads.clearFinished() }) { Text("Clear finished") }
                            }
                        }
                    }
                    items(modelDownloads, key = { it.id ?: "pending-${it.key}" }) { download ->
                        ModelDownloadCard(
                            download = download,
                            queuedCount = queuedCount,
                            queueControl = queueControl,
                            onMove = { position -> downloads.move(download, position) },
                            onCancel = { downloads.cancel(download) },
                            onRetry = { downloads.retry(download) }
                        )
                    }
                }
            }
        }
    }

    deleteItem?.let { item ->
        ConfirmDialog(
            title = "Remove from queue?",
            text = "“${item.workflowName}” will be removed from the app's queue.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.deleteItem(item)
                deleteItem = null
            },
            onDismiss = { deleteItem = null }
        )
    }
    confirmStop?.let { job ->
        ConfirmDialog(
            title = "Stop the running job?",
            text = "The server stops “${jobName(job)}” and discards its output.",
            confirmLabel = "Stop",
            destructive = true,
            onConfirm = {
                serverQueue.interrupt(job)
                confirmStop = null
            },
            onDismiss = { confirmStop = null }
        )
    }
    if (confirmClearPending) {
        ConfirmDialog(
            title = "Clear pending jobs?",
            text = "${pending.size} waiting ${if (pending.size == 1) "job is" else "jobs are"} removed from the server's queue. The running job continues.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                serverQueue.clearPending()
                confirmClearPending = false
            },
            onDismiss = { confirmClearPending = false }
        )
    }
}

private fun jobName(job: ServerJob) = job.workflowName ?: "Workflow ${job.promptId.take(8)}"

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Dimens.xs)
    )
}

@Composable
private fun RunningJobCard(job: ServerJob, progress: MainViewModel.ExecutionProgress?, onStop: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(jobName(job), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = progress?.let { p ->
                        listOfNotNull(
                            p.currentNodeTitle,
                            if (p.maxSteps > 0) "step ${p.currentStep} of ${p.maxSteps}" else null
                        ).joinToString(" · ")
                    }
                    Text(
                        detail?.takeIf { it.isNotEmpty() } ?: "Running",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onStop) { Text("Stop") }
            }
            if (progress != null && progress.overallProgress > 0f) {
                LinearProgressIndicator(progress = { progress.overallProgress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PendingJobRow(job: ServerJob, position: Int, onCancel: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(jobName(job), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Waiting · #$position",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Remove from server queue")
            }
        }
    }
}

private fun statusLabel(status: QueueStatus) = when (status) {
    QueueStatus.PENDING -> "Waiting"
    QueueStatus.EXECUTING -> "Sending"
    QueueStatus.COMPLETED -> "Sent to server"
    QueueStatus.FAILED -> "Failed"
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
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.workflowName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Batch ${item.batchCount} · ${statusLabel(item.status)}",
                    style = MaterialTheme.typography.bodySmall
                )
                // Why it failed, as the server reported it (Phase 92); tap to expand
                if (item.status == QueueStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = Dimens.xs)
                            .clickable { expanded = !expanded }
                    )
                }
                Text(
                    text = DATE_FORMATTER.withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(item.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (item.status != QueueStatus.EXECUTING) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${item.workflowName} from queue")
                }
            }
        }
    }
}
