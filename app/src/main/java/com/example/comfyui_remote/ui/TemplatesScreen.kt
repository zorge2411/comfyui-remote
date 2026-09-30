package com.example.comfyui_remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.domain.WorkflowTemplate
import com.example.comfyui_remote.domain.WorkflowTemplateIndex
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.EmptyState
import com.example.comfyui_remote.ui.components.LoadingIndicator
import com.example.comfyui_remote.ui.components.LoadingOverlay
import com.example.comfyui_remote.ui.components.StatusBanner
import com.example.comfyui_remote.ui.components.StatusKind
import kotlinx.coroutines.flow.drop

/**
 * Browses the workflow templates the connected ComfyUI server ships (/templates/index.json).
 * Tapping a template imports it into the local workflow list and opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenWorkflow: (WorkflowEntity) -> Unit
) {
    val categories by viewModel.templateCategories.collectAsState()
    val loading by viewModel.templatesLoading.collectAsState()
    val error by viewModel.templatesError.collectAsState()
    val importError by viewModel.templateImportError.collectAsState()
    val isImporting by viewModel.isSyncing.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var localOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.fetchTemplates() }

    // Jump back to the top when the filters change, but keep the position when returning to this screen.
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { Triple(query, category, localOnly) }
            .drop(1)
            .collect { gridState.scrollToItem(0) }
    }

    val visible = remember(categories, query, category, localOnly) {
        WorkflowTemplateIndex.filter(categories, query, category).filter { !localOnly || it.openSource }
    }

    Scaffold(
        topBar = {
            AppTopBar("Templates", onBack = onBack) {
                IconButton(onClick = { viewModel.fetchTemplates(force = true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload templates")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && categories.isEmpty() -> LoadingIndicator(modifier = Modifier.fillMaxSize())
                error != null && categories.isEmpty() -> EmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "No templates",
                    message = error ?: "",
                    actionText = "Retry",
                    onAction = { viewModel.fetchTemplates(force = true) }
                )
                else -> Column(Modifier.fillMaxSize()) {
                    // A failed reload keeps the templates already shown (Phase 103)
                    error?.let { message ->
                        StatusBanner(
                            StatusKind.Error,
                            "Couldn't reload templates",
                            message = message,
                            modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.s),
                            actions = {
                                TextButton(onClick = { viewModel.clearTemplatesError() }) { Text("Dismiss") }
                                TextButton(onClick = { viewModel.fetchTemplates(force = true) }) { Text("Retry") }
                            }
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.s),
                        // One line in portrait; models and tags are searched too (device check)
                        placeholder = { Text("Search templates", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.s)
                    ) {
                        FilterChip(
                            selected = localOnly,
                            onClick = { localOnly = !localOnly },
                            label = { Text("Local only") }
                        )
                        FilterChip(
                            selected = category == null,
                            onClick = { category = null },
                            label = { Text("All") }
                        )
                        categories.forEach { c ->
                            FilterChip(
                                selected = category == c.title,
                                onClick = { category = if (category == c.title) null else c.title },
                                label = { Text(c.title) }
                            )
                        }
                    }
                    Text(
                        "${visible.size} templates",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.xs)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Dimens.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                        verticalArrangement = Arrangement.spacedBy(Dimens.s)
                    ) {
                        items(visible, key = { it.name }) { template ->
                            TemplateCard(
                                template = template,
                                thumbnailUrl = template.thumbnailUrl(viewModel.serverBaseUrl),
                                onClick = {
                                    viewModel.importTemplate(template) { workflow -> onOpenWorkflow(workflow) }
                                }
                            )
                        }
                    }
                }
            }

            if (isImporting) {
                LoadingOverlay(importStatus.ifEmpty { "Importing template…" })
            }
        }
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearTemplateImportError() },
            title = { Text("Couldn't import template") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearTemplateImportError() }) { Text("OK") }
            }
        )
    }
}

/** One template: thumbnail with an "API" badge for paid-API templates, title and models (AppCard colours). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCard(template: WorkflowTemplate, thumbnailUrl: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box {
            val placeholder = rememberVectorPainter(
                if (template.mediaSubtype == "mp3") Icons.Default.AudioFile else Icons.Default.Image
            )
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = template.title,
                placeholder = placeholder,
                error = placeholder,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (!template.openSource) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.s)
                ) {
                    Text(
                        "API",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Dimens.s, vertical = 2.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(Dimens.m)) {
            Text(
                template.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (template.models.isNotEmpty()) {
                Text(
                    template.models.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
