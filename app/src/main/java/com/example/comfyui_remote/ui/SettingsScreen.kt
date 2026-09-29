package com.example.comfyui_remote.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.network.WebSocketState
import com.example.comfyui_remote.ui.components.AppCard
import com.example.comfyui_remote.ui.components.AppTopBar
import com.example.comfyui_remote.ui.components.ConnectionStatus
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.SectionHeader

/** The Settings tab (Phase 102): server, appearance, gallery sync and storage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onOpenConnection: () -> Unit) {
    val context = LocalContext.current
    val saveFolderUri by viewModel.saveFolderUri.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val serverAddress by viewModel.serverAddress.collectAsState()
    val hasSavedServer by viewModel.hasSavedServer.collectAsState()
    val autoConnect by viewModel.autoConnect.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val maxItems by viewModel.maxSyncItems.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.saveSaveFolderUri(it.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Settings")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.s)
        ) {
            SectionHeader("Server")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
                    Text(
                        text = if (hasSavedServer == true) serverAddress else "No server set",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    ConnectionStatus(connectionState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (connectionState) {
                            WebSocketState.CONNECTED, WebSocketState.CONNECTING, WebSocketState.RECONNECTING ->
                                OutlinedButton(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
                            else -> if (hasSavedServer == true) {
                                Button(onClick = { viewModel.connect() }) { Text("Connect") }
                            }
                        }
                        TextButton(onClick = onOpenConnection) { Text("Change server…") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Connect automatically on start",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = autoConnect, onCheckedChange = { viewModel.setAutoConnect(it) })
                    }
                }
            }

            SectionHeader("Appearance")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                val themeOptions = listOf("System", "Light", "Dark")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                            onClick = { viewModel.updateThemeMode(index) },
                            selected = themeMode == index
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            SectionHeader("Gallery sync")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Items to load from the server: $maxItems",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = maxItems.toFloat(),
                        onValueChange = { viewModel.updateMaxSyncItems(it.toInt()) },
                        valueRange = 100f..2000f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Higher limits load more history but take longer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader("Storage")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Save folder", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = saveFolderUri?.takeIf { it.isNotEmpty() } ?: "Not selected (defaults to Downloads)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!saveFolderUri.isNullOrEmpty()) {
                            TextButton(onClick = { viewModel.saveSaveFolderUri("") }) {
                                Text("Reset", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        OutlinedButton(onClick = { launcher.launch(null) }) {
                            Text("Change folder")
                        }
                    }
                }
            }
        }
    }
}
