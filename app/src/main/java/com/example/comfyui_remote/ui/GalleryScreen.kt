package com.example.comfyui_remote.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GeneratedMediaEntity

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onMediaClick: (GeneratedMediaEntity) -> Unit
) {
    val mediaList by viewModel.allMedia.collectAsState(initial = emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState()
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val itemsToDelete = mediaList.filter { it.id in selectedIds }
                            viewModel.deleteMedia(itemsToDelete)
                            selectedIds.clear()
                        }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Gallery") }
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncHistory() },
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (mediaList.isEmpty()) {
                Text(
                    text = "No generated images yet.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(mediaList, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        GalleryItem(
                            item = item,
                            isSelected = isSelected,
                            onLongClick = {
                                if (isSelected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                                } else {
                                    onMediaClick(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalleryItem(
    item: GeneratedMediaEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Construct URL: http://{host}:{port}/view?filename={fileName}&subfolder={subfolder}&type=output
    val url = "http://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=output"

    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .let {
                if (isSelected) {
                    it.border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                } else it
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = url,
                contentDescription = "Generated Media",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                }
            }
            
            if (item.mediaType == "VIDEO") {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}
