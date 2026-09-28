package com.example.comfyui_remote.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GeneratedMediaListing
import com.example.comfyui_remote.domain.MediaInfo
import com.example.comfyui_remote.ui.components.ConfirmDialog
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.SectionHeader
import com.example.comfyui_remote.utils.ShareUtils
import com.example.comfyui_remote.utils.StorageUtils
import com.example.comfyui_remote.utils.WallpaperUtils
import kotlinx.coroutines.launch
import kotlin.math.*

// Reusable date formatter to avoid instantiation on every recomposition
private val DATE_TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
    .withZone(java.time.ZoneId.systemDefault())

// Media viewers may use black and white (UI spec §9)
private val ViewerScrim = Color.Black.copy(alpha = 0.5f)
private val OnViewer = Color.White

/**
 * Full-screen media viewer (Phase 104): black and edge to edge with light system-bar icons; one tap shows or
 * hides the bars. The pager follows the gallery's filtered order when the item is in it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MediaDetailScreen(
    viewModel: MainViewModel,
    mediaId: Long,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    LightSystemBarIcons()

    val gallery by viewModel.galleryMedia.collectAsState()
    val all by viewModel.allMedia.collectAsState(initial = null)
    val allMedia = all
    if (allMedia == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    // Opened from the form, the item may be outside the gallery's filter: then page through everything
    val useGallery = remember(mediaId) { gallery.any { it.id == mediaId } }
    val mediaList = if (useGallery) gallery else allMedia
    if (mediaList.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val initialIndex = remember(mediaId) { mediaList.indexOfFirst { it.id == mediaId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaList.size }
    val currentMedia = mediaList.getOrNull(pagerState.currentPage)

    // Swipe down to dismiss
    var offsetY by remember { mutableStateOf(0f) }
    val dismissThreshold = 300f
    val alpha = 1f - (offsetY / 800f).coerceIn(0f, 1f)
    var isZoomed by remember { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }

    var showInfo by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSecure by viewModel.isSecure.collectAsState()
    val currentHost by viewModel.host.collectAsState()
    val currentPort by viewModel.port.collectAsState()
    val saveFolderUri by viewModel.saveFolderUri.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    fun urlOf(item: GeneratedMediaListing) = item.constructUrl(currentHost, currentPort, isSecure)

    // How the current item was made; null while loading, empty for uploads
    val info by produceState<MediaInfo?>(null, currentMedia?.id) {
        value = currentMedia?.let { viewModel.mediaInfo(it.id) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = offsetY },
            userScrollEnabled = !isZoomed
        ) { page ->
            val item = mediaList[page]
            val url = urlOf(item)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (item.mediaType == "VIDEO") {
                    // The player's own controls take taps; keep the bars' padding clear of them
                    Box(Modifier.fillMaxSize().systemBarsPadding()) { VideoPlayer(url = url) }
                } else {
                    ZoomableImage(
                        url = url,
                        contentDescription = "Image from ${item.workflowName}",
                        onTap = { chromeVisible = !chromeVisible },
                        onZoomChanged = { zoomed -> isZoomed = zoomed },
                        onDismissDrag = { dragAmount -> offsetY = (offsetY + dragAmount).coerceAtLeast(0f) },
                        onDismissEnd = { if (offsetY > dismissThreshold) onBack() else offsetY = 0f },
                        imageModifier = Modifier.let { modifier ->
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

        // Top bar: Back, title, and "Set as wallpaper" for images
        AnimatedVisibility(
            visible = chromeVisible && offsetY < 100f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            var menu by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text(currentMedia?.workflowName ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (currentMedia?.mediaType == "IMAGE") {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Set as wallpaper") }, onClick = {
                                    menu = false
                                    currentMedia.let { item -> scope.launch { WallpaperUtils.setWallpaper(context, urlOf(item)) } }
                                })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ViewerScrim,
                    titleContentColor = OnViewer,
                    navigationIconContentColor = OnViewer,
                    actionIconContentColor = OnViewer
                )
            )
        }

        // Bottom actions, labelled
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            AnimatedVisibility(visible = chromeVisible && offsetY < 100f, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ViewerScrim)
                        .navigationBarsPadding()
                        .padding(vertical = Dimens.xs)
                ) {
                    ViewerAction(Icons.Filled.Share, "Share") {
                        currentMedia?.let { item ->
                            scope.launch {
                                if (!ShareUtils.downloadAndShare(context, urlOf(item), item.fileName)) {
                                    snackbar.showSnackbar("Couldn't download it to share")
                                }
                            }
                        }
                    }
                    ViewerAction(Icons.Filled.Download, "Save") {
                        val folder = saveFolderUri
                        val item = currentMedia
                        if (folder == null) {
                            scope.launch { snackbar.showSnackbar("Choose a save folder in Settings first") }
                        } else if (item != null) {
                            scope.launch {
                                val ok = StorageUtils.saveMediaToFolder(context, urlOf(item), folder, item.fileName, item.mediaType)
                                snackbar.showSnackbar(if (ok) "Saved to device" else "Couldn't save")
                            }
                        }
                    }
                    ViewerAction(Icons.Filled.Edit, "Open in form", enabled = info?.isEmpty == false) {
                        currentMedia?.let { viewModel.loadHistory(it) }
                    }
                    ViewerAction(Icons.Filled.Info, "Info") { showInfo = true }
                    ViewerAction(Icons.Filled.Delete, "Remove") { confirmRemove = true }
                }
            }
        }
    }

    if (showInfo && currentMedia != null) {
        MediaInfoSheet(item = currentMedia, info = info, onDismiss = { showInfo = false })
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove from gallery?",
            text = "It's removed from this app's gallery. The file stays on the server.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmRemove = false
                currentMedia?.let { viewModel.removeFromGallery(listOf(it.id)) }
                onBack()
            },
            onDismiss = { confirmRemove = false }
        )
    }
}

/** Light status and navigation bar icons while the black viewer is shown; the theme's setting comes back after. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

/** An icon with its label under it, at least 48 dp, for the viewer's action row. */
@Composable
private fun ViewerAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) OnViewer else OnViewer.copy(alpha = 0.38f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = Dimens.minTouch)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.xs, vertical = Dimens.xs)
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** How the item was made (prompt, settings) and its file details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaInfoSheet(item: GeneratedMediaListing, info: MediaInfo?, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
                .navigationBarsPadding()
                .padding(bottom = Dimens.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.s)
        ) {
            when {
                info == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                info.isEmpty -> Text(
                    "No workflow data for this item (for example, an uploaded image).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    info.prompt?.let { prompt ->
                        CopyableText("Prompt", prompt) { clipboard.setText(AnnotatedString(prompt)) }
                    }
                    info.negative?.let { negative ->
                        CopyableText("Negative prompt", negative) { clipboard.setText(AnnotatedString(negative)) }
                    }
                    if (info.settings.isNotEmpty()) {
                        SectionHeader("Settings")
                        info.settings.forEach { (label, value) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp))
                                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (label == "Seed") {
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy seed")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SectionHeader("File")
            DetailRow("Workflow", item.workflowName)
            DetailRow("File name", item.fileName)
            DetailRow("Date", DATE_TIME_FORMATTER.format(java.time.Instant.ofEpochMilli(item.timestamp)))
            DetailRow("Type", if (item.mediaType == "VIDEO") "Video" else "Image")
            DetailRow("Server", "${item.serverHost}:${item.serverPort}")
            item.subfolder?.takeIf { it.isNotEmpty() }?.let { DetailRow("Subfolder", it) }
        }
    }
}

/** A labelled block of text that shows 6 lines until expanded, with Copy. */
@Composable
private fun CopyableText(label: String, text: String, onCopy: () -> Unit) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy ${label.lowercase()}") }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 6,
        overflow = TextOverflow.Ellipsis
    )
    if (!expanded && text.lines().size + text.length / 45 > 6) {
        TextButton(onClick = { expanded = true }) { Text("Show more") }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = Dimens.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun VideoPlayer(url: String) {
    val context = LocalContext.current
    
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.player = exoPlayer
        }
    )
}

@Composable
fun ZoomableImage(
    url: String,
    contentDescription: String? = null,
    onTap: () -> Unit = {},
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
                    onTap = { onTap() },
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
                contentDescription = contentDescription,
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
