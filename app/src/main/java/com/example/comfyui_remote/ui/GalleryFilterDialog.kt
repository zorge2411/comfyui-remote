package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.ui.components.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen dialog for configuring gallery sync filters.
 * Users can set date range, max items, workflow name, media type, and server filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryFilterDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSyncWithFilter: (GallerySyncFilter) -> Unit,
    onSaveAsList: (GallerySyncFilter) -> Unit
) {
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var maxItems by remember { mutableStateOf(100) }
    var workflowNameFilter by remember { mutableStateOf("") }
    var fileNameFilter by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf<GallerySyncFilter.MediaType?>(null) }
    var serverFilter by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(GallerySyncFilter.SortOrder.NEWEST_FIRST) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showSaveListDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Initialize with current filter state
    LaunchedEffect(Unit) {
        val currentFilter = viewModel.gallerySyncFilter.value
        startDate = currentFilter.startDate
        endDate = currentFilter.endDate
        maxItems = currentFilter.maxItems
        workflowNameFilter = currentFilter.workflowNameFilter ?: ""
        fileNameFilter = currentFilter.fileNameFilter ?: ""
        mediaType = currentFilter.mediaType
        serverFilter = currentFilter.serverFilter ?: ""
        sortOrder = currentFilter.sortOrder
    }

    if (showSaveListDialog) {
        SaveListDialog(
            filter = GallerySyncFilter(
                startDate = startDate,
                endDate = endDate,
                maxItems = maxItems,
                workflowNameFilter = workflowNameFilter.ifBlank { null },
                fileNameFilter = fileNameFilter.ifBlank { null },
                mediaType = mediaType,
                serverFilter = serverFilter.ifBlank { null },
                sortOrder = sortOrder
            ),
            onDismiss = { showSaveListDialog = false },
            onSave = { name, listType ->
                showSaveListDialog = false
            },
            viewModel = viewModel
        )
    }


    if (showStartDatePicker) {
        DatePickerDialog(
            onDateSelected = { date ->
                startDate = date.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDateSelected = { date ->
                endDate = date.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Filter Syntax Help") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Advanced filtering supports the following syntax:", fontWeight = FontWeight.Bold)
                    Text("• Semicolon (;) - Separate multiple search terms.")
                    Text("• Wildcard (*) - Match any sequence of characters.")
                    Text("• Exclusion (-) - Prefix with minus to hide matching items.")
                    Text("• Inclusion (+) - Prefix with plus (optional) to explicitly include items.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Example:", fontWeight = FontWeight.Bold)
                    Text("*.mp4; *temp* ; -skip*")
                    Text("(Shows MP4s and anything with 'temp', but hides anything starting with 'skip')")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Filter Gallery Sync")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date Range Section
                FilterSection(title = "Date Range") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (startDate != null) dateFormat.format(Date(startDate!!)) else "Start Date")
                        }
                        OutlinedButton(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (endDate != null) dateFormat.format(Date(endDate!!)) else "End Date")
                        }
                    }
                    if (startDate != null || endDate != null) {
                        TextButton(
                            onClick = {
                                startDate = null
                                endDate = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Date Range")
                        }
                    }
                }

                // Max Items Section
                FilterSection(title = "Max Items") {
                    Column {
                        Text("$maxItems items", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = maxItems.toFloat(),
                            onValueChange = { maxItems = it.toInt() },
                            valueRange = 10f..5000f,
                            steps = 49,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("10", style = MaterialTheme.typography.bodySmall)
                            Text("5000", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Workflow Name Section
                FilterSection(
                    title = "Workflow Name",
                    onHelpClick = { showHelpDialog = true }
                ) {
                    OutlinedTextField(
                        value = workflowNameFilter,
                        onValueChange = { workflowNameFilter = it },
                        label = { Text("Filter by workflow name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Filename Section
                FilterSection(
                    title = "Filename",
                    onHelpClick = { showHelpDialog = true }
                ) {
                    OutlinedTextField(
                        value = fileNameFilter,
                        onValueChange = { fileNameFilter = it },
                        label = { Text("Filter by filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Media Type Section
                FilterSection(title = "Media Type") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = mediaType == null,
                            onClick = { mediaType = null },
                            label = { Text("All") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = mediaType == GallerySyncFilter.MediaType.IMAGE,
                            onClick = { mediaType = GallerySyncFilter.MediaType.IMAGE },
                            label = { Text("Images") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = mediaType == GallerySyncFilter.MediaType.VIDEO,
                            onClick = { mediaType = GallerySyncFilter.MediaType.VIDEO },
                            label = { Text("Videos") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Server Filter Section
                FilterSection(title = "Server") {
                    OutlinedTextField(
                        value = serverFilter,
                        onValueChange = { serverFilter = it },
                        label = { Text("Filter by server (host:port)") },
                        singleLine = true,
                        placeholder = { Text("e.g., 192.168.1.100:8188") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Sort Order Section
                FilterSection(title = "Sort Order") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = sortOrder == GallerySyncFilter.SortOrder.NEWEST_FIRST,
                            onClick = { sortOrder = GallerySyncFilter.SortOrder.NEWEST_FIRST },
                            label = { Text("Newest") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = sortOrder == GallerySyncFilter.SortOrder.OLDEST_FIRST,
                            onClick = { sortOrder = GallerySyncFilter.SortOrder.OLDEST_FIRST },
                            label = { Text("Oldest") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        val filter = GallerySyncFilter(
                            startDate = startDate,
                            endDate = endDate,
                            maxItems = maxItems,
                            workflowNameFilter = workflowNameFilter.ifBlank { null },
                            mediaType = mediaType,
                            serverFilter = serverFilter.ifBlank { null },
                            sortOrder = sortOrder
                        )
                        showSaveListDialog = true
                    }
                ) {
                    Text("Save as List")
                }
                Button(
                    onClick = {
                        val filter = GallerySyncFilter(
                            startDate = startDate,
                            endDate = endDate,
                            maxItems = maxItems,
                            workflowNameFilter = workflowNameFilter.ifBlank { null },
                            fileNameFilter = fileNameFilter.ifBlank { null },
                            mediaType = mediaType,
                            serverFilter = serverFilter.ifBlank { null },
                            sortOrder = sortOrder
                        )
                        onSyncWithFilter(filter)
                    }
                ) {
                    Text("Sync with Filter")
                }
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
private fun FilterSection(
    title: String,
    onHelpClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (onHelpClick != null) {
                IconButton(
                    onClick = onHelpClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        content()
        HorizontalDivider()
    }
}
