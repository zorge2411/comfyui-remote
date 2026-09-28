package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.domain.InputField
import com.example.comfyui_remote.domain.ModelSource
import com.example.comfyui_remote.domain.PromptValidator
import com.example.comfyui_remote.domain.displayName
import com.example.comfyui_remote.network.ExecutionStatus
import com.example.comfyui_remote.ui.components.ErrorCard
import com.example.comfyui_remote.ui.components.formatBytes
import kotlin.random.Random

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormScreen(
    viewModel: MainViewModel,
    workflow: WorkflowEntity,
    onBack: () -> Unit,
    onViewInGallery: (Long) -> Unit,
    onViewQueue: () -> Unit
) {
    // We need to parse inputs once
    var inputs by remember { mutableStateOf<List<InputField>>(emptyList()) }
    // Initialize parsed inputs
    LaunchedEffect(workflow) {
        inputs = viewModel.parseWorkflowInputs(workflow.jsonContent)
    }

    // Tracks indices of ImageInput fields with an upload in flight, so Generate/Queue
    // can be blocked until the selected image's server filename is actually available —
    // otherwise injectValues() silently skips the field and the workflow runs with
    // whatever image the node already had (default/placeholder), with no error.
    var pendingImageUploads by remember { mutableStateOf(setOf<Int>()) }

    var showNodeSheet by remember { mutableStateOf(false) }

    val executionStatus by viewModel.executionStatus.collectAsState()
    val nodeMetadata by viewModel.nodeMetadata.collectAsState()
    // Phase 91: pre-flight check before Generate / Queue
    val preflightScope = androidx.compose.runtime.rememberCoroutineScope()
    var preflightIssues by remember { mutableStateOf<List<PromptValidator.Issue>?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var checking by remember { mutableStateOf(false) }
    fun withPreflight(action: () -> Unit) {
        if (checking) return
        checking = true
        preflightScope.launch {
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
    // Missing nodes against the connected server; the list stored at import may be stale
    val liveMissingNodes by androidx.compose.runtime.produceState<List<String>?>(null, workflow, nodeMetadata) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { viewModel.missingNodeTypes(workflow) }
    }
    val missingNodesText = liveMissingNodes?.joinToString(", ") ?: workflow.missingNodes
    // Phase 97: models named by the workflow's download links that the server doesn't have
    val helperAvailable by viewModel.helperAvailable.collectAsState()
    val helperHasToken by viewModel.helperHasToken.collectAsState()
    val modelDownloads by viewModel.modelDownloads.collectAsState()
    val modelsVersion by viewModel.modelsVersion.collectAsState()
    val missingModels by androidx.compose.runtime.produceState(emptyList<ModelSource>(), workflow, nodeMetadata, modelsVersion) {
        value = viewModel.missingModels(workflow)
    }
    LaunchedEffect(workflow.id) { viewModel.refreshModelHelper() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val serverWarning by viewModel.serverWarning.collectAsState()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showNodeSheet = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Workflow Architecture")
                }
            }
            
            // Missing Nodes Warning
            if (!missingNodesText.isNullOrBlank()) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "⚠️ Missing Nodes on Server",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            missingNodesText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (missingModels.isNotEmpty()) {
                MissingModelsCard(
                    models = missingModels,
                    downloads = modelDownloads,
                    helperAvailable = helperAvailable,
                    helperHasToken = helperHasToken,
                    onProbe = { viewModel.probeModel(it) },
                    onDownload = { viewModel.downloadModel(it) },
                    onDownloadAll = { viewModel.downloadAllModels(it) },
                    onCancel = { viewModel.cancelModelDownload(it) }
                )
            }

            // Detailed Error Message
            if (executionStatus == ExecutionStatus.ERROR && errorMessage != null) {
                val clipboard = LocalClipboardManager.current
                ErrorCard(
                    title = "Execution Error",
                    message = errorMessage!!,
                    onDismiss = { viewModel.clearErrorMessage() },
                    onCopy = { clipboard.setText(AnnotatedString(errorMessage!!)) }
                )
            }

            // The server accepted the prompt but skipped outputs that depend on failing nodes (Phase 92)
            serverWarning?.let { warning ->
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚠️ Some outputs were skipped",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearServerWarning() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            inputs.forEachIndexed { index, inputField ->
                when (inputField) {
                    is InputField.StringInput -> {
                        val clipboardManager = LocalClipboardManager.current
                        OutlinedTextField(
                            value = inputField.value,
                            onValueChange = { newValue ->
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = newValue)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (${inputField.displayName})") },
                            minLines = 3,
                            // Long prompts scroll inside the field instead of stretching the whole form.
                            maxLines = 8,
                            trailingIcon = if (inputField.value.isNotEmpty()) {
                                {
                                    Row {
                                        IconButton(onClick = {
                                            clipboardManager.setText(AnnotatedString(inputField.value))
                                        }) {
                                            Icon(Icons.Default.Info, contentDescription = "Copy text")
                                        }
                                        IconButton(onClick = {
                                            inputs = inputs.toMutableList().also {
                                                it[index] = inputField.copy(value = "")
                                            }
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear text")
                                        }
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is InputField.IntInput -> {
                         OutlinedTextField(
                            value = inputField.value.toString(),
                            onValueChange = { newValue ->
                                val intVal = newValue.toIntOrNull() ?: 0
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = intVal)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (${inputField.displayName})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is InputField.FloatInput -> {
                        OutlinedTextField(
                            value = inputField.value.toString(),
                            onValueChange = { newValue ->
                                val floatVal = newValue.toFloatOrNull() ?: 0f
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = floatVal)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (${inputField.displayName})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is InputField.SeedInput -> {
                         OutlinedTextField(
                            value = inputField.value.toString(),
                            onValueChange = { newValue ->
                                val longVal = newValue.toLongOrNull() ?: 0L
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = longVal)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (Seed)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val newSeed = Random.nextLong(1, Long.MAX_VALUE)
                                    inputs = inputs.toMutableList().also {
                                        it[index] = inputField.copy(value = newSeed)
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Randomize Seed")
                                }
                            }
                        )
                    }
                    is InputField.SelectionInput -> {
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inputField.value,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("${inputField.nodeTitle} (${inputField.displayName})") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                inputField.options.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(text = option) },
                                        onClick = {
                                            inputs = inputs.toMutableList().also {
                                                it[index] = inputField.copy(value = option)
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is InputField.ModelInput -> {
                        val availableModels by viewModel.availableModels.collectAsState()
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inputField.value,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("${inputField.nodeTitle} (Model)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                availableModels.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(text = modelName) },
                                        onClick = {
                                            inputs = inputs.toMutableList().also {
                                                it[index] = inputField.copy(value = modelName)
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is InputField.ImageInput -> {
                        val context = LocalContext.current
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        val isSecure by viewModel.isSecure.collectAsState()
                        val currentHost by viewModel.host.collectAsState()
                        val currentPort by viewModel.port.collectAsState()

                        // Build server URL if localUri is null but value exists
                        val serverUrl = if (inputField.localUri == null && inputField.value != null) {
                            val protocol = if (isSecure) "https" else "http"
                            "$protocol://$currentHost:$currentPort/view?filename=${inputField.value}&type=input"
                        } else {
                            null
                        }

                        com.example.comfyui_remote.ui.components.ImageSelector(
                            label = "${inputField.nodeTitle} (${inputField.displayName})",
                            currentUri = inputField.localUri,
                            serverUrl = serverUrl,
                            onImageSelected = { uri ->
                                val previousLocalUri = inputField.localUri
                                val previousValue = inputField.value

                                // 1. Optimistic Update
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(localUri = uri.toString(), value = null)
                                }

                                // 2. Trigger Upload — track in-flight so Generate/Queue stay disabled
                                pendingImageUploads = pendingImageUploads + index
                                scope.launch {
                                    try {
                                        val uploadResponse = viewModel.uploadImage(uri, context.contentResolver)
                                        if (uploadResponse != null) {
                                            // 3. Update with Server Filename
                                            inputs = inputs.toMutableList().also { list ->
                                                // Re-fetch item to be safe, though index should be stable
                                                val current = list[index] as? InputField.ImageInput
                                                if (current != null) {
                                                    list[index] = current.copy(value = uploadResponse.name)
                                                }
                                            }
                                        } else {
                                            // Upload failed — revert the optimistic update and surface the error
                                            inputs = inputs.toMutableList().also { list ->
                                                val current = list[index] as? InputField.ImageInput
                                                if (current != null) {
                                                    list[index] = current.copy(localUri = previousLocalUri, value = previousValue)
                                                }
                                            }
                                            viewModel.reportError("Image upload failed — please try again")
                                        }
                                    } finally {
                                        pendingImageUploads = pendingImageUploads - index
                                    }
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            val executionProgress by viewModel.executionProgress.collectAsState()
            
            if (executionStatus == ExecutionStatus.EXECUTING || executionStatus == ExecutionStatus.QUEUED) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    val hasOverall = executionProgress.overallProgress > 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (executionStatus == ExecutionStatus.QUEUED) "Queued..."
                                   else "Executing: ${executionProgress.currentNodeTitle ?: "Node #${executionProgress.currentNodeId ?: "?"}"}" +
                                        (if (executionProgress.maxSteps > 0) " (${executionProgress.currentStep}/${executionProgress.maxSteps})" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasOverall) {
                            Text(
                                text = "${(executionProgress.overallProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (hasOverall) {
                        LinearProgressIndicator(
                            progress = { executionProgress.overallProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

                // Batch Generation Selector
                var batchCount by remember { mutableStateOf(1) }
                val currentBatchCount by remember { mutableStateOf(1) }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Batch Count:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                    androidx.compose.material3.FilledIconButton(
                        onClick = { if (batchCount > 1) batchCount-- },
                        enabled = batchCount > 1,
                        modifier = Modifier.size(36.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease Batch")
                    }
                    
                    Text(
                        text = "$batchCount",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    androidx.compose.material3.FilledIconButton(
                        onClick = { if (batchCount < 10) batchCount++ },
                        enabled = batchCount < 10,
                        modifier = Modifier.size(36.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase Batch")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                    // Add to Queue (Secondary)
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            withPreflight { viewModel.addToQueue(workflow, inputs, batchCount) }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = (executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED) && pendingImageUploads.isEmpty() && !checking
                    ) {
                         Icon(Icons.Default.Add, contentDescription = null)
                         Spacer(Modifier.width(8.dp))
                         Text("Queue")
                    }

                    // Generate (Primary)
                    Button(
                        onClick = {
                            withPreflight { viewModel.executeWorkflow(workflow, inputs, batchCount) }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = (executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED) && pendingImageUploads.isEmpty() && !checking
                    ) {
                        if (executionStatus == ExecutionStatus.EXECUTING || executionStatus == ExecutionStatus.QUEUED) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp).padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("Running...")
                        } else if (pendingImageUploads.isNotEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp).padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("Uploading image...")
                        } else {
                            Text("Generate")
                        }
                    }
                }
                
                // View Queue Link
                androidx.compose.material3.TextButton(
                    onClick = onViewQueue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Queue Manager")
                }

            // Save as Template Button (only show if it's a temporary/history workflow)
            if (workflow.id == 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        // We need the injected JSON or just the current state?
                        // Actually, saving the template means saving the jsonContent.
                        // But maybe we want to save with the current values?
                        // For now, save the base jsonContent. 
                        viewModel.importWorkflow(workflow.name, workflow.jsonContent, com.example.comfyui_remote.domain.WorkflowSource.LOCAL_IMPORT) {
                            // No navigation needed when saving as template from here
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save as Template")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val image by viewModel.generatedImage.collectAsState()
            val generatedMediaId by viewModel.generatedMediaId.collectAsState()
            
            if (image != null) {
                // Ensure we are in an item block for Composable content if needed, 
                // but observing strictly, we are replacing an existing block. 
                // We'll output the content assuming the context allows it (or adding item {} if appropriate, but avoiding nesting risks).
                // Given the clutter, let's just output the content directly and fix the logic.
                
                Text("Result:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.clickable(enabled = generatedMediaId != null) {
                    generatedMediaId?.let { onViewInGallery(it) }
                }) {
                    coil.compose.AsyncImage(
                        model = image,
                        contentDescription = "Generated Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    
                    if (generatedMediaId != null) {
                         androidx.compose.material3.Surface(
                             color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                             shape = MaterialTheme.shapes.small,
                             modifier = Modifier.padding(8.dp)
                         ) {
                             Row(
                                 verticalAlignment = Alignment.CenterVertically,
                                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                             ) {
                                 Icon(
                                     Icons.Default.Search, 
                                     contentDescription = null,
                                     modifier = Modifier.size(16.dp)
                                 )
                                 Spacer(modifier = Modifier.width(4.dp))
                                 Text("Open in Gallery", style = MaterialTheme.typography.labelSmall)
                             }
                         }
                    }
                }
            }
        }
    }

    if (showNodeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNodeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text("Workflow Architecture", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                
                val allNodes = viewModel.parseAllNodes(workflow.jsonContent)
                LazyColumn {
                    items(allNodes) { node ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row {
                                Text("#${node.id}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(node.title, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Class: ${node.classType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
    val warnings = issues.size - errors
    val byNode = issues
        .sortedBy { if (it.severity == PromptValidator.Severity.ERROR) 0 else 1 }
        .groupBy { it.nodeTitle to it.classType }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (errors > 0) "Server will likely reject this prompt" else "Server may reject this prompt") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    listOfNotNull(
                        if (errors > 0) "$errors error${if (errors == 1) "" else "s"}" else null,
                        if (warnings > 0) "$warnings warning${if (warnings == 1) "" else "s"}" else null
                    ).joinToString(", "),
                    style = MaterialTheme.typography.labelLarge
                )
                if (missingModelCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$missingModelCount missing model file${if (missingModelCount == 1) "" else "s"} can be fetched from the Missing Models card",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                byNode.forEach { (node, nodeIssues) ->
                    val (title, classType) = node
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (classType.isNotEmpty() && classType != title) "$title ($classType)" else title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    nodeIssues.forEach { issue ->
                        val prefix = if (issue.severity == PromptValidator.Severity.ERROR) "✖" else "⚠"
                        Text(
                            "$prefix ${issue.inputName?.let { "$it: " } ?: ""}${issue.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (issue.severity == PromptValidator.Severity.ERROR) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    onProbe: suspend (ModelSource) -> Pair<Long?, Boolean>?,
    onDownload: (ModelSource) -> Unit,
    onDownloadAll: (List<ModelSource>) -> Unit,
    onCancel: (com.example.comfyui_remote.domain.ModelDownload) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirming by remember { mutableStateOf<ModelSource?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<Pair<Long?, Boolean>?>(null) }
    val onCard = MaterialTheme.colorScheme.onErrorContainer
    // Phase 99: Download all; probes of every model to download, null entries while probing
    var confirmingAll by remember { mutableStateOf<List<ModelSource>?>(null) }
    var allProbes by remember { mutableStateOf<List<Pair<Long?, Boolean>?>?>(null) }
    val downloadable = models.filter { downloads["${it.directory}/${it.name}"]?.active != true }
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

    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⚠️ Missing Models on Server",
                    style = MaterialTheme.typography.titleSmall,
                    color = onCard,
                    modifier = Modifier.weight(1f)
                )
                if (helperAvailable == true && downloadable.size >= 2) {
                    androidx.compose.material3.TextButton(onClick = {
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
            models.forEach { model ->
                val download = downloads["${model.directory}/${model.name}"]
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.name, style = MaterialTheme.typography.bodyMedium, color = onCard)
                        Text("models/${model.directory}", style = MaterialTheme.typography.bodySmall, color = onCard)
                    }
                    when {
                        download?.active == true ->
                            androidx.compose.material3.TextButton(
                                onClick = { onCancel(download) },
                                enabled = download.id != null
                            ) { Text("Cancel") }
                        helperAvailable == true ->
                            androidx.compose.material3.TextButton(onClick = {
                                confirming = model
                                probe = null
                                probing = true
                                scope.launch {
                                    probe = onProbe(model)
                                    probing = false
                                }
                            }) { Text(if (download?.status == "error") "Retry" else "Download") }
                        else ->
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
                        Text(
                            "Gated or unavailable: ${gated.joinToString { it.name }}. " +
                                if (helperHasToken) "The server's HF_TOKEN account must have accepted their licences."
                                else "Set HF_TOKEN on the server after accepting their licences, or these will fail.",
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
                            if (helperHasToken) "This model is gated: the Hugging Face account of the server's HF_TOKEN must have accepted its licence."
                            else "This model is gated: accept its licence on huggingface.co and set HF_TOKEN on the server, or the download will fail.",
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
