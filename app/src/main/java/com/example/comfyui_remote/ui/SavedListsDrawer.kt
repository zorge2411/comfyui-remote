package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.SavedGalleryList
import com.example.comfyui_remote.ui.components.AppCard
import com.example.comfyui_remote.ui.components.ConfirmDialog
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.EmptyState

/** Saved gallery lists (named filters, Phase 104): tap to apply; rename, duplicate or delete from the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedListsDrawer(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onApplyList: (String) -> Unit
) {
    val savedLists by viewModel.savedGalleryLists.collectAsState(initial = emptyList())
    var renaming by remember { mutableStateOf<SavedGalleryList?>(null) }
    var deleting by remember { mutableStateOf<SavedGalleryList?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.m)
        ) {
            Text("Saved lists", style = MaterialTheme.typography.titleLarge)
            if (savedLists.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Bookmarks,
                    title = "No saved lists",
                    message = "Save a filter from the Filter sheet to come back to it here.",
                    modifier = Modifier.heightIn(max = 320.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
                    modifier = Modifier.padding(bottom = Dimens.l)
                ) {
                    items(savedLists, key = { it.id }) { list ->
                        SavedListCard(
                            list = list,
                            onApply = { onApplyList(list.id) },
                            onRename = { renaming = list },
                            onDuplicate = { viewModel.duplicateSavedGalleryList(list.id) },
                            onDelete = { deleting = list }
                        )
                    }
                }
            }
        }
    }

    renaming?.let { list ->
        RenameListDialog(
            list = list,
            onDismiss = { renaming = null },
            onRename = { newName ->
                viewModel.renameSavedGalleryList(list.id, newName)
                renaming = null
            }
        )
    }

    deleting?.let { list ->
        ConfirmDialog(
            title = "Delete list?",
            text = "\"${list.name}\" is deleted. Images aren't affected.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteSavedGalleryList(list.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun SavedListCard(
    list: SavedGalleryList,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    AppCard(onClick = onApply) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    filterSummary(list.filter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(list.getAgeString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${list.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun RenameListDialog(list: SavedGalleryList, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(list.name) }
    val valid = name.isNotBlank() && name.length <= 50
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = !valid,
                supportingText = if (!valid) { { Text(if (name.isBlank()) "Enter a name" else "50 characters at most") } } else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name.trim()) }, enabled = valid) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
