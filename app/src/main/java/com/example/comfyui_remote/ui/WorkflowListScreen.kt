package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity

@Composable
fun WorkflowListScreen(
    viewModel: MainViewModel,
    onWorkflowValidation: (WorkflowEntity) -> Unit // Will navigate to detail/run screen
) {
    val workflows by viewModel.allWorkflows.collectAsState(initial = emptyList())
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Import Workflow")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (workflows.isEmpty()) {
                Text(
                    text = "No workflows. Click + to import.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(workflows) { workflow ->
                        WorkflowItem(
                            workflow = workflow,
                            onDelete = { viewModel.deleteWorkflow(workflow) },
                            onClick = { onWorkflowValidation(workflow) }
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportWorkflowDialog(
            onDismissRequest = { showImportDialog = false },
            onImport = { name, json ->
                viewModel.importWorkflow(name, json)
                showImportDialog = false
            }
        )
    }
}

@Composable
fun WorkflowItem(
    workflow: WorkflowEntity,
    onDelete: () -> Unit,
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
                Text(
                    text = "Created: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(workflow.createdAt))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
