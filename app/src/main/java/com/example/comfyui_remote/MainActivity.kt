package com.example.comfyui_remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComfyUI_front_endTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: MainViewModel) {
    val serverAddress by viewModel.serverAddress.collectAsState()
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
            value = serverAddress,
            onValueChange = { viewModel.updateServerAddress(it) },
            label = { Text("Server Address (IP:Port)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                  if (connectionState == WebSocketState.CONNECTED) {
                      viewModel.disconnect()
                  } else {
                      viewModel.connect()
                  }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (connectionState == WebSocketState.CONNECTED) "Disconnect" else "Connect")
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
