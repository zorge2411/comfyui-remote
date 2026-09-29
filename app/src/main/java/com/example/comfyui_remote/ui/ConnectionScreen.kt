package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.ConnectionStatus
import com.example.comfyui_remote.ui.components.Dimens

/**
 * Server address and connect/disconnect (Phase 102: a pushed screen, opened from the connection chip,
 * from Settings, or on first run). [onBack] is null on first run, when there is nothing to go back to.
 * [onConnected] runs once the connection the user asked for is up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: MainViewModel, onBack: (() -> Unit)?, onConnected: () -> Unit) {
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val isSecure by viewModel.isSecure.collectAsState()
    val serverProfiles by viewModel.serverProfiles.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current

    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var connectRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(connectionState, connectRequested) {
        if (connectRequested && connectionState == WebSocketState.CONNECTED) {
            connectRequested = false
            onConnected()
        }
    }

    val connectAction = {
        keyboardController?.hide()
        hostError = null
        portError = null

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
            viewModel.saveConnection()
            connectRequested = true
            viewModel.connect()
        }
    }

    // Phase 100 reference screen: top bar, scrolls (reachable in landscape and above the keyboard)
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Connection", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.s)
        ) {
            ConnectionStatus(connectionState)

            Text(
                text = "Connect to a ComfyUI server on your network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        .menuAnchor(MenuAnchorType.PrimaryEditable),
                    singleLine = true,
                    isError = hostError != null,
                    supportingText = hostError?.let { { Text(it) } },
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
                                        IconButton(onClick = { viewModel.deleteServerProfile(profile) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete profile",
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

            OutlinedTextField(
                value = port,
                onValueChange = {
                    viewModel.updatePort(it)
                    portError = null
                },
                label = { Text("Port (default: 8188)") },
                isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { connectAction() }),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isSecure,
                    onCheckedChange = { viewModel.updateIsSecure(it) }
                )
                Spacer(modifier = Modifier.width(Dimens.s))
                Column {
                    Text(text = "Use secure connection (HTTPS/WSS)")
                    Text(
                        text = "Required for remote servers with SSL",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.s))

            // One filled button per screen (UI spec §8)
            if (connectionState == WebSocketState.CONNECTED) {
                OutlinedButton(onClick = { viewModel.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            } else {
                Button(onClick = connectAction, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect")
                }
            }
        }
    }
}
