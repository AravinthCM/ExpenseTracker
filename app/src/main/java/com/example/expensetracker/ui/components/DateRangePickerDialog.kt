package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    var daysBackStart by remember { mutableStateOf("7") }
    var daysBackEnd by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Date Range") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Specify range in days relative to today:")
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = daysBackStart,
                    onValueChange = { daysBackStart = it },
                    label = { Text("From (Days ago)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = daysBackEnd,
                    onValueChange = { daysBackEnd = it },
                    label = { Text("To (Days ago, e.g. 0 = Today)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startDays = daysBackStart.toLongOrNull() ?: 7L
                    val endDays = daysBackEnd.toLongOrNull() ?: 0L

                    val now = System.currentTimeMillis()
                    val dayMs = 24 * 60 * 60 * 1000L

                    val startMillis = now - (startDays * dayMs)
                    val endMillis = now - (endDays * dayMs) + (24 * 60 * 60 * 1000L - 1)

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
}
