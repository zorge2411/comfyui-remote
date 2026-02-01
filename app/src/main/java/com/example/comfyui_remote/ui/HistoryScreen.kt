package com.example.comfyui_remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GeneratedMediaListing
import com.example.comfyui_remote.ui.components.EmptyState
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.util.*

// Reusable date formatter to avoid instantiation on every recomposition
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onNavigateToSettings: () -> Unit = {}) {
    val historyList by viewModel.allMedia.collectAsState(initial = emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isSecure by viewModel.isSecure.collectAsState()
    val currentHost by viewModel.host.collectAsState()
    val currentPort by viewModel.port.collectAsState()
    
    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    val historyStartDate = viewModel.historyStartDate.collectAsState()
    val historyEndDate = viewModel.historyEndDate.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Execution History") },
                actions = {
                    // Date filter button
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Filter by Date"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { 
                viewModel.syncHistory(historyStartDate.value, historyEndDate.value)
            },
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Date range indicator
            if (historyStartDate.value != null || historyEndDate.value != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                if (historyStartDate.value != null) {
                                    append("From: ${java.time.Instant.ofEpochMilli(historyStartDate.value!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}")
                                }
                                if (historyEndDate.value != null) {
                                    if (historyStartDate.value != null) append(" | ")
                                    append("To: ${java.time.Instant.ofEpochMilli(historyEndDate.value!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.clearHistoryDateRange() }) {
                            Text("Clear")
                        }
                    }
                }
            }
            
            if (historyList.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "No History Found",
                    message = "Your execution history will appear here once you generate images."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = historyList,
                        key = { it.id }
                    ) { item ->
                        HistoryItemCard(
                            item = item,
                            isSecure = isSecure,
                            currentHost = currentHost,
                            currentPort = currentPort,
                            onClick = {
                                android.util.Log.d("HISTORY_DEBUG", "==================== HISTORY ITEM CLICKED ====================")
                                android.util.Log.d("HISTORY_DEBUG", "Item ID: ${item.id}")
                                android.util.Log.d("HISTORY_DEBUG", "Workflow Name: ${item.workflowName}")
                                android.util.Log.d("HISTORY_DEBUG", "Timestamp: ${item.timestamp}")
                                android.util.Log.d("HISTORY_DEBUG", "FileName: ${item.fileName}")
                                android.util.Log.d("HISTORY_DEBUG", "Subfolder: ${item.subfolder}")
                                android.util.Log.d("HISTORY_DEBUG", "ServerType: ${item.serverType}")
                                android.util.Log.d("HISTORY_DEBUG", "MediaType: ${item.mediaType}")
                                android.util.Log.d("HISTORY_DEBUG", "Calling viewModel.loadHistory()...")
                                viewModel.loadHistory(item)
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Date picker dialog
    if (showDatePicker) {
        com.example.comfyui_remote.ui.components.DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { startDate, endDate ->
                viewModel.setHistoryDateRange(startDate, endDate)
                showDatePicker = false
                viewModel.syncHistory(startDate, endDate)
            },
            initialStartDate = historyStartDate.value,
            initialEndDate = historyEndDate.value
        )
    }
}

@Composable
fun HistoryItemCard(
    item: GeneratedMediaListing,
    isSecure: Boolean,
    currentHost: String,
    currentPort: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            val url = item.constructUrl(currentHost, currentPort, isSecure)
            AsyncImage(
                model = url,
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .size(80.dp)
                    .padding(end = 16.dp),
                contentScale = ContentScale.Crop
            )

            Column {
                Text(
                    text = item.workflowName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = DATE_FORMATTER.format(Instant.ofEpochMilli(item.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (true) {
                    Text(
                        text = "Tap to Restore",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
