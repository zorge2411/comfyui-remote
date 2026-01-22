package com.example.comfyui_remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.comfyui_remote.data.AppDatabase
import com.example.comfyui_remote.data.WorkflowRepository
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.WorkflowListScreen
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = WorkflowRepository(database.workflowDao())
        val userPreferencesRepository = com.example.comfyui_remote.data.UserPreferencesRepository(this)
        val viewModelFactory = MainViewModelFactory(repository, userPreferencesRepository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        setContent {
            ComfyUI_front_endTheme {
                val navController = rememberNavController()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "connection") {
                        composable("connection") {
                            ConnectionScreen(viewModel) {
                                // On successful connection trigger (user interaction)
                                navController.navigate("workflows")
                            }
                        }
                        composable("workflows") {
                            WorkflowListScreen(viewModel) { workflow ->
                                viewModel.parseWorkflowInputs(workflow.jsonContent) // Pre-parse if needed
                                // We need to pass the workflow ID or object. For simple navigation:
                                // Ideally use a shared ViewModel or proper NavType. 
                                // Since we have shared VM, we can set a "currentWorkflow" or just pass ID.
                                // Let's simplify: Set current in VM and navigate.
                                viewModel.selectWorkflow(workflow)
                                navController.navigate("remote_control")
                            }
                        }
                        composable("remote_control") {
                            // retrieve selected
                            val workflow = viewModel.selectedWorkflow.value
                            if (workflow != null) {
                                com.example.comfyui_remote.ui.DynamicFormScreen(viewModel, workflow)
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
    val connectionState by viewModel.connectionState.collectAsState()

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

        OutlinedTextField(
            value = host,
            onValueChange = { viewModel.updateHost(it) },
            label = { Text("Host IP (e.g. 192.168.1.10)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = port,
            onValueChange = { viewModel.updatePort(it) },
            label = { Text("Port (Default: 8188)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                  if (connectionState == WebSocketState.CONNECTED) {
                      viewModel.disconnect()
                  } else {
                      viewModel.saveConnection() // Persist
                      viewModel.connect()
                  }
            },
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
