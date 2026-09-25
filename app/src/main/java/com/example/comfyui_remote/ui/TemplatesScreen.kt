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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.domain.WorkflowTemplate
import com.example.comfyui_remote.domain.WorkflowTemplateIndex
import com.example.comfyui_remote.ui.components.EmptyState

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

    val visible = remember(categories, query, category, localOnly) {
        WorkflowTemplateIndex.filter(categories, query, category).filter { !localOnly || it.openSource }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchTemplates(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload templates")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && categories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && categories.isEmpty() -> EmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "No Templates",
                    message = error ?: "",
                    actionText = "Retry",
                    onAction = { viewModel.fetchTemplates(force = true) }
                )
                else -> Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search templates, models, tags") },
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
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
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
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            importStatus.ifEmpty { "Importing template..." },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearTemplateImportError() },
            title = { Text("Import failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearTemplateImportError() }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCard(template: WorkflowTemplate, thumbnailUrl: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (!template.openSource) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                ) {
                    Text(
                        "API",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
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
