package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.data.SavedGalleryList
import kotlinx.coroutines.launch

/**
 * Dialog for saving the current gallery list as a named list.
 * Users can choose between saving as a snapshot (current items) or live filter (filter config).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveListDialog(
    filter: GallerySyncFilter,
    onDismiss: () -> Unit,
    onSave: (name: String, listType: SavedGalleryList.ListType) -> Unit,
    viewModel: MainViewModel
) {
    var listName by remember { mutableStateOf("") }
    var listType by remember { mutableStateOf(SavedGalleryList.ListType.LIVE_FILTER) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val isValidName = listName.isNotBlank() && listName.length <= 50

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Save Gallery List")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // List Name Input
                OutlinedTextField(
                    value = listName,
                    onValueChange = { 
                        listName = it
                        nameError = when {
                            it.isBlank() -> "Name is required"
                            it.length > 50 -> "Name must be 50 characters or less"
                            else -> null
                        }
                    },
                    label = { Text("List Name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                // Save Mode Selection
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Save Mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = listType == SavedGalleryList.ListType.LIVE_FILTER,
                            onClick = { listType = SavedGalleryList.ListType.LIVE_FILTER },
                            label = { Text("Live Filter") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = listType == SavedGalleryList.ListType.SNAPSHOT,
                            onClick = { listType = SavedGalleryList.ListType.SNAPSHOT },
                            label = { Text("Snapshot") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Description of selected mode
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = when (listType) {
                                    SavedGalleryList.ListType.LIVE_FILTER -> "Live Filter"
                                    SavedGalleryList.ListType.SNAPSHOT -> "Snapshot"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = when (listType) {
                                    SavedGalleryList.ListType.LIVE_FILTER -> 
                                        "Saves the filter configuration. When applied, it will re-sync with the server to get the latest matching items."
                                    SavedGalleryList.ListType.SNAPSHOT -> 
                                        "Saves the exact list of current items. The list remains static even if new items are added to the server."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Filter Summary
                if (filter.isActive()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Active Filters",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = filter.getSummary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val coroutineScope = rememberCoroutineScope()
            var isSaving by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (isValidName) {
                        isSaving = true
                        coroutineScope.launch {
                            viewModel.saveCurrentGalleryList(listName, listType)
                            isSaving = false
                            onSave(listName, listType)
                        }
                    }
                },
                enabled = isValidName && !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
