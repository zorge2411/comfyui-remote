package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.comfyui_remote.domain.PromptValidator

/**
 * Pre-flight issues grouped by node, errors first (Phases 91 and 103): the counts, then [afterCounts],
 * then each node with its issues. Used by the form's pre-flight dialog and the workflow list's badge.
 */
@Composable
fun IssueList(
    issues: List<PromptValidator.Issue>,
    modifier: Modifier = Modifier,
    afterCounts: @Composable () -> Unit = {}
) {
    val errors = issues.count { it.severity == PromptValidator.Severity.ERROR }
    val warnings = issues.size - errors
    val byNode = issues
        .sortedBy { if (it.severity == PromptValidator.Severity.ERROR) 0 else 1 }
        .groupBy { it.nodeTitle to it.classType }
    Column(modifier = modifier) {
        Text(
            listOfNotNull(
                if (errors > 0) "$errors error${if (errors == 1) "" else "s"}" else null,
                if (warnings > 0) "$warnings warning${if (warnings == 1) "" else "s"}" else null
            ).joinToString(", "),
            style = MaterialTheme.typography.labelLarge
        )
        afterCounts()
        byNode.forEach { (node, nodeIssues) ->
            val (title, classType) = node
            Spacer(Modifier.height(Dimens.m))
            Text(
                if (classType.isNotEmpty() && classType != title) "$title ($classType)" else title,
                style = MaterialTheme.typography.titleSmall
            )
            // Outlined icons instead of text marks (UI spec §5; Phase 105)
            nodeIssues.forEach { issue ->
                val error = issue.severity == PromptValidator.Severity.ERROR
                val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = Dimens.xs)) {
                    Icon(
                        if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                        contentDescription = if (error) "Error" else "Warning",
                        tint = color,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Text(
                        "${issue.inputName?.let { "$it: " } ?: ""}${issue.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier.padding(start = Dimens.xs)
                    )
                }
            }
        }
    }
}
