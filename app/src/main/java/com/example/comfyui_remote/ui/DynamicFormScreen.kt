package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.comfyui_remote.network.ExecutionStatus
import kotlin.random.Random

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormScreen(
    viewModel: MainViewModel,
    workflow: WorkflowEntity,
    onBack: () -> Unit,
    onViewInGallery: (Long) -> Unit
) {
    // We need to parse inputs once
    var inputs by remember { mutableStateOf<List<InputField>>(emptyList()) }
    // Initialize parsed inputs
    LaunchedEffect(workflow) {
        inputs = viewModel.parseWorkflowInputs(workflow.jsonContent)
    }

    var showNodeSheet by remember { mutableStateOf(false) }

    val executionStatus by viewModel.executionStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            if (!workflow.missingNodes.isNullOrBlank()) {
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
                            workflow.missingNodes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Detailed Error Message
            if (executionStatus == ExecutionStatus.ERROR && errorMessage != null) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { viewModel.clearErrorMessage() }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "❌ Execution Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
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
                            label = { Text("${inputField.nodeTitle} (${inputField.fieldName})") },
                            minLines = 3,
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
                            label = { Text("${inputField.nodeTitle} (${inputField.fieldName})") },
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
                            label = { Text("${inputField.nodeTitle} (${inputField.fieldName})") },
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
                                label = { Text("${inputField.nodeTitle} (${inputField.fieldName})") },
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

                        com.example.comfyui_remote.ui.components.ImageSelector(
                            label = "${inputField.nodeTitle} (${inputField.fieldName})",
                            currentUri = inputField.localUri,
                            onImageSelected = { uri ->
                                // 1. Optimistic Update
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(localUri = uri.toString(), value = null)
                                }
                                
                                // 2. Trigger Upload
                                scope.launch {
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
                                        // Upload failed
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (executionStatus == ExecutionStatus.QUEUED) "Queued..." 
                                   else "Executing: ${executionProgress.currentNodeTitle ?: "Node #${executionProgress.currentNodeId ?: "?"}"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (executionProgress.maxSteps > 0) {
                            Text(
                                text = "${(executionProgress.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (executionProgress.maxSteps > 0) {
                        LinearProgressIndicator(
                            progress = { executionProgress.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.executeWorkflow(workflow, inputs)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED
            ) {
                if (executionStatus == ExecutionStatus.EXECUTING || executionStatus == ExecutionStatus.QUEUED) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp).padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("Running...")
                } else {
                    Text("Generate")
                }
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
            
            if (image != null) {
                Text("Result:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(contentAlignment = Alignment.BottomEnd) {
                    coil.compose.AsyncImage(
                        model = image,
                        contentDescription = "Generated Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
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
                    .padding(bottom = 32.dp)
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
