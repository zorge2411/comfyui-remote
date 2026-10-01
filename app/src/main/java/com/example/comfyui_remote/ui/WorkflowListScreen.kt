package com.example.comfyui_remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GeneratedMediaListing
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.domain.Compatibility
import com.example.comfyui_remote.domain.ModelRef
import com.example.comfyui_remote.domain.PromptValidator
import com.example.comfyui_remote.domain.WorkflowListView
import com.example.comfyui_remote.domain.WorkflowSort
import com.example.comfyui_remote.network.ServerWorkflowFile
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.components.AppCard
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.ConfirmDialog
import com.example.comfyui_remote.ui.components.ConnectionChip
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.EmptyState
import com.example.comfyui_remote.ui.components.IssueList
import com.example.comfyui_remote.ui.components.LoadingOverlay
import com.example.comfyui_remote.ui.components.NotConnectedBanner
import com.example.comfyui_remote.ui.components.SectionHeader
import com.example.comfyui_remote.ui.components.sharedImage

private val SORT_LABELS = listOf(
    WorkflowSort.LAST_USED to "Last used",
    WorkflowSort.NAME to "Name",
    WorkflowSort.NEWEST to "Newest"
)

/**
 * The Workflows tab (Phase 103): cards with the last result, the models and whether the workflow will run
 * on the connected server; search, sort, and the server's saved workflows in a collapsible section.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun WorkflowListScreen(
    viewModel: MainViewModel,
    onOpenTemplates: () -> Unit = {},
    onOpenConnection: () -> Unit,
    // Phase 106: the card thumbnail grows into the form's result preview
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    onWorkflowValidation: (WorkflowEntity) -> Unit // Will navigate to detail/run screen
) {
    val workflows by viewModel.allWorkflows.collectAsState(initial = emptyList())
    val serverWorkflows by viewModel.serverWorkflows.collectAsState()
    val models by viewModel.workflowModels.collectAsState()
    val statuses by viewModel.workflowStatus.collectAsState()
    val lastResults by viewModel.lastResults.collectAsState()
    val sort by viewModel.workflowSort.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == WebSocketState.CONNECTED
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val isSecure by viewModel.isSecure.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<WorkflowEntity?>(null) }
    var deleting by remember { mutableStateOf<WorkflowEntity?>(null) }
    var issuesOf by remember { mutableStateOf<Pair<WorkflowEntity, List<PromptValidator.Issue>>?>(null) }
    // A sync started by pulling shows the pull indicator instead of the overlay
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(isSyncing) { if (!isSyncing) pulled = false }

    val shown = WorkflowListView.apply(workflows, models, query, sort)
    val shownServer = serverWorkflows.filter { it.name.orEmpty().contains(query.trim(), ignoreCase = true) }

    Scaffold(
        topBar = {
            // Phase 102: the chip counts as one of the three actions; "Sync from server" is pull to refresh
            var sortMenu by remember { mutableStateOf(false) }
            AppTopBar("Workflows") {
                ConnectionChip(connectionState, onClick = onOpenConnection)
                IconButton(onClick = onOpenTemplates, enabled = isConnected) {
                    Icon(Icons.Default.GridView, contentDescription = "Browse templates")
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort workflows")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SORT_LABELS.forEach { (option, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    if (option == sort) Icon(Icons.Default.Check, contentDescription = "Selected")
                                },
                                onClick = {
                                    sortMenu = false
                                    viewModel.setWorkflowSort(option)
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Import workflow")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Banner and search scroll away with the list, so landscape keeps room for the cards (Phase 103)
            val header: @Composable (horizontal: androidx.compose.ui.unit.Dp) -> Unit = { horizontal ->
                NotConnectedBanner(
                    connectionState,
                    onReconnect = { viewModel.connect() },
                    onOpenConnection = onOpenConnection,
                    modifier = Modifier.padding(horizontal = horizontal, vertical = Dimens.s)
                )
                if (workflows.isNotEmpty() || serverWorkflows.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search workflows") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontal, vertical = Dimens.s)
                    )
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = pulled && isSyncing,
                    onRefresh = {
                        if (isConnected) {
                            pulled = true
                            viewModel.syncHistory()
                            viewModel.fetchServerWorkflows()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        workflows.isEmpty() && serverWorkflows.isEmpty() -> Column {
                            header(Dimens.screenPadding)
                            EmptyState(
                                icon = Icons.Default.AccountTree,
                                title = "No workflows yet",
                                message = "Start from one of your server's templates, or tap + to import a workflow file.",
                                actionText = if (isConnected) "Browse templates" else null,
                                onAction = if (isConnected) onOpenTemplates else null
                            )
                        }
                        shown.isEmpty() && shownServer.isEmpty() -> Column {
                            header(Dimens.screenPadding)
                            EmptyState(
                                icon = Icons.Default.SearchOff,
                                title = "No matches",
                                message = "No workflow name or model matches “${query.trim()}”.",
                                actionText = "Clear search",
                                onAction = { query = "" }
                            )
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // Room below the last card for the FAB (UI spec §10)
                            contentPadding = PaddingValues(
                                start = Dimens.screenPadding,
                                end = Dimens.screenPadding,
                                top = Dimens.xs,
                                bottom = 56.dp + Dimens.l + Dimens.l
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
                        ) {
                            item(key = "header") { Column { header(0.dp) } }
                            items(shown, key = { it.id }) { workflow ->
                                WorkflowCard(
                                    workflow = workflow,
                                    models = models[workflow.id].orEmpty(),
                                    status = statuses[workflow.id],
                                    thumbnailUrl = lastResults[workflow.id]
                                        ?.takeIf { it.mediaType != "VIDEO" }
                                        ?.constructUrl(host, port, isSecure),
                                    isVideo = lastResults[workflow.id]?.mediaType == "VIDEO",
                                    onOpen = { onWorkflowValidation(workflow) },
                                    onShowIssues = { issues -> issuesOf = workflow to issues },
                                    onRename = { renaming = workflow },
                                    onDelete = { deleting = workflow },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                            if (shownServer.isNotEmpty()) {
                                item(key = "server-header") {
                                    SectionHeader("On the server (${shownServer.size})") {
                                        TextButton(onClick = { showServer = !showServer }) {
                                            Text(if (showServer) "Hide" else "Show")
                                        }
                                    }
                                }
                                if (showServer) {
                                    items(shownServer, key = { "server-${it.path ?: it.hashCode()}" }) { serverFile ->
                                        ServerWorkflowItem(
                                            serverFile = serverFile,
                                            enabled = isConnected,
                                            onImport = {
                                                viewModel.importServerWorkflow(serverFile) { newWf ->
                                                    onWorkflowValidation(newWf)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isSyncing && !pulled) {
                LoadingOverlay(importStatus.ifEmpty { "Importing workflow…" })
            }
        }
    }

    if (showImportDialog) {
        ImportWorkflowDialog(
            onDismissRequest = { showImportDialog = false },
            onImport = { name, json ->
                viewModel.importWorkflow(name, json) { newWorkflow ->
                    showImportDialog = false
                    onWorkflowValidation(newWorkflow)
                }
            }
        )
    }

    renaming?.let { workflow ->
        var newName by remember(workflow.id) { mutableStateOf(workflow.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename workflow") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameWorkflow(workflow, newName.trim())
                        renaming = null
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { workflow ->
        ConfirmDialog(
            title = "Delete workflow?",
            text = "“${workflow.name}” will be removed from this device.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteWorkflow(workflow)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }

    issuesOf?.let { (workflow, issues) ->
        AlertDialog(
            onDismissRequest = { issuesOf = null },
            title = { Text("Problems in ${workflow.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { IssueList(issues, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { issuesOf = null }) { Text("Close") } }
        )
    }
}

/** One stored workflow: last result, name, models, compatibility badge and a More menu (UI spec §4). */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun WorkflowCard(
    workflow: WorkflowEntity,
    models: List<ModelRef>,
    status: Compatibility?,
    thumbnailUrl: String?,
    isVideo: Boolean,
    onOpen: () -> Unit,
    onShowIssues: (List<PromptValidator.Issue>) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope?,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope?
) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                thumbnailUrl,
                isVideo,
                imageModifier = Modifier.sharedImage(
                    "workflow-thumb-${workflow.id}", sharedTransitionScope, animatedVisibilityScope
                )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.m),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                Text(
                    workflow.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (models.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        models.take(2).forEach { ModelLabel(it.baseName, Modifier.weight(1f, fill = false)) }
                        if (models.size > 2) ModelLabel("+${models.size - 2}")
                    }
                }
                status?.let { CompatibilityBadge(it, onShowIssues) }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${workflow.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(url: String?, isVideo: Boolean, imageModifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Last result",
                contentScale = ContentScale.Crop,
                modifier = imageModifier.fillMaxSize()
            )
        } else {
            Icon(
                if (isVideo) Icons.Outlined.Movie else Icons.Outlined.Image,
                contentDescription = if (isVideo) "Last result is a video" else "No result yet",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelLabel(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimens.s, vertical = 2.dp)
        )
    }
}

/** Ready, N warnings or Won't run, on the StatusBanner colour roles; all but Ready open the issue list. */
@Composable
private fun CompatibilityBadge(status: Compatibility, onShowIssues: (List<PromptValidator.Issue>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    data class Look(val label: String, val icon: ImageVector, val container: Color, val content: Color, val issues: List<PromptValidator.Issue>?)
    val look = when (status) {
        Compatibility.Ready -> Look("Ready", Icons.Outlined.CheckCircle, scheme.secondaryContainer, scheme.onSecondaryContainer, null)
        is Compatibility.Warnings -> Look(
            "${status.issues.size} warning${if (status.issues.size == 1) "" else "s"}",
            Icons.Outlined.WarningAmber, scheme.tertiaryContainer, scheme.onTertiaryContainer, status.issues
        )
        is Compatibility.WillFail -> Look("Won't run", Icons.Outlined.ErrorOutline, scheme.errorContainer, scheme.onErrorContainer, status.issues)
    }
    // The 48 dp touch area wraps the small label (UI spec §12)
    val clickable = look.issues?.let { issues ->
        Modifier
            .heightIn(min = Dimens.minTouch)
            .clickable(onClickLabel = "Show problems") { onShowIssues(issues) }
    } ?: Modifier
    Box(modifier = clickable, contentAlignment = Alignment.CenterStart) {
        Surface(color = look.container, contentColor = look.content, shape = MaterialTheme.shapes.small) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Dimens.s, vertical = 2.dp)
            ) {
                Icon(look.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(look.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = Dimens.xs))
            }
        }
    }
}

/** A workflow saved on the server (userdata), imported with the trailing button. */
@Composable
private fun ServerWorkflowItem(
    serverFile: ServerWorkflowFile,
    enabled: Boolean,
    onImport: () -> Unit
) {
    val name = serverFile.name ?: "Unnamed workflow"
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                serverFile.fullpath?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onImport, enabled = enabled) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = "Import $name")
            }
        }
    }
}
