package com.example.comfyui_remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.comfyui_remote.data.AppDatabase
import com.example.comfyui_remote.data.WorkflowRepository
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.WorkflowListScreen
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = WorkflowRepository(database.workflowDao())
        val mediaRepository = com.example.comfyui_remote.data.MediaRepository(database.generatedMediaDao())
        val userPreferencesRepository = com.example.comfyui_remote.data.UserPreferencesRepository(this)
        val app = application as ComfyApplication
        val viewModelFactory = MainViewModelFactory(
            app,
            repository, 
            mediaRepository, 
            userPreferencesRepository,
            app.connectionRepository
        )
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            ComfyUI_front_endTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                Scaffold(
                    bottomBar = {
                        if (currentRoute != "remote_control") { // Hide on dynamic form
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                    label = { Text("Home") },
                                    selected = currentRoute == "connection",
                                    onClick = {
                                        // Simple navigation back to start
                                        navController.navigate("connection") {
                                            popUpTo("connection") {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.List, contentDescription = "Workflows") },
                                    label = { Text("Workflows") },
                                    selected = currentRoute == "workflows",
                                    onClick = {
                                        navController.navigate("workflows") {
                                            popUpTo("connection") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Search, contentDescription = "Gallery") },
                                    label = { Text("Gallery") },
                                    selected = currentRoute == "gallery",
                                    onClick = {
                                        navController.navigate("gallery") {
                                            popUpTo("connection") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )

                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Info, contentDescription = "History") },
                                    label = { Text("History") },
                                    selected = currentRoute == "history",
                                    onClick = {
                                        navController.navigate("history") {
                                            popUpTo("connection") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    selected = currentRoute == "settings",
                                    onClick = {
                                        navController.navigate("settings") {
                                            popUpTo("connection") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    // Listen for auto-navigation to form (e.g. from history load)
                    val navigateToForm by viewModel.navigateToForm.collectAsState()
                    LaunchedEffect(navigateToForm) {
                        if (navigateToForm) {
                            navController.navigate("remote_control") {
                                // popUpTo("history") // Optional: Back goes back to history
                            }
                            viewModel.onNavigatedToForm()
                        }
                    }

                    @OptIn(ExperimentalSharedTransitionApi::class)
                    SharedTransitionLayout {
                        NavHost(
                            navController = navController, 
                            startDestination = "connection",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                        composable("connection") {
                            ConnectionScreen(viewModel) {
                                navController.navigate("workflows")
                            }
                        }
                        composable("workflows") {
                            WorkflowListScreen(viewModel) { workflow ->
                                viewModel.parseWorkflowInputs(workflow.jsonContent)
                                viewModel.selectWorkflow(workflow)
                                navController.navigate("remote_control")
                            }
                        }
                        composable("history") {
                            com.example.comfyui_remote.ui.HistoryScreen(viewModel)
                        }
                        composable("gallery") {
                            com.example.comfyui_remote.ui.GalleryScreen(
                                viewModel = viewModel,
                                onMediaClick = { media ->
                                    navController.navigate("media_detail/${media.id}")
                                },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable
                            )
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
                            com.example.comfyui_remote.ui.SettingsScreen(viewModel)
                        }
                        composable("remote_control") {
                            val workflow by viewModel.selectedWorkflow.collectAsState()
                            if (workflow != null) {
                                com.example.comfyui_remote.ui.DynamicFormScreen(
                                    viewModel,
                                    workflow!!,
                                    onBack = { navController.popBackStack() },
                                    onViewInGallery = { mediaId ->
                                        navController.navigate("media_detail/$mediaId")
                                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: MainViewModel, onConnect: () -> Unit) {
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val isSecure by viewModel.isSecure.collectAsState()
    val serverProfiles by viewModel.serverProfiles.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val shouldNavigate by viewModel.shouldNavigateToWorkflows.collectAsState()
    
    // Auto-navigate ONLY when the signal is raised
    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
             onConnect()
             viewModel.onNavigatedToWorkflows() // Consume the event
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }

    val connectAction = {
        keyboardController?.hide()
        
        // Reset errors
        hostError = null
        portError = null

        if (connectionState == WebSocketState.CONNECTED) {
            viewModel.disconnect()
        } else {
            // Validate
            var hasError = false
            if (host.isBlank()) {
                hostError = "IP address is required"
                hasError = true
            }
            
            if (port.isBlank()) {
                portError = "Port is required"
                hasError = true
            } else if (port.toIntOrNull() == null) {
                portError = "Invalid port number"
                hasError = true
            }

            if (!hasError) {
                viewModel.saveConnection() // Persist
                viewModel.connect()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StatusIndicator(connectionState)
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Connect to ComfyUI",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { 
                    viewModel.updateHost(it)
                    hostError = null 
                },
                label = { Text("Host IP (e.g. 192.168.1.10)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true,
                isError = hostError != null,
                supportingText = if (hostError != null) { { Text(hostError!!) } } else null,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { connectAction() })
            )

            if (serverProfiles.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    serverProfiles.forEach { profile ->
                        val label = if (profile.port != 8188 || profile.isSecure) {
                             "${profile.host}:${profile.port}${if (profile.isSecure) " (secure)" else ""}"
                        } else {
                            profile.host
                        }
                        
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = label, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { 
                                            viewModel.deleteServerProfile(profile)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete, 
                                            contentDescription = "Delete Profile",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectServerProfile(profile)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = port,
            onValueChange = { 
                viewModel.updatePort(it)
                portError = null
            },
            label = { Text("Port (Default: 8188)") },
            isError = portError != null,
            supportingText = portError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { connectAction() }),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = isSecure,
                onCheckedChange = { viewModel.updateIsSecure(it) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = "Use Secure Connection (HTTPS/WSS)")
                Text(
                    text = "Required for remote servers with SSL",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = connectAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (connectionState == WebSocketState.CONNECTED) "Disconnect" else "Connect")
        }
        
        if (connectionState == WebSocketState.CONNECTED) {
             Spacer(modifier = Modifier.height(16.dp))
             Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                 Text("Go to Workflows")
             }
        }
    }
}

@Composable
fun StatusIndicator(state: WebSocketState) {
    val color = when (state) {
        WebSocketState.CONNECTED -> Color.Green
        WebSocketState.CONNECTING -> Color.Yellow
        WebSocketState.ERROR -> Color.Red
        WebSocketState.DISCONNECTED -> Color.Gray
        WebSocketState.RECONNECTING -> Color.Yellow
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = state.name)
    }
}
