package com.example.comfyui_remote.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val saveFolderUri by viewModel.saveFolderUri.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            
            viewModel.saveSaveFolderUri(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val themeMode by viewModel.themeMode.collectAsState()
                val themeOptions = listOf("System", "Light", "Dark")
                
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Save Folder",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Text(
                    text = saveFolderUri ?: "Not selected (Defaults to Downloads)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { launcher.launch(null) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Change Folder")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permission Revocation Warning / Reset
        val hasPermission = try {
            if (saveFolderUri != null) {
                val uri = Uri.parse(saveFolderUri)
                // Check if we still have access (simple heuristic: valid URI and content resolver doesn't crash)
                // In reality, takePersistableUriPermission handles it, but revoked permission might not throw until access.
                // We'll just provide the Reset button always if set.
                true
            } else false
        } catch (e: Exception) { false }

        if (saveFolderUri != null) {
             TextButton(
                 onClick = { viewModel.saveSaveFolderUri("") }, // Empty string or null? ViewModel logic needed.
                 modifier = Modifier.align(Alignment.CenterHorizontally)
             ) {
                 Text("Reset Folder Permission", color = MaterialTheme.colorScheme.error)
             }
        }
    }
}
