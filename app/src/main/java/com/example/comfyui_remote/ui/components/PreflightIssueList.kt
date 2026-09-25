package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.domain.PreflightResult

/**
 * Pre-flight issues grouped by node (Phase 91). Kept separate so server node_errors
 * (Phase 92) can be shown the same way. No scrolling or truncation: the caller decides.
 */
@Composable
fun PreflightIssueList(
    result: PreflightResult,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onErrorContainer
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        result.nodes.forEach { node ->
            Column {
                Text(node.heading, style = MaterialTheme.typography.titleSmall, color = color)
                node.issues.forEach { issue ->
                    Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
    }
}

/** Form-screen card: one-line summary, expands to the grouped list. Informational only. */
@Composable
fun PreflightCard(result: PreflightResult, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Server compatibility",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        result.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (expanded) {
                PreflightIssueList(result, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Queue-time warning: the prompt may fail on the server; the user can queue anyway. */
@Composable
fun PreflightDialog(result: PreflightResult, onQueueAnyway: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("This workflow may fail") },
        text = {
            Column {
                Text(result.summary(), style = MaterialTheme.typography.bodyMedium)
                PreflightIssueList(
                    result,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = { TextButton(onClick = onQueueAnyway) { Text("Queue anyway") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
