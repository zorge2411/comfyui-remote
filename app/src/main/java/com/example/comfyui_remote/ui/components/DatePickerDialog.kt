package com.example.comfyui_remote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startDate: Long?, endDate: Long?) -> Unit,
    initialStartDate: Long? = null,
    initialEndDate: Long? = null
) {
    var startDate by remember { mutableStateOf<LocalDate?>(initialStartDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }) }
    var endDate by remember { mutableStateOf<LocalDate?>(initialEndDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }) }
    
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date Range") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start Date Picker
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Date: ${startDate?.toString() ?: "Not Set"}")
                }
                
                // End Date Picker
                OutlinedButton(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("End Date: ${endDate?.toString() ?: "Not Set"}")
                }
                
                // Clear Button
                TextButton(
                    onClick = {
                        startDate = null
                        endDate = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Date Range")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    val endMillis = endDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    onDateRangeSelected(startMillis, endMillis)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    if (showStartDatePicker) {
        DatePickerDialog(
            onDateSelected = { selectedDate ->
                startDate = selectedDate
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
    
    if (showEndDatePicker) {
        DatePickerDialog(
            onDateSelected = { selectedDate ->
                endDate = selectedDate
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
