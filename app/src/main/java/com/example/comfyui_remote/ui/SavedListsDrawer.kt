package com.example.comfyui_remote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.SavedGalleryList

/**
 * Bottom sheet drawer for displaying and managing saved gallery lists.
 * Users can view, apply, rename, duplicate, and delete saved lists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SavedListsDrawer(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onApplyList: (String) -> Unit
) {
    val savedLists by viewModel.savedGalleryLists.collectAsState(initial = emptyList())
    var showRenameDialog by remember { mutableStateOf<SavedGalleryList?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<SavedGalleryList?>(null) }

    if (showRenameDialog != null) {
        RenameListDialog(
            list = showRenameDialog!!,
            onDismiss = { showRenameDialog = null },
            onRename = { newName ->
                viewModel.renameSavedGalleryList(showRenameDialog!!.id, newName)
                showRenameDialog = null
            }
        )
    }

    if (showDeleteConfirmDialog != null) {
        DeleteListConfirmDialog(
            list = showDeleteConfirmDialog!!,
            onDismiss = { showDeleteConfirmDialog = null },
            onDelete = {
                viewModel.deleteSavedGalleryList(showDeleteConfirmDialog!!.id)
                showDeleteConfirmDialog = null
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Lists",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            // Lists
            if (savedLists.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "No Saved Lists",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Create your first list by applying filters and saving them",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedLists, key = { it.id }) { list ->
                        SavedListItem(
                            list = list,
                            onApply = { onApplyList(list.id) },
                            onRename = { showRenameDialog = list },
                            onDuplicate = { viewModel.duplicateSavedGalleryList(list.id) },
                            onDelete = { showDeleteConfirmDialog = list }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedListItem(
    list: SavedGalleryList,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onApply,
                onLongClick = { showMenu = true }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // List type icon
                Icon(
                    imageVector = when (list.listType) {
                        SavedGalleryList.ListType.SNAPSHOT -> Icons.Default.Bookmark
                        SavedGalleryList.ListType.LIVE_FILTER -> Icons.Default.FilterList
                    },
                    contentDescription = null,
                    tint = when (list.listType) {
                        SavedGalleryList.ListType.SNAPSHOT -> MaterialTheme.colorScheme.primary
                        SavedGalleryList.ListType.LIVE_FILTER -> MaterialTheme.colorScheme.secondary
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // List name
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Menu button
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // List type and item count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            text = list.getTypeDescription(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                Text(
                    text = list.getAgeString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Filter summary (if active)
            if (list.filter.isActive()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = list.filter.getSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameListDialog(
    list: SavedGalleryList,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(list.name) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val isValidName = newName.isNotBlank() && newName.length <= 50

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename List") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { 
                    newName = it
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidName) {
                        onRename(newName)
                    }
                },
                enabled = isValidName
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteListConfirmDialog(
    list: SavedGalleryList,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete List") },
        text = {
            Text("Are you sure you want to delete \"${list.name}\"? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
