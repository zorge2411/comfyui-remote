package com.example.comfyui_remote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ImportWorkflowDialog(
    onDismissRequest: () -> Unit,
    onImport: (name: String, json: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var jsonContent by remember { mutableStateOf("") }
    
    // Validation State
    var validationState by remember { mutableStateOf<JsonValidationState>(JsonValidationState.Empty) }
    
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // Helper to validate JSON content
    fun validateJson(content: String) {
        if (content.isBlank()) {
            validationState = JsonValidationState.Empty
            return
        }
        
        try {
            val trimmed = content.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                validationState = JsonValidationState.InvalidJson
                return
            }
            
            // Heuristic Check
            // API Format: {"3": {"inputs": ...}, "4": ...} (Keys are typically IDs)
            // Graph Format: {"last_node_id": 10, "last_link_id": ... "nodes": [...]}
            // Or simple check: if it has "nodes" array -> likely Graph Format.
            // If it has "last_node_id" -> definitely Graph Format.
            
            if (trimmed.contains("\"nodes\"") && trimmed.contains("\"links\"")) {
                validationState = JsonValidationState.GraphFormatDetected
            } else if (trimmed.contains("\"last_node_id\"")) {
                validationState = JsonValidationState.GraphFormatDetected
            } else {
                // assume valid API format for now if it parses as object
                validationState = JsonValidationState.ValidApiFormat
            }
        } catch (e: Exception) {
            validationState = JsonValidationState.InvalidJson
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                // Get Filename
                val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "Imported Workflow"
                
                name = fileName.removeSuffix(".json").removeSuffix(".JSON")
                
                // Read content
                contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    jsonContent = text
                    validateJson(text)
                }
            } catch (e: Exception) {
                validationState = JsonValidationState.InvalidJson
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Import Workflow (API Format)")
        },
        text = {
            Column {
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select JSON File")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workflow Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = jsonContent,
                    onValueChange = {
                        jsonContent = it
                        validateJson(it)
                    },
                    label = { Text("Paste JSON Here") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationState is JsonValidationState.InvalidJson
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                when (val state = validationState) {
                    JsonValidationState.Empty -> {
                         Text(
                            text = "Note: Please use 'API format' (from ComfyUI > Save > API format).",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        )
                    }
                    JsonValidationState.ValidApiFormat -> {
                        Text(
                            text = "✓ Valid API Format detected",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
                        )
                    }
                    JsonValidationState.GraphFormatDetected -> {
                        Text(
                            text = "✓ Graph Format detected (will be auto-converted)",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
                        )
                    }
                    JsonValidationState.InvalidJson -> {
                        Text(
                            text = "Invalid JSON syntax",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && (validationState == JsonValidationState.ValidApiFormat || validationState == JsonValidationState.GraphFormatDetected)) {
                         onImport(name, jsonContent)
                    }
                },
                enabled = name.isNotBlank() && (validationState == JsonValidationState.ValidApiFormat || validationState == JsonValidationState.GraphFormatDetected)
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
        }
    )
}

sealed class JsonValidationState {
    object Empty : JsonValidationState()
    object ValidApiFormat : JsonValidationState()
    object GraphFormatDetected : JsonValidationState()
    object InvalidJson : JsonValidationState()
}
