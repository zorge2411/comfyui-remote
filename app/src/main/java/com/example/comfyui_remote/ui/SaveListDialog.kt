package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.ui.components.Dimens
import kotlinx.coroutines.launch

/** Names and saves [filter] as a list (Phase 104: saved lists are named filters). */
@Composable
fun SaveListDialog(
    filter: GallerySyncFilter,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MainViewModel
) {
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val valid = name.isNotBlank() && name.length <= 50

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = name.length > 50,
                    supportingText = if (name.length > 50) { { Text("50 characters at most") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    filterSummary(filter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    scope.launch {
                        viewModel.saveCurrentGalleryList(name.trim(), filter)
                        saving = false
                        onSaved()
                    }
                },
                enabled = valid && !saving
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
