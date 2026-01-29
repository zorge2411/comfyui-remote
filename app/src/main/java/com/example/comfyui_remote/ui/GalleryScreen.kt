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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GeneratedMediaListing
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File
import coil.imageLoader
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onMediaClick: (GeneratedMediaListing) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val mediaList by viewModel.allMedia.collectAsState(initial = emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isSecure by viewModel.isSecure.collectAsState()
    val currentHost by viewModel.host.collectAsState()
    val currentPort by viewModel.port.collectAsState()
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }
    val context = LocalContext.current
    var showSelectionDialog by remember { mutableStateOf(false) }
    var cameraTmpUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraTmpUri = cameraTmpUriString?.let { Uri.parse(it) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.uploadManualImage(uri, context.contentResolver)
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && cameraTmpUri != null) {
                viewModel.uploadManualImage(cameraTmpUri, context.contentResolver)
            } else {
                android.widget.Toast.makeText(context, "Camera capture failed or aborted", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                launchCamera(context, { cameraTmpUriString = it.toString() }, cameraLauncher)
            }
        }
    )

    fun handleCameraAction() {
        when (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)) {
            android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                launchCamera(context, { cameraTmpUriString = it.toString() }, cameraLauncher)
            }
            else -> {
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

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
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                if (showSelectionDialog) {
                    AlertDialog(
                        onDismissRequest = { showSelectionDialog = false },
                        title = { Text("Add Image") },
                        text = {
                            Column {
                                TextButton(
                                    onClick = {
                                        showSelectionDialog = false
                                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pick from Gallery", modifier = Modifier.fillMaxWidth())
                                }
                                TextButton(
                                    onClick = {
                                        showSelectionDialog = false
                                        handleCameraAction()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Take Photo", modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSelectionDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                
                FloatingActionButton(onClick = {
                    showSelectionDialog = true
                }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Add Image")
                }
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
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                val context = LocalContext.current
                val imageLoader = context.imageLoader
                
                LaunchedEffect(gridState.firstVisibleItemIndex) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { lastIndex ->
                            if (lastIndex != null) {
                                val preloadCount = 12 // Preload 4 rows
                                val start = lastIndex + 1
                                val end = (start + preloadCount).coerceAtMost(mediaList.size - 1)
                                
                                if (start <= end) {
                                    val screenWidth = context.resources.displayMetrics.widthPixels
                                    val targetSize = screenWidth / 3
                                    
                                    for (i in start..end) {
                                        val item = mediaList[i]
                                        val url = "http://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=${item.serverType}"
                                        
                                        val request = coil.request.ImageRequest.Builder(context)
                                            .data(url)
                                            .size(targetSize)
                                            .precision(coil.size.Precision.EXACT)
                                            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                            .build()
                                        imageLoader.enqueue(request)
                                    }
                                }
                            }
                        }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(
                        items = mediaList,
                        key = { it.id },
                        contentType = { "media" }
                    ) { item ->
                        val isSelected = item.id in selectedIds
                        GalleryItem(
                            item = item,
                            isSelected = isSelected,
                            isSecure = isSecure,
                            currentHost = currentHost,
                            currentPort = currentPort,
                            onLongClick = {
                                if (isSelected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                                } else {
                                    onMediaClick(item)
                                }
                            },
                            isScrolling = isScrolling,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }
    }
}

private fun launchCamera(
    context: android.content.Context,
    onUriUpdate: (Uri) -> Unit,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
) {
    val file = File(context.cacheDir, "shared/camera_${System.currentTimeMillis()}.jpg")
    file.parentFile?.mkdirs()
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    onUriUpdate(uri)
    cameraLauncher.launch(uri)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryItem(
    item: GeneratedMediaListing,
    isSelected: Boolean,
    isSecure: Boolean,
    currentHost: String,
    currentPort: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isScrolling: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    // Construct URL
    val context = LocalContext.current
    val imageRequest = remember(item.serverHost, item.serverPort, item.fileName, item.subfolder, item.serverType, isSecure, currentHost, currentPort) {
        val portInt = currentPort.toIntOrNull() ?: 8188
        val shouldUseSecure = isSecure && item.serverHost == currentHost && item.serverPort == portInt
        val protocol = if (shouldUseSecure) "https" else "http"
        val url = "$protocol://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=${item.serverType}"
        
        // Calculate approximate size for grid (assuming 3 columns)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val targetSize = screenWidth / 3

        coil.request.ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .size(targetSize)
            .precision(coil.size.Precision.EXACT)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // 50% memory saving for thumbs
            .build()
    }

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
                model = imageRequest,
                contentDescription = "Generated Media",
                modifier = Modifier
                    .fillMaxSize()
                    .let { modifier ->
                         // ONLY apply shared element if NOT scrolling to ensure 60fps
                         if (!isScrolling && sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                modifier.sharedElement(
                                    state = rememberSharedContentState(key = "image-${item.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else modifier
                    },
                contentScale = ContentScale.Crop
            )
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
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
