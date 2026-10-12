package com.example.comfyui_remote.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/**
 * A yes/no dialog (UI spec §6): TextButtons, dismiss then confirm; a [destructive] confirm is shown in the
 * error colour. Use it for every delete.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    ConfirmDialog(
        title = "Delete workflow?",
        text = "“Z-Image-Turbo: Text to Image” will be removed from this phone.",
        confirmLabel = "Delete",
        destructive = true,
        onConfirm = {},
        onDismiss = {}
    )
}
