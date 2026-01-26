package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.network.ServerWorkflowFile

// Reusable date formatter to avoid instantiation on every recomposition
private val DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault())
    .withZone(java.time.ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    viewModel: MainViewModel,
    onWorkflowValidation: (WorkflowEntity) -> Unit // Will navigate to detail/run screen
) {
    val workflows by viewModel.allWorkflows.collectAsState(initial = emptyList())
    val serverWorkflows by viewModel.serverWorkflows.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<WorkflowEntity?>(null) }

    Scaffold(
        topBar = {
             androidx.compose.material3.TopAppBar(
                 title = { Text("Workflows") },
                 actions = {
                     IconButton(onClick = { 
                         viewModel.syncHistory() 
                         viewModel.fetchServerWorkflows()
                     }) {
                         Icon(Icons.Default.Refresh, contentDescription = "Sync from Server")
                     }
                 }
             )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Import Workflow")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (workflows.isEmpty() && serverWorkflows.isEmpty()) {
                Text(
                    text = "No workflows. Click + to import.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (workflows.isNotEmpty()) {
                        item {
                            Text(
                                "Local Workflows", 
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    items(
                        items = workflows,
                        key = { it.id }
                    ) { workflow ->
                        WorkflowItem(
                            workflow = workflow,
                            onDelete = { viewModel.deleteWorkflow(workflow) },
                            onRename = { showRenameDialog = workflow },
                            onClick = { onWorkflowValidation(workflow) }
                        )
                    }

                    if (serverWorkflows.isNotEmpty()) {
                        item {
                             Text(
                                 "Server Workflows (Userdata)", 
                                 style = MaterialTheme.typography.labelLarge,
                                 modifier = Modifier.padding(16.dp),
                                 color = MaterialTheme.colorScheme.primary
                             )
                        }
                        items(
                            items = serverWorkflows,
                            key = { it.path ?: it.hashCode() }
                        ) { serverFile ->
                            ServerWorkflowItem(
                                serverFile = serverFile,
                                onClick = {
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

    showRenameDialog?.let { workflow ->
        var newName by remember { mutableStateOf(workflow.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Workflow") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Workflow Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.renameWorkflow(workflow, newName)
                    showRenameDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WorkflowItem(
    workflow: WorkflowEntity,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = workflow.name, style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Created: ${DATE_FORMATTER.format(java.time.Instant.ofEpochMilli(workflow.createdAt))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (workflow.baseModelName != null) {
                         androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                         Surface(
                             shape = RoundedCornerShape(4.dp),
                             color = MaterialTheme.colorScheme.secondaryContainer
                         ) {
                             Text(
                                 text = "📦 ${workflow.baseModelName}",
                                 style = MaterialTheme.typography.labelSmall,
                                 modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                         }
                    }
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun ServerWorkflowItem(
    serverFile: ServerWorkflowFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = serverFile.name ?: "Unnamed Workflow", style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Path: ${serverFile.fullpath ?: "?"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.Default.Add, 
                contentDescription = "Import from Server",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
