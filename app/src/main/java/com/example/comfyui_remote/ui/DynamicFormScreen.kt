package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.domain.FormLayout
import com.example.comfyui_remote.domain.FormValues
import com.example.comfyui_remote.domain.InputField
import com.example.comfyui_remote.domain.LabelledField
import com.example.comfyui_remote.domain.ModelSource
import com.example.comfyui_remote.domain.ModelSources
import com.example.comfyui_remote.domain.PromptValidator
import com.example.comfyui_remote.domain.key
import com.example.comfyui_remote.network.ExecutionStatus
import com.example.comfyui_remote.ui.components.AppCard
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.ConfirmDialog
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.ErrorCard
import com.example.comfyui_remote.ui.components.NotConnectedBanner
import com.example.comfyui_remote.ui.components.SectionHeader
import com.example.comfyui_remote.ui.components.StatusBanner
import com.example.comfyui_remote.ui.components.StatusKind
import com.example.comfyui_remote.ui.components.formatBytes
import com.example.comfyui_remote.ui.form.FormActionBar
import com.example.comfyui_remote.ui.form.MainSettings
import com.example.comfyui_remote.ui.form.NodeSection
import com.example.comfyui_remote.ui.form.NumberField
import com.example.comfyui_remote.ui.form.PinnableField
import com.example.comfyui_remote.ui.form.PromptField
import com.example.comfyui_remote.ui.form.SeedField
import com.example.comfyui_remote.ui.form.SelectionField
import com.example.comfyui_remote.ui.form.TextInputField
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * The workflow form (Phase 101): a status area (warnings, progress, latest result), the prompt, main
 * settings, and every other input in collapsed per-node sections, with Queue and Generate in a fixed bar.
 */
@Suppress("DEPRECATION", "UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormScreen(
    viewModel: MainViewModel,
    workflow: WorkflowEntity,
    onBack: () -> Unit,
    onViewInGallery: (Long) -> Unit,
    onOpenConnection: () -> Unit
) {
    val nodeMetadata by viewModel.nodeMetadata.collectAsState()
    // Field values, keyed by nodeId/fieldName. Re-parsed when the workflow or the server's node list changes,
    // keeping remembered values (Phase 101.2) and anything edited on this screen.
    var inputs by remember(workflow.id) { mutableStateOf<List<InputField>>(emptyList()) }
    LaunchedEffect(workflow.id, nodeMetadata) {
        inputs = FormValues.overlay(viewModel.parseWorkflowInputs(workflow), inputs)
    }
    fun onFieldChange(field: InputField) {
        inputs = inputs.map { if (it.key == field.key) field else it }
        viewModel.saveFormValues(workflow, inputs)
    }
    val promptIds = remember(workflow.jsonContent) { viewModel.promptNodeIds(workflow.jsonContent) }
    // Phase 106: node-group fields the user pinned under the prompt
    val pinnedKeys = remember(workflow.pinnedFields) { viewModel.pinnedFields(workflow) }
    val form = remember(inputs, promptIds, pinnedKeys) {
        FormLayout.build(inputs, promptIds.first, promptIds.second, pinnedKeys)
    }
    var expandedGroups by rememberSaveable(workflow.id) { mutableStateOf(listOf<String>()) }

    // Image fields with an upload in flight, by key: Generate/Queue wait for the server file name, or
    // injectValues() would skip the field and run with the node's old image.
    var pendingUploads by remember { mutableStateOf(setOf<String>()) }
    var showNodeSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var batchCount by rememberSaveable { mutableStateOf(1) }

    val executionStatus by viewModel.executionStatus.collectAsState()
    val executionProgress by viewModel.executionProgress.collectAsState()
    val lastUsedSeeds by viewModel.lastUsedSeeds.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Phase 91: pre-flight check before Generate / Queue
    var preflightIssues by remember { mutableStateOf<List<PromptValidator.Issue>?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var checking by remember { mutableStateOf(false) }
    fun withPreflight(action: () -> Unit) {
        if (checking) return
        checking = true
        scope.launch {
            val issues = viewModel.preflight(workflow, inputs)
            checking = false
            if (issues.isNullOrEmpty()) {
                action()
            } else {
                preflightIssues = issues
                pendingAction = action
            }
        }
    }
    // Phase 105: a click when a run is sent, a confirm when its result arrives while the form shows
    val vibration by viewModel.vibration.collectAsState()
    val haptics = com.example.comfyui_remote.ui.components.rememberHaptics(vibration)
    var previousStatus by remember { mutableStateOf(executionStatus) }
    LaunchedEffect(executionStatus) {
        val wasRunning = previousStatus == ExecutionStatus.EXECUTING || previousStatus == ExecutionStatus.QUEUED
        if (wasRunning && executionStatus == ExecutionStatus.FINISHED) haptics.confirm()
        previousStatus = executionStatus
    }
    fun generate() = withPreflight {
        haptics.click()
        viewModel.executeWorkflow(workflow, inputs, batchCount)
    }

    // Missing nodes against the connected server; the list stored at import may be stale
    val liveMissingNodes by androidx.compose.runtime.produceState<List<String>?>(null, workflow, nodeMetadata) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { viewModel.missingNodeTypes(workflow) }
    }
    val missingNodesText = liveMissingNodes?.joinToString(", ") ?: workflow.missingNodes
    // Phase 97: models the server doesn't have, from the workflow's download links and from the prompt's
    // loader values (which follow the form's current model choices)
    val helperAvailable by viewModel.helperAvailable.collectAsState()
    val helperHasToken by viewModel.helperHasToken.collectAsState()
    val helperHasCivitaiToken by viewModel.helperHasCivitaiToken.collectAsState()
    val helperVersion by viewModel.helperVersion.collectAsState()
    val modelDownloads by viewModel.modelDownloads.collectAsState()
    val modelsVersion by viewModel.modelsVersion.collectAsState()
    val modelChoices = remember(inputs) {
        inputs.mapNotNull { f ->
            when (f) {
                is InputField.ModelInput -> f.key to f.value
                is InputField.SelectionInput -> f.key to f.value
                else -> null
            }
        }.toMap()
    }
    val missingModels by androidx.compose.runtime.produceState(emptyList<ModelSource>(), workflow, nodeMetadata, modelsVersion, modelChoices) {
        value = viewModel.missingModels(workflow, modelChoices)
    }
    LaunchedEffect(workflow.id) { viewModel.refreshModelHelper() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val serverWarning by viewModel.serverWarning.collectAsState()
    val image by viewModel.generatedImage.collectAsState()
    val generatedMediaId by viewModel.generatedMediaId.collectAsState()

    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.example.comfyui_remote.network.WebSocketState.CONNECTED
    val running = executionStatus == ExecutionStatus.EXECUTING || executionStatus == ExecutionStatus.QUEUED
    val idle = executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED ||
        executionStatus == ExecutionStatus.ERROR

    val context = LocalContext.current
    val isSecure by viewModel.isSecure.collectAsState()
    val currentHost by viewModel.host.collectAsState()
    val currentPort by viewModel.port.collectAsState()

    val renderField: @Composable (LabelledField, Modifier) -> Unit = { lf, modifier ->
        when (val f = lf.field) {
            is InputField.StringInput ->
                if (lf.key == form.prompt?.key || lf.key == form.negative?.key) PromptField(lf, ::onFieldChange, modifier)
                else TextInputField(lf, ::onFieldChange, modifier)
            is InputField.IntInput, is InputField.FloatInput -> NumberField(lf, ::onFieldChange, modifier)
            is InputField.SeedInput -> SeedField(lf, lastUsedSeeds[lf.key], ::onFieldChange, modifier)
            is InputField.SelectionInput ->
                SelectionField(lf, f.options, f.value, { onFieldChange(f.copy(value = it)) }, modifier)
            is InputField.ModelInput ->
                SelectionField(lf, availableModels, f.value, { onFieldChange(f.copy(value = it)) }, modifier)
            is InputField.ImageInput -> {
                val serverUrl = if (f.localUri == null && f.value != null) {
                    com.example.comfyui_remote.domain.MediaUrls.view("${if (isSecure) "https" else "http"}://$currentHost:$currentPort", f.value!!, type = "input")
                } else null
                com.example.comfyui_remote.ui.components.ImageSelector(
                    label = lf.label,
                    currentUri = f.localUri,
                    serverUrl = serverUrl,
                    onImageSelected = { uri ->
                        val key = lf.key
                        val previous = f
                        // Optimistic update, then upload; Generate/Queue wait for it
                        inputs = inputs.map { if (it.key == key) f.copy(localUri = uri.toString(), value = null) else it }
                        pendingUploads = pendingUploads + key
                        scope.launch {
                            try {
                                val response = viewModel.uploadImage(uri, context.contentResolver)
                                inputs = inputs.map { current ->
                                    if (current.key != key || current !is InputField.ImageInput) current
                                    else if (response != null) current.copy(value = response.name)
                                    else previous
                                }
                                if (response == null) viewModel.reportError("Image upload failed — please try again")
                                else viewModel.saveFormValues(workflow, inputs)
                            } finally {
                                pendingUploads = pendingUploads - key
                            }
                        }
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(workflow.name, onBack = onBack) {
                IconButton(onClick = { showNodeSheet = true }) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = "Workflow nodes")
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Reset to workflow defaults") },
                        onClick = { showMenu = false; confirmReset = true }
                    )
                    if (workflow.id == 0L) {
                        DropdownMenuItem(
                            text = { Text("Save as template") },
                            onClick = {
                                showMenu = false
                                viewModel.importWorkflow(workflow.name, workflow.jsonContent, com.example.comfyui_remote.domain.WorkflowSource.LOCAL_IMPORT) {}
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            FormActionBar(
                batchCount = batchCount,
                onBatchChange = { batchCount = it.coerceIn(1, 10) },
                enabled = idle && pendingUploads.isEmpty() && !checking && isConnected,
                // Phase 102: Generate needs the server; Queue only adds to the app's own queue
                queueEnabled = idle && pendingUploads.isEmpty() && !checking,
                offlineLabel = if (isConnected) null else "Not connected",
                busyLabel = when {
                    running -> "Running…"
                    pendingUploads.isNotEmpty() -> "Uploading…"
                    checking -> "Checking…"
                    else -> null
                },
                onQueue = {
                    withPreflight {
                        haptics.click()
                        viewModel.addToQueue(workflow, inputs, batchCount)
                    }
                },
                onGenerate = { generate() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.m)
        ) {
            // ---- Status area ----
            NotConnectedBanner(
                connectionState,
                onReconnect = { viewModel.connect() },
                onOpenConnection = onOpenConnection
            )
            if (!missingNodesText.isNullOrBlank()) {
                StatusBanner(StatusKind.Error, "Missing nodes on server", message = missingNodesText)
            }
            if (missingModels.isNotEmpty()) {
                MissingModelsCard(
                    models = missingModels,
                    downloads = modelDownloads,
                    helperAvailable = helperAvailable,
                    helperHasToken = helperHasToken,
                    helperHasCivitaiToken = helperHasCivitaiToken,
                    helperVersion = helperVersion,
                    onProbe = { viewModel.probeModel(it) },
                    onSaveLink = { viewModel.saveModelLink(workflow, it) },
                    onDownload = { viewModel.downloadModel(it) },
                    onDownloadAll = { viewModel.downloadAllModels(it) },
                    onCancel = { viewModel.cancelModelDownload(it) }
                )
            }
            if (executionStatus == ExecutionStatus.ERROR && errorMessage != null) {
                ErrorCard(
                    title = "Execution error",
                    message = errorMessage!!,
                    onDismiss = { viewModel.clearErrorMessage() },
                    onRetry = { viewModel.clearErrorMessage(); generate() },
                    onCopy = { clipboard.setText(AnnotatedString(errorMessage!!)) }
                )
            }
            // The server accepted the prompt but skipped outputs that depend on failing nodes (Phase 92)
            serverWarning?.let { warning ->
                StatusBanner(
                    StatusKind.Info,
                    "Some outputs were skipped",
                    actions = { TextButton(onClick = { viewModel.clearServerWarning() }) { Text("Dismiss") } }
                ) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
            if (running) {
                AppCard {
                    val hasOverall = executionProgress.overallProgress > 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (executionStatus == ExecutionStatus.QUEUED) "Queued…"
                            else "Running: ${executionProgress.currentNodeTitle ?: "node #${executionProgress.currentNodeId ?: "?"}"}" +
                                (if (executionProgress.maxSteps > 0) " (${executionProgress.currentStep}/${executionProgress.maxSteps})" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasOverall) {
                            Text("${(executionProgress.overallProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(Dimens.s))
                    if (hasOverall) {
                        LinearProgressIndicator(progress = { executionProgress.overallProgress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (image != null) {
                // Compact, so the prompt stays near the top
                AppCard(onClick = generatedMediaId?.let { id -> { onViewInGallery(id) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        coil.compose.AsyncImage(
                            model = image,
                            contentDescription = "Latest result",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.small)
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = Dimens.m)) {
                            Text("Latest result", style = MaterialTheme.typography.titleSmall)
                            if (generatedMediaId != null) {
                                TextButton(onClick = { generatedMediaId?.let { onViewInGallery(it) } }) { Text("Open in gallery") }
                            }
                        }
                    }
                }
            }

            // ---- Prompt ----
            form.prompt?.let { renderField(it, Modifier) }
            form.negative?.let { renderField(it, Modifier) }

            // ---- Pinned (Phase 106) ----
            if (form.pinned.isNotEmpty()) {
                SectionHeader("Pinned")
                form.pinned.forEach { lf ->
                    PinnableField(pinned = true, label = lf.label, onToggle = { viewModel.togglePin(workflow, lf.key) }) {
                        renderField(lf, Modifier)
                    }
                }
            }

            // ---- Main settings ----
            if (form.main.isNotEmpty()) {
                SectionHeader("Main settings")
                MainSettings(form.main) { lf, modifier -> renderField(lf, modifier) }
            }

            // ---- Everything else, per node ----
            if (form.groups.isNotEmpty()) {
                SectionHeader(if (form.prompt == null && form.main.isEmpty()) "Inputs" else "Other inputs")
                form.groups.forEach { group ->
                    NodeSection(
                        group = group,
                        expanded = group.nodeId in expandedGroups,
                        onToggle = {
                            expandedGroups = if (group.nodeId in expandedGroups) expandedGroups - group.nodeId
                            else expandedGroups + group.nodeId
                        }
                    ) { lf ->
                        PinnableField(pinned = false, label = lf.label, onToggle = { viewModel.togglePin(workflow, lf.key) }) {
                            renderField(lf, Modifier)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.l))
        }
    }

    preflightIssues?.let { issues ->
        PreflightDialog(
            issues = issues,
            missingModelCount = missingModels.size,
            onQueueAnyway = {
                val action = pendingAction
                preflightIssues = null
                pendingAction = null
                action?.invoke()
            },
            onCancel = {
                preflightIssues = null
                pendingAction = null
            }
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset to workflow defaults?",
            text = "Your edited values for this workflow will be replaced by the values saved in the workflow.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = {
                confirmReset = false
                scope.launch { inputs = viewModel.resetFormValues(workflow) }
            },
            onDismiss = { confirmReset = false }
        )
    }

    if (showNodeSheet) {
        ModalBottomSheet(onDismissRequest = { showNodeSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.l)
                    .navigationBarsPadding()
            ) {
                Text("Workflow nodes", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Dimens.l))
                val allNodes = remember(workflow.jsonContent) { viewModel.parseAllNodes(workflow.jsonContent) }
                LazyColumn {
                    items(allNodes) { node ->
                        Column(modifier = Modifier.padding(vertical = Dimens.xs)) {
                            Row {
                                Text("#${node.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(Dimens.s))
                                Text(node.title, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = node.classType,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = Dimens.s), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Phase 91: what the server would likely reject, grouped by node; nothing is blocked. */
@Composable
private fun PreflightDialog(
    issues: List<PromptValidator.Issue>,
    missingModelCount: Int,
    onQueueAnyway: () -> Unit,
    onCancel: () -> Unit
) {
    val errors = issues.count { it.severity == PromptValidator.Severity.ERROR }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (errors > 0) "Server will likely reject this prompt" else "Server may reject this prompt") },
        text = {
            // Phase 103: the list is shared with the workflow list's badge
            com.example.comfyui_remote.ui.components.IssueList(
                issues,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (missingModelCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$missingModelCount missing model file${if (missingModelCount == 1) "" else "s"} can be fetched from the Missing Models card",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onQueueAnyway) { Text("Queue anyway") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

/** Phase 97: models the server lacks, downloadable through the server's comfyui_remote_helper extension. */
@Composable
private fun MissingModelsCard(
    models: List<ModelSource>,
    downloads: Map<String, com.example.comfyui_remote.domain.ModelDownload>,
    helperAvailable: Boolean?,
    helperHasToken: Boolean,
    helperHasCivitaiToken: Boolean,
    helperVersion: Int,
    onProbe: suspend (ModelSource) -> Pair<Long?, Boolean>?,
    onSaveLink: suspend (ModelSource) -> Unit,
    onDownload: (ModelSource) -> Unit,
    onDownloadAll: (List<ModelSource>) -> Unit,
    onCancel: (com.example.comfyui_remote.domain.ModelDownload) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirming by remember { mutableStateOf<ModelSource?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<Pair<Long?, Boolean>?>(null) }
    val onCard = MaterialTheme.colorScheme.onTertiaryContainer
    // Phase 99: Download all; probes of every model to download, null entries while probing
    var confirmingAll by remember { mutableStateOf<List<ModelSource>?>(null) }
    var allProbes by remember { mutableStateOf<List<Pair<Long?, Boolean>?>?>(null) }
    val downloadable = models.filter { it.hasLink && downloads["${it.directory}/${it.name}"]?.active != true }
    // A model found in the prompt without a download link: the user pastes one (saved with the workflow)
    var addingLink by remember { mutableStateOf<ModelSource?>(null) }
    fun confirmDownload(model: ModelSource) {
        confirming = model
        probe = null
        probing = true
        scope.launch {
            probe = onProbe(model)
            probing = false
        }
    }
    // Download progress is shown as a notification; Android 13+ needs the permission asked for at run time
    val context = LocalContext.current
    var askedNotifications by remember { mutableStateOf(false) }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    fun askForNotifications() {
        if (askedNotifications || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        askedNotifications = true
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(permission)
    }

    StatusBanner(
        StatusKind.Warning,
        "Missing models on server",
        actions = {
            if (helperAvailable == true && downloadable.size >= 2) {
                TextButton(onClick = {
                    confirmingAll = downloadable
                    allProbes = null
                    scope.launch {
                        allProbes = kotlinx.coroutines.coroutineScope {
                            downloadable
                                .map { source -> async { onProbe(source) } }
                                .map { it.await() }
                        }
                    }
                }) { Text("Download all (${downloadable.size})") }
            }
        }
    ) {
        Column {
            models.forEach { model ->
                val download = downloads["${model.directory}/${model.name}"]
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.name, style = MaterialTheme.typography.bodyMedium, color = onCard)
                        Text(
                            if (model.hasLink) "models/${model.directory}" else "models/${model.directory} · no download link",
                            style = MaterialTheme.typography.bodySmall,
                            color = onCard
                        )
                    }
                    when {
                        download?.active == true ->
                            androidx.compose.material3.TextButton(
                                onClick = { onCancel(download) },
                                enabled = download.id != null
                            ) { Text("Cancel") }
                        helperAvailable == true && !model.hasLink ->
                            TextButton(onClick = { addingLink = model }) { Text("Add link") }
                        helperAvailable == true ->
                            TextButton(onClick = { confirmDownload(model) }) {
                                Text(if (download?.status == "error") "Retry" else "Download")
                            }
                        model.hasLink ->
                            androidx.compose.material3.TextButton(onClick = {
                                clipboard.setText(AnnotatedString(model.url))
                            }) { Text("Copy link") }
                    }
                }
                if (download?.active == true) {
                    val total = download.total
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = { (download.done.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        Text(
                            "${formatBytes(download.done)} / ${formatBytes(total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = onCard
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Text(
                            if (download.status == "queued") "Waiting…" else formatBytes(download.done),
                            style = MaterialTheme.typography.bodySmall,
                            color = onCard
                        )
                    }
                } else if (download?.status == "error" && download.error != null) {
                    Text(download.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (helperAvailable == false) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Install comfyui_remote_helper on the server to download from here",
                    style = MaterialTheme.typography.bodySmall,
                    color = onCard
                )
            }
        }
    }

    confirmingAll?.let { all ->
        val probes = allProbes
        val sizes = probes?.mapNotNull { it?.first }
        val unknown = if (probes == null) 0 else probes.count { it?.first == null }
        val gated = probes?.let { p -> all.filterIndexed { i, _ -> p[i]?.second == true } }.orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingAll = null },
            title = { Text("Download ${all.size} models to the server?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        when {
                            sizes == null -> "Checking sizes…"
                            unknown > 0 -> "Total: ${formatBytes(sizes.sum())} ($unknown of unknown size)"
                            else -> "Total: ${formatBytes(sizes.sum())}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "They download one at a time, in this order. You can reorder or remove them on the Queue screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    all.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                    if (gated.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val (civitai, hf) = gated.partition { ModelSources.isCivitai(it.url) }
                        Text(
                            listOfNotNull(
                                hf.takeIf { it.isNotEmpty() }?.let {
                                    "Gated or unavailable: ${it.joinToString { m -> m.name }}. " +
                                        if (helperHasToken) "The server's HF_TOKEN account must have accepted their licences."
                                        else "Set HF_TOKEN on the server after accepting their licences, or these will fail."
                                },
                                civitai.takeIf { it.isNotEmpty() }?.let {
                                    "Civitai login needed: ${it.joinToString { m -> m.name }}. " +
                                        if (helperHasCivitaiToken) "Check the server's CIVITAI_TOKEN."
                                        else "Set CIVITAI_TOKEN on the server, or these will fail."
                                }
                            ).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDownloadAll(all)
                        confirmingAll = null
                        askForNotifications()
                    },
                    enabled = probes != null
                ) { Text("Download all") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingAll = null }) { Text("Cancel") }
            }
        )
    }

    addingLink?.let { model ->
        AddModelLinkDialog(
            model = model,
            civitaiSupported = helperVersion >= 3,
            onDismiss = { addingLink = null },
            onSave = { url ->
                addingLink = null
                val linked = model.copy(url = url)
                scope.launch { onSaveLink(linked) }
                confirmDownload(linked)
            }
        )
    }

    confirming?.let { model ->
        val size = probe?.first
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Download to server?") },
            text = {
                Column {
                    Text(model.name, style = MaterialTheme.typography.bodyMedium)
                    Text("Folder: models/${model.directory}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            probing -> "Checking size…"
                            size != null -> "Size: ${formatBytes(size)}"
                            else -> "Size: unknown"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (probe?.second == true) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                ModelSources.isCivitai(model.url) ->
                                    if (helperHasCivitaiToken) "Civitai refused the download: check the server's CIVITAI_TOKEN."
                                    else "This model needs a Civitai login: set CIVITAI_TOKEN on the server (an API key from civitai.com), or the download will fail."
                                helperHasToken -> "This model is gated: the Hugging Face account of the server's HF_TOKEN must have accepted its licence."
                                else -> "This model is gated: accept its licence on huggingface.co and set HF_TOKEN on the server, or the download will fail."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDownload(model)
                        confirming = null
                        askForNotifications()
                    },
                    enabled = !probing
                ) { Text("Download") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Asks for a Hugging Face, GitHub or Civitai link to a model the workflow names but carries no download link
 * for. Civitai needs helper v3 on the server ([civitaiSupported]).
 */
@Composable
private fun AddModelLinkDialog(
    model: ModelSource,
    civitaiSupported: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var text by remember(model) {
        // Prefill a copied link, the usual way to get one here
        val copied = clipboard.getText()?.text?.trim().orEmpty()
        mutableStateOf(if (com.example.comfyui_remote.domain.ModelSources.normalizeUrl(copied) != null) copied else "")
    }
    val parsed = com.example.comfyui_remote.domain.ModelSources.normalizeUrl(text)
    val needsNewerHelper = parsed != null && !civitaiSupported && ModelSources.isCivitai(parsed)
    val url = parsed?.takeIf { !needsNewerHelper }
    // The download link's last part is a version id for Civitai, not a file name
    val showsName = url != null && !ModelSources.isCivitai(url)
    val linkedName = url?.takeIf { showsName }?.substringBefore('?')?.substringAfterLast('/')?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
                Text(model.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Paste a Hugging Face, GitHub or Civitai link to this file. It's saved with the workflow.",
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Link") },
                    singleLine = true,
                    isError = text.isNotBlank() && url == null,
                    supportingText = when {
                        needsNewerHelper -> { { Text("Civitai links need comfyui_remote_helper version 3 on the server") } }
                        text.isNotBlank() && url == null -> {
                            { Text("Use an https link on huggingface.co, github.com or civitai.com (a Civitai page link needs a model version)") }
                        }
                        linkedName != null && linkedName != model.name -> { { Text("Will be saved as ${model.name}") } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                val query = java.net.URLEncoder.encode(model.name.substringBeforeLast('.'), "UTF-8")
                // Wraps on narrow phones instead of squeezing the second label onto two lines
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow {
                    TextButton(onClick = { uriHandler.openUri("https://huggingface.co/search/full-text?q=$query") }) {
                        Text("Search Hugging Face")
                    }
                    TextButton(onClick = { uriHandler.openUri("https://civitai.com/search/models?query=$query") }) {
                        Text("Search Civitai")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { url?.let(onSave) }, enabled = url != null) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
