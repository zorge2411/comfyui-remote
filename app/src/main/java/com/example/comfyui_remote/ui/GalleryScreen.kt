package com.example.comfyui_remote.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.data.GeneratedMediaListing
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.ConfirmDialog
import com.example.comfyui_remote.ui.components.ConnectionChip
import com.example.comfyui_remote.ui.components.NotConnectedBanner
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.EmptyState
import com.example.comfyui_remote.ui.components.StatusBanner
import com.example.comfyui_remote.ui.components.StatusKind
import com.example.comfyui_remote.utils.ShareUtils
import com.example.comfyui_remote.utils.StorageUtils
import kotlinx.coroutines.launch
import java.io.File

// Reusable date formatter for accessibility content descriptions
private val ACCESSIBILITY_DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())

/** Room below the grid's last row for the Add FAB (56 dp) and its margin. */
private val FAB_CLEARANCE = 88.dp

/**
 * The gallery (Phase 104): stored media filtered on the phone, with selection (Share, Save, Remove),
 * saved lists and a manual upload FAB.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onMediaClick: (GeneratedMediaListing) -> Unit,
    onOpenConnection: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.example.comfyui_remote.network.WebSocketState.CONNECTED
    val mediaList by viewModel.galleryMedia.collectAsState()
    val allMedia by viewModel.allMedia.collectAsState(initial = emptyList())
    val filter by viewModel.gallerySyncFilter.collectAsState()
    val removedCount by viewModel.removedMediaCount.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isSecure by viewModel.isSecure.collectAsState()
    val currentHost by viewModel.host.collectAsState()
    val currentPort by viewModel.port.collectAsState()
    val saveFolderUri by viewModel.saveFolderUri.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Selected ids survive rotation
    var selected by rememberSaveable { mutableStateOf(longArrayOf()) }
    val selecting = selected.isNotEmpty()
    fun toggle(id: Long) {
        selected = if (id in selected) selected.filter { it != id }.toLongArray() else selected + id
    }
    BackHandler(enabled = selecting) { selected = longArrayOf() }
    val selectedItems = mediaList.filter { it.id in selected }

    var showAddDialog by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showLists by remember { mutableStateOf(false) }
    var savingList by remember { mutableStateOf<GallerySyncFilter?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmReload by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var cameraTmpUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraTmpUri = cameraTmpUriString?.let { Uri.parse(it) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadManualImage(uri, context.contentResolver)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraTmpUri != null) {
            viewModel.uploadManualImage(cameraTmpUri, context.contentResolver)
        } else {
            scope.launch { snackbar.showSnackbar("No photo taken") }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera(context, { cameraTmpUriString = it.toString() }, cameraLauncher)
    }
    fun handleCameraAction() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera(context, { cameraTmpUriString = it.toString() }, cameraLauncher)
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun shareSelected() {
        val items = selectedItems.map { it.constructUrl(currentHost, currentPort, isSecure) to it.fileName }
        scope.launch {
            snackbar.showSnackbar("Preparing ${items.size} items…")
        }
        scope.launch {
            val shared = ShareUtils.downloadAndShareMultiple(context, items)
            if (shared < items.size) snackbar.showSnackbar("Couldn't download ${items.size - shared} of ${items.size}")
        }
    }

    fun saveSelected() {
        val folder = saveFolderUri
        if (folder == null) {
            scope.launch { snackbar.showSnackbar("Choose a save folder in Settings first") }
            return
        }
        val items = selectedItems
        scope.launch {
            val saved = items.count { item ->
                StorageUtils.saveMediaToFolder(
                    context, item.constructUrl(currentHost, currentPort, isSecure), folder, item.fileName, item.mediaType
                )
            }
            snackbar.showSnackbar("Saved $saved of ${items.size}")
        }
        selected = longArrayOf()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                var more by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = longArrayOf() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selected = mediaList.map { it.id }.toLongArray() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = { shareSelected() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        Box {
                            IconButton(onClick = { more = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(text = { Text("Save to device") }, onClick = { more = false; saveSelected() })
                                DropdownMenuItem(text = { Text("Remove from gallery") }, onClick = { more = false; confirmRemove = true })
                            }
                        }
                    }
                )
            } else {
                var more by remember { mutableStateOf(false) }
                // Phase 102: the connection chip counts as one of the three actions; Saved lists moved into More
                AppTopBar("Gallery") {
                    ConnectionChip(connectionState, onClick = onOpenConnection)
                    IconButton(onClick = { showFilter = true }) {
                        BadgedBox(badge = { if (filter.isActive() || filter.isSorted()) Badge() }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                        }
                    }
                    Box {
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("Saved lists") },
                                leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                                onClick = { more = false; showLists = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Reload from server") },
                                enabled = isConnected,
                                onClick = { more = false; confirmReload = true }
                            )
                            if (removedCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Restore removed items ($removedCount)") },
                                    onClick = { more = false; confirmRestore = true }
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Uploading needs the server (Phase 102)
            if (!selecting && isConnected) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add image")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            NotConnectedBanner(
                connectionState,
                onReconnect = { viewModel.connect() },
                onOpenConnection = onOpenConnection,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.s)
            )
            val parts = filterParts(filter)
            if (parts.isNotEmpty()) {
                ActiveFilterRow(filter, onChange = { viewModel.setGalleryFilter(it) })
            }
            syncError?.let { error ->
                StatusBanner(
                    StatusKind.Error,
                    "Couldn't load from the server",
                    message = error,
                    modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.s),
                    actions = {
                        TextButton(onClick = { viewModel.clearSyncError() }) { Text("Dismiss") }
                        TextButton(onClick = { viewModel.syncHistory() }) { Text("Retry") }
                    }
                )
            }

            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = { if (isConnected) viewModel.syncHistory() },
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                when {
                    mediaList.isEmpty() && allMedia.isNotEmpty() -> EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "Nothing matches the filter",
                        message = filterSummary(filter),
                        actionText = "Clear filter",
                        onAction = { viewModel.clearGallerySyncFilter() }
                    )
                    mediaList.isEmpty() -> EmptyState(
                        icon = Icons.Filled.PhotoLibrary,
                        title = "No images yet",
                        message = "Generated images and videos appear here after a run. Pull down to load them from the server."
                    )
                    else -> MediaGrid(
                        mediaList = mediaList,
                        selected = selected,
                        selecting = selecting,
                        isSecure = isSecure,
                        currentHost = currentHost,
                        currentPort = currentPort,
                        onToggle = ::toggle,
                        onOpen = onMediaClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add image") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pick from device", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            handleCameraAction()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Take photo", modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    if (confirmRemove) {
        val n = selected.size
        ConfirmDialog(
            title = if (n == 1) "Remove 1 item?" else "Remove $n items?",
            text = "They're removed from this app's gallery. The files stay on the server.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.removeFromGallery(selected.toList())
                selected = longArrayOf()
                confirmRemove = false
            },
            onDismiss = { confirmRemove = false }
        )
    }
    if (confirmReload) {
        ConfirmDialog(
            title = "Reload from server?",
            text = "The gallery is rebuilt from the server's history. Items the server no longer lists disappear from the app (ComfyUI forgets its history when it restarts; if it's empty, the gallery is kept). Removed items stay removed, and nothing on the server changes.",
            confirmLabel = "Reload",
            onConfirm = {
                viewModel.clearAndRefreshHistory()
                confirmReload = false
            },
            onDismiss = { confirmReload = false }
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore removed items?",
            text = "$removedCount removed items are shown in the gallery again.",
            confirmLabel = "Restore",
            onConfirm = {
                viewModel.restoreRemovedMedia()
                confirmRestore = false
            },
            onDismiss = { confirmRestore = false }
        )
    }

    if (showFilter) {
        GalleryFilterSheet(
            current = filter,
            onApply = {
                viewModel.setGalleryFilter(it)
                showFilter = false
            },
            onSaveAsList = {
                showFilter = false
                savingList = it
            },
            onDismiss = { showFilter = false }
        )
    }
    savingList?.let { toSave ->
        SaveListDialog(
            filter = toSave,
            onDismiss = { savingList = null },
            onSaved = {
                viewModel.setGalleryFilter(toSave)
                savingList = null
            },
            viewModel = viewModel
        )
    }
    if (showLists) {
        SavedListsDrawer(
            viewModel = viewModel,
            onDismiss = { showLists = false },
            onApplyList = { listId ->
                showLists = false
                viewModel.applySavedFilter(listId)
            }
        )
    }
}

/** The active filter as removable chips, plus Clear. */
@Composable
private fun ActiveFilterRow(filter: GallerySyncFilter, onChange: (GallerySyncFilter) -> Unit) {
    val chips = buildList {
        dateRangeLabel(filter.startDate, filter.endDate)?.let { add(it to filter.copy(startDate = null, endDate = null)) }
        filter.workflowNameFilter?.takeIf { it.isNotBlank() }?.let { add("Workflow: $it" to filter.copy(workflowNameFilter = null)) }
        filter.fileNameFilter?.takeIf { it.isNotBlank() }?.let { add("File: $it" to filter.copy(fileNameFilter = null)) }
        filter.mediaType?.let { add((if (it == GallerySyncFilter.MediaType.IMAGE) "Images" else "Videos") to filter.copy(mediaType = null)) }
        when (filter.sortOrder) {
            GallerySyncFilter.SortOrder.OLDEST_FIRST -> add("Oldest first" to filter.copy(sortOrder = GallerySyncFilter.SortOrder.NEWEST_FIRST))
            GallerySyncFilter.SortOrder.NAME_ASC -> add("By name" to filter.copy(sortOrder = GallerySyncFilter.SortOrder.NEWEST_FIRST))
            GallerySyncFilter.SortOrder.NEWEST_FIRST -> {}
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding)
    ) {
        chips.forEach { (label, without) ->
            InputChip(
                selected = true,
                onClick = { onChange(without) },
                label = { Text(label) },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove $label", modifier = Modifier.size(InputChipDefaults.IconSize))
                }
            )
        }
        TextButton(onClick = {
            onChange(GallerySyncFilter.default())
        }) { Text("Clear") }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MediaGrid(
    mediaList: List<GeneratedMediaListing>,
    selected: LongArray,
    selecting: Boolean,
    isSecure: Boolean,
    currentHost: String,
    currentPort: String,
    onToggle: (Long) -> Unit,
    onOpen: (GeneratedMediaListing) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    val gridState = rememberLazyGridState()
    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    // Preload the next two rows of thumbnails while scrolling
    LaunchedEffect(mediaList, currentHost, currentPort, isSecure) {
        var maxPreloadedIndex = -1
        val targetSize = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) } / 3
        val loadingUrls = mutableSetOf<String>()
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex == null) return@collect
                val end = (lastIndex + 6).coerceAtMost(mediaList.size - 1)
                val start = kotlin.math.max(lastIndex + 1, maxPreloadedIndex + 1)
                for (i in start..end) {
                    val url = mediaList[i].constructUrl(currentHost, currentPort, isSecure)
                    if (!loadingUrls.add(url)) continue
                    imageLoader.enqueue(
                        coil.request.ImageRequest.Builder(context)
                            .data(url)
                            .size(targetSize)
                            .precision(coil.size.Precision.EXACT)
                            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                            .listener(
                                onCancel = { loadingUrls.remove(url) },
                                onSuccess = { _, _ -> loadingUrls.remove(url) },
                                onError = { _, _ -> loadingUrls.remove(url) }
                            )
                            .build()
                    )
                }
                if (end >= start) maxPreloadedIndex = end
            }
    }

    LazyVerticalGrid(
        state = gridState,
        // 3 columns on a portrait phone, more in landscape and on tablets
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        contentPadding = PaddingValues(
            start = Dimens.s, end = Dimens.s, top = Dimens.xs, bottom = FAB_CLEARANCE
        )
    ) {
        items(items = mediaList, key = { it.id }, contentType = { "media" }) { item ->
            GalleryItem(
                item = item,
                isSelected = item.id in selected,
                isSecure = isSecure,
                currentHost = currentHost,
                currentPort = currentPort,
                onLongClick = { onToggle(item.id) },
                onClick = { if (selecting) onToggle(item.id) else onOpen(item) },
                isScrolling = isScrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
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
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
    val dateString = remember(item.timestamp) {
        ACCESSIBILITY_DATE_FORMATTER.withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(item.timestamp))
    }
    val kind = if (item.mediaType == "VIDEO") "Video" else "Image"
    val contentDescription = "$kind from ${item.workflowName}, $dateString" + if (isSelected) ", selected" else ""

    val context = LocalContext.current
    val imageRequest = remember(item.serverHost, item.serverPort, item.fileName, item.subfolder, item.serverType, isSecure, currentHost, currentPort) {
        coil.request.ImageRequest.Builder(context)
            .data(item.constructUrl(currentHost, currentPort, isSecure))
            .crossfade(true)
            .size(context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) } / 3)
            .precision(coil.size.Precision.EXACT)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // 50% memory saving for thumbs
            .build()
    }
    val shape = MaterialTheme.shapes.extraSmall

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .let { if (isSelected) it.border(3.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSelected) 3.dp else 0.dp)
                .let { modifier ->
                    // Only while not scrolling, to keep scrolling smooth
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
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.xs)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(Dimens.xs)
                    .size(16.dp)
            )
        }
        if (item.mediaType == "VIDEO") {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                    .padding(Dimens.xs)
            )
        }
    }
}
