package com.example.comfyui_remote.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.MainViewModel.DuplicateChoice

/** Asks what to do when an import matches a workflow that's already saved (Phase 98). */
@Composable
fun DuplicateImportDialog(viewModel: MainViewModel) {
    val prompt by viewModel.duplicatePrompt.collectAsState()
    prompt?.let { p ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveDuplicate(null) },
            title = { Text("Already imported") },
            text = { Text("\"${p.existing.name}\" is already in your workflows. Open it, import another copy, or replace it with this version?") },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveDuplicate(DuplicateChoice.OPEN) }) { Text("Open existing") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { viewModel.resolveDuplicate(DuplicateChoice.COPY) }) { Text("Copy") }
                    TextButton(onClick = { viewModel.resolveDuplicate(DuplicateChoice.REPLACE) }) { Text("Replace") }
                    TextButton(onClick = { viewModel.resolveDuplicate(null) }) { Text("Cancel") }
                }
            }
        )
    }
}
