package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ImportWorkflowDialog(
    onDismissRequest: () -> Unit,
    onImport: (name: String, json: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var jsonContent by remember { mutableStateOf("") }
    var isJsonError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Import Workflow (API Format)")
        },
        text = {
            Column {
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
                        isJsonError = false
                    },
                    label = { Text("Paste JSON Here") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    isError = isJsonError
                )
                if (isJsonError) {
                    Text(text = "Invalid JSON", color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && jsonContent.isNotBlank()) {
                        // Basic validation: Check if it starts/ends with braces
                        val trimmed = jsonContent.trim()
                        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                            onImport(name, trimmed)
                        } else {
                            isJsonError = true
                        }
                    }
                }
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
