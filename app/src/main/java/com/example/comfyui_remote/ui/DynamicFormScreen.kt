package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.comfyui_remote.MainViewModel
import com.example.comfyui_remote.data.WorkflowEntity
import com.example.comfyui_remote.domain.InputField
import com.example.comfyui_remote.network.ExecutionStatus
import kotlin.random.Random

@Composable
fun DynamicFormScreen(
    viewModel: MainViewModel,
    workflow: WorkflowEntity
) {
    // We need to parse inputs once
    var inputs by remember { mutableStateOf<List<InputField>>(emptyList()) }
    // Initialize parsed inputs
    LaunchedEffect(workflow) {
        inputs = viewModel.parseWorkflowInputs(workflow.jsonContent)
    }

    val executionStatus by viewModel.executionStatus.collectAsState()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = workflow.name,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            inputs.forEachIndexed { index, inputField ->
                when (inputField) {
                    is InputField.StringInput -> {
                        OutlinedTextField(
                            value = inputField.value,
                            onValueChange = { newValue ->
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = newValue)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (${inputField.fieldName})") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is InputField.IntInput -> {
                         OutlinedTextField(
                            value = inputField.value.toString(),
                            onValueChange = { newValue ->
                                val intVal = newValue.toIntOrNull() ?: 0
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = intVal)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (${inputField.fieldName})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is InputField.SeedInput -> {
                         OutlinedTextField(
                            value = inputField.value.toString(),
                            onValueChange = { newValue ->
                                val longVal = newValue.toLongOrNull() ?: 0L
                                inputs = inputs.toMutableList().also {
                                    it[index] = inputField.copy(value = longVal)
                                }
                            },
                            label = { Text("${inputField.nodeTitle} (Seed)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val newSeed = Random.nextLong(1, Long.MAX_VALUE)
                                    inputs = inputs.toMutableList().also {
                                        it[index] = inputField.copy(value = newSeed)
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Randomize Seed")
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.executeWorkflow(workflow, inputs)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.FINISHED
            ) {
                if (executionStatus == ExecutionStatus.EXECUTING || executionStatus == ExecutionStatus.QUEUED) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp).padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("Running...")
                } else {
                    Text("Generate")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val image by viewModel.generatedImage.collectAsState()
            if (image != null) {
                Text("Result:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                coil.compose.AsyncImage(
                    model = image,
                    contentDescription = "Generated Image",
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )
            }
        }
    }
}
