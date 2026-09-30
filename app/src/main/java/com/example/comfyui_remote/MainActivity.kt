package com.example.comfyui_remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.comfyui_remote.data.AppDatabase
import com.example.comfyui_remote.data.WorkflowRepository
import com.example.comfyui_remote.ui.ConnectionScreen
import com.example.comfyui_remote.ui.WorkflowListScreen
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/** A bottom-bar tab (Phase 102): its root route and the pushed routes that belong to it. */
private data class Tab(val route: String, val label: String, val icon: ImageVector, val children: Set<String> = emptySet()) {
    fun isSelected(currentRoute: String?) = currentRoute == route || currentRoute in children
}

private val TABS = listOf(
    Tab("workflows", "Workflows", Icons.Filled.AccountTree, setOf("templates")),
    Tab("queue", "Queue", Icons.Filled.PendingActions),
    Tab("gallery", "Gallery", Icons.Filled.PhotoLibrary),
    Tab("settings", "Settings", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phase 100: draw behind the system bars; the outer Scaffold pads them once (UI spec §10)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        val repository = WorkflowRepository(database.workflowDao())
        val mediaRepository = com.example.comfyui_remote.data.MediaRepository(database.generatedMediaDao())
        val localQueueRepository = com.example.comfyui_remote.data.LocalQueueRepository(database.localQueueDao())
        val userPreferencesRepository = com.example.comfyui_remote.data.UserPreferencesRepository(this)
        val savedGalleryListRepository = com.example.comfyui_remote.data.SavedGalleryListRepository(this)
        val app = application as ComfyApplication
        val viewModelFactory = MainViewModelFactory(
            app,
            repository,
            mediaRepository,
            userPreferencesRepository,
            app.connectionRepository,
            localQueueRepository,
            savedGalleryListRepository
        )
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        // WorkflowExecutionService is stateless; the queue gets its own instance
        val workflowExecutor = com.example.comfyui_remote.domain.WorkflowExecutor()
        val imageRepository = com.example.comfyui_remote.data.ImageRepository()
        val workflowExecutionService = com.example.comfyui_remote.domain.WorkflowExecutionService(imageRepository, workflowExecutor)

        val queueViewModelFactory = com.example.comfyui_remote.QueueViewModelFactory(
            app,
            localQueueRepository,
            app.connectionRepository,
            userPreferencesRepository,
            workflowExecutionService
        )
        val queueViewModel = ViewModelProvider(this, queueViewModelFactory)[com.example.comfyui_remote.QueueViewModel::class.java]

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            ComfyUI_front_endTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val openConnection = { navController.navigate("connection") { launchSingleTop = true } }

                // First run: no saved server, so start on Connection (Phase 102 D-02)
                val hasSavedServer by viewModel.hasSavedServer.collectAsState()
                LaunchedEffect(hasSavedServer) {
                    if (hasSavedServer == false && navController.currentDestination?.route != "connection") {
                        navController.navigate("connection") {
                            popUpTo("workflows") { inclusive = true }
                        }
                    }
                }

                // Tabs are hidden on the form, the full-screen media viewer (Phase 104) and Connection.
                // Landscape puts them in a rail, portrait in the bottom bar (Phase 105 D-01).
                val tabsHidden = currentRoute == "remote_control" || currentRoute == "connection" ||
                    currentRoute?.startsWith("media_detail") == true
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val showRail = landscape && !tabsHidden

                Scaffold(
                    bottomBar = {
                        if (!landscape && !tabsHidden) {
                            NavigationBar {
                                TABS.forEach { tab ->
                                    NavigationBarItem(
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(tab.label) },
                                        selected = tab.isSelected(currentRoute),
                                        onClick = { navController.navigateToTab(tab.route) }
                                    )
                                }
                            }
                        }
                    }
                ) { scaffoldPadding ->
                    // The rail takes the start inset, so the screens beside it don't pad it again
                    val innerPadding = if (showRail) {
                        val direction = LocalLayoutDirection.current
                        PaddingValues(
                            start = 0.dp,
                            top = scaffoldPadding.calculateTopPadding(),
                            end = scaffoldPadding.calculateEndPadding(direction),
                            bottom = scaffoldPadding.calculateBottomPadding()
                        )
                    } else {
                        scaffoldPadding
                    }
                    // Listen for auto-navigation to form (e.g. "Open in form" in the media viewer)
                    val navigateToForm by viewModel.navigateToForm.collectAsState()
                    LaunchedEffect(navigateToForm) {
                        if (navigateToForm) {
                            navController.navigate("remote_control") { launchSingleTop = true }
                            viewModel.onNavigatedToForm()
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                    if (showRail) {
                        NavigationRail {
                            Spacer(Modifier.weight(1f))
                            TABS.forEach { tab ->
                                NavigationRailItem(
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    selected = tab.isSelected(currentRoute),
                                    onClick = { navController.navigateToTab(tab.route) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(
                                if (showRail) Modifier.consumeWindowInsets(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                                ) else Modifier
                            )
                    ) {
                    @OptIn(ExperimentalSharedTransitionApi::class)
                    SharedTransitionLayout {
                        NavHost(
                            navController = navController,
                            startDestination = "workflows"
                        ) {
                            composable("connection") {
                                PaddedScreen(innerPadding) {
                                    val canGoBack = navController.previousBackStackEntry != null
                                    ConnectionScreen(
                                        viewModel = viewModel,
                                        onBack = if (canGoBack) ({ navController.popBackStack() }) else null,
                                        onConnected = {
                                            if (navController.previousBackStackEntry != null) {
                                                navController.popBackStack()
                                            } else {
                                                navController.navigate("workflows") {
                                                    popUpTo("connection") { inclusive = true }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            composable("workflows") {
                                PaddedScreen(innerPadding) {
                                    WorkflowListScreen(
                                        viewModel = viewModel,
                                        onOpenTemplates = { navController.navigate("templates") },
                                        onOpenConnection = openConnection
                                    ) { workflow ->
                                        viewModel.parseWorkflowInputs(workflow.jsonContent)
                                        viewModel.selectWorkflow(workflow)
                                        navController.navigate("remote_control")
                                    }
                                }
                            }
                            composable("templates") {
                                PaddedScreen(innerPadding) {
                                    com.example.comfyui_remote.ui.TemplatesScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() },
                                        onOpenWorkflow = { workflow ->
                                            viewModel.parseWorkflowInputs(workflow.jsonContent)
                                            viewModel.selectWorkflow(workflow)
                                            navController.navigate("remote_control")
                                        }
                                    )
                                }
                            }
                            composable("queue") {
                                PaddedScreen(innerPadding) {
                                    com.example.comfyui_remote.ui.QueueScreen(
                                        viewModel = queueViewModel,
                                        mainViewModel = viewModel,
                                        downloads = app.modelDownloadRepository,
                                        serverQueue = app.serverQueueRepository,
                                        onOpenConnection = openConnection
                                    )
                                }
                            }
                            composable("gallery") {
                                PaddedScreen(innerPadding) {
                                    com.example.comfyui_remote.ui.GalleryScreen(
                                        viewModel = viewModel,
                                        onMediaClick = { media ->
                                            navController.navigate("media_detail/${media.id}")
                                        },
                                        onOpenConnection = openConnection,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@composable
                                    )
                                }
                            }
                            composable(
                                route = "media_detail/{mediaId}",
                                arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
                                com.example.comfyui_remote.ui.MediaDetailScreen(
                                    viewModel = viewModel,
                                    mediaId = mediaId,
                                    onBack = { navController.popBackStack() },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable
                                )
                            }
                            composable("settings") {
                                PaddedScreen(innerPadding) {
                                    com.example.comfyui_remote.ui.SettingsScreen(viewModel, onOpenConnection = openConnection)
                                }
                            }
                            composable("remote_control") {
                                PaddedScreen(innerPadding) {
                                    val workflow by viewModel.selectedWorkflow.collectAsState()
                                    if (workflow != null) {
                                        com.example.comfyui_remote.ui.DynamicFormScreen(
                                            viewModel,
                                            workflow!!,
                                            onBack = { navController.popBackStack() },
                                            onViewInGallery = { mediaId ->
                                                navController.navigate("media_detail/$mediaId")
                                            },
                                            onOpenConnection = openConnection
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Switches tabs keeping each tab's state (Phase 102 D-01): the stack is at most Workflows plus one other tab,
 * so Back from a tab root goes to Workflows, and Back from Workflows leaves the app.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The outer Scaffold's padding (tab bar, system bars) for one destination; the full-screen media viewer
 * goes without it (Phase 104). Inner Scaffolds and top bars must not add the system bars again.
 */
@Composable
private fun PaddedScreen(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
}
