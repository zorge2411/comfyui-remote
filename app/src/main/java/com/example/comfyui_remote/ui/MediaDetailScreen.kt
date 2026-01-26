package com.example.comfyui_remote.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import kotlin.math.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GeneratedMediaEntity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.IntSize

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MediaDetailScreen(
    viewModel: MainViewModel,
    mediaId: Long,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val mediaList by viewModel.allMedia.collectAsState(initial = emptyList())
    
    // Only proceed if we have the list
    if (mediaList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val initialIndex = remember(mediaId) { mediaList.indexOfFirst { it.id == mediaId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaList.size }
    val currentMedia = mediaList.getOrNull(pagerState.currentPage)

    // Swipe to dismiss state
    var offsetY by remember { mutableStateOf(0f) }
    val dismissThreshold = 300f
    val alpha = 1f - (offsetY / 800f).coerceIn(0f, 1f)
    
    // Zoom state (to lock pager)
    var isZoomed by remember { mutableStateOf(false) }

    // Metadata sheet state
    var showInfoSheet by remember { mutableStateOf(false) }

    @OptIn(ExperimentalMaterial3Api::class)
    val sheetState = rememberModalBottomSheetState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveFolderUri by viewModel.saveFolderUri.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Delete state
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Helper to handle back/dismiss logic
    val handleBack = {
        onBack()
    }

    Scaffold(
        topBar = {
            // Fade top bar out when dragging
            if (offsetY < 100f) {
                var showMenu by remember { mutableStateOf(false) }
                
                TopAppBar(
                    title = { Text(currentMedia?.workflowName ?: "Detail") },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Share Action
                        IconButton(onClick = {
                            currentMedia?.let { item ->
                                val url = "http://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=output"
                                scope.launch {
                                    com.example.comfyui_remote.utils.ShareUtils.downloadAndShare(context, url)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }

                        // Delete Action
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }

                        // Info Action
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info")
                        }

                        // Overflow Menu
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Set as Wallpaper") },
                                onClick = {
                                    showMenu = false
                                    currentMedia?.let { item ->
                                        val url = "http://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=output"
                                        scope.launch {
                                            com.example.comfyui_remote.utils.WallpaperUtils.setWallpaper(context, url)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to Device") },
                                onClick = {
                                    showMenu = false
                                    if (saveFolderUri == null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Please select a save folder in Settings")
                                        }
                                    } else {
                                        currentMedia?.let { item ->
                                            val url = "http://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=output"
                                            scope.launch {
                                                val success = com.example.comfyui_remote.utils.StorageUtils.saveMediaToFolder(
                                                    context = context,
                                                    url = url,
                                                    folderUri = saveFolderUri!!,
                                                    fileName = item.fileName,
                                                    mediaType = item.mediaType
                                                )
                                                if (success) {
                                                    snackbarHostState.showSnackbar("Saved to device")
                                                } else {
                                                    snackbarHostState.showSnackbar("Failed to save")
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.6f * alpha),
                        titleContentColor = Color.White.copy(alpha = alpha),
                        navigationIconContentColor = Color.White.copy(alpha = alpha),
                        actionIconContentColor = Color.White.copy(alpha = alpha)
                    )
                )
            }
        },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Black.copy(alpha = alpha) // Fade background
        ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed // Disable paging when zoomed
            ) { page ->
                val item = mediaList[page]
                val url = "http://${item.serverHost}:${item.serverPort}/view?filename=${item.fileName}${if (item.subfolder != null) "&subfolder=${item.subfolder}" else ""}&type=output"
                
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (item.mediaType == "VIDEO") {
                        VideoPlayer(url = url)
                    } else {
                        ZoomableImage(
                            url = url,
                            onZoomChanged = { zoomed -> isZoomed = zoomed },
                            onDismissDrag = { dragAmount ->
                                offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                            },
                            onDismissEnd = {
                                if (offsetY > dismissThreshold) {
                                    handleBack()
                                } else {
                                    offsetY = 0f // Reset
                                }
                            },
                            imageModifier = Modifier
                                    .let { modifier ->
                                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                            with(sharedTransitionScope) {
                                                modifier.sharedElement(
                                                    state = rememberSharedContentState(key = "image-${item.id}"),
                                                    animatedVisibilityScope = animatedVisibilityScope
                                                )
                                            }
                                        } else modifier
                                    }
                        )
                    }
                }
            }
            
            if (showInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showInfoSheet = false },
                    sheetState = sheetState
                ) {
                    currentMedia?.let { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .padding(bottom = 32.dp) // Bottom padding for navigation bar
                        ) {
                            Text(
                                "Details",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            DetailRow("File Name", item.fileName)
                            DetailRow("Workflow", item.workflowName)
                            DetailRow("Type", item.mediaType)
                            DetailRow("Server", "${item.serverHost}:${item.serverPort}")
                            DetailRow("Date", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(item.timestamp)))
                            
                            if (item.subfolder != null) {
                                DetailRow("Subfolder", item.subfolder)
                            }
                        }
                    }
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Media?") },
                    text = { Text("Are you sure you want to delete this file? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                currentMedia?.let { item ->
                                    viewModel.deleteMedia(listOf(item))
                                    handleBack()
                                }
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun VideoPlayer(url: String) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ZoomableImage(
    url: String,
    onZoomChanged: (Boolean) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissEnd: () -> Unit,
    imageModifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { centroid ->
                        scope.launch {
                            if (scale.value > 1.1f) {
                                launch { scale.animateTo(1f) }
                                launch { offsetX.animateTo(0f) }
                                launch { offsetY.animateTo(0f) }
                                onZoomChanged(false)
                            } else {
                                launch { scale.animateTo(3f) }
                                onZoomChanged(true)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - zoom) * centroidSize
                                val panMotion = pan.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                var consumed = false
                                if (zoomChange != 1f) {
                                    val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
                                    scope.launch { scale.snapTo(newScale) }
                                    onZoomChanged(newScale > 1.1f)
                                    consumed = true
                                }

                                if (panChange != Offset.Zero) {
                                    scope.launch {
                                        if (scale.value > 1.1f) {
                                            val extraWidth = (scale.value - 1) * containerSize.width
                                            val extraHeight = (scale.value - 1) * containerSize.height
                                            val maxX = extraWidth / 2
                                            val maxY = extraHeight / 2
                                            
                                            offsetX.snapTo((offsetX.value + panChange.x).coerceIn(-maxX, maxX))
                                            offsetY.snapTo((offsetY.value + panChange.y).coerceIn(-maxY, maxY))
                                        } else {
                                            // 1x scale: only handle vertical for dismissal
                                            if (abs(panChange.y) > abs(panChange.x)) {
                                                if (panChange.y > 0 || offsetY.value > 0) {
                                                    onDismissDrag(panChange.y)
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Consume if zoomed OR if unzoomed and primarily vertical
                                    if (scale.value > 1.1f || abs(panChange.y) > abs(panChange.x)) {
                                        consumed = true
                                    }
                                }
                                
                                if (consumed) {
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                    
                    if (scale.value <= 1.1f) {
                        onDismissEnd()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
            AsyncImage(
                model = url,
                contentDescription = "Full Screen Content",
                modifier = imageModifier
                    .fillMaxSize()
                    .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offsetX.value,
                    translationY = offsetY.value
                ),
            contentScale = ContentScale.Fit
        )
    }
}
