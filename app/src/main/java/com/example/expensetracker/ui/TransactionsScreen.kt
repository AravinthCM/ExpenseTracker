package com.example.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.TransactionEditLogEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.ui.components.DateRangePickerDialog
import com.example.expensetracker.ui.components.FilterBar
import com.example.expensetracker.ui.components.TransactionDetailsDialog
import com.example.expensetracker.ui.components.TransactionItem
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.theme.RadiusMd
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.example.expensetracker.model.TransactionType

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    transactionEditLogs: List<TransactionEditLogEntity> = emptyList(),
    categories: List<CategoryEntity> = emptyList(),
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {}
) {
    val appColors = LocalAppColors.current

    var viewingDetailsTransaction by remember { mutableStateOf<Transaction?>(null) }
    var menuTransaction by remember { mutableStateOf<Transaction?>(null) }

    // --- Filter & Search States ---
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }
    var selectedSource by remember { mutableStateOf("All") }
    var selectedDateOption by remember { mutableStateOf("All time") }
    var customStartDate by remember { mutableLongStateOf(0L) }
    var customEndDate by remember { mutableLongStateOf(0L) }
    var sortOption by remember { mutableStateOf("Newest first") }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val isFilterActive = searchQuery.isNotBlank() ||
            selectedCategory != "All" ||
            selectedType != "All" ||
            selectedSource != "All" ||
            selectedDateOption != "All time" ||
            sortOption != "Newest first"

    // Filter pipeline
    val filteredTransactions = remember(
        transactions,
        searchQuery,
        selectedCategory,
        selectedType,
        selectedSource,
        selectedDateOption,
        customStartDate,
        customEndDate,
        sortOption
    ) {
        transactions.asSequence()
            .filter { transaction ->
                if (searchQuery.isNotBlank()) {
                    transaction.title.contains(searchQuery, ignoreCase = true)
                } else true
            }
            .filter { transaction ->
                if (selectedCategory != "All") {
                    transaction.displayCategoryName.equals(selectedCategory, ignoreCase = true)
                } else true
            }
            .filter { transaction ->
                if (selectedType != "All") {
                    transaction.type.name.equals(selectedType, ignoreCase = true)
                } else true
            }
            .filter { transaction ->
                if (selectedSource != "All") {
                    when (selectedSource.lowercase()) {
                        "manual" -> transaction.source == TransactionSource.MANUAL
                        "gpay" -> transaction.source == TransactionSource.GPAY
                        "bank sms" -> transaction.source == TransactionSource.BANK_SMS
                        "bank email" -> transaction.source == TransactionSource.BANK_EMAIL
                        "notification" -> transaction.source == TransactionSource.NOTIFICATION
                        else -> true
                    }
                } else true
            }
            .filter { transaction ->
                filterByDateOption(transaction.timestamp, selectedDateOption, customStartDate, customEndDate)
            }
            .let { sequence ->
                if (sortOption == "Oldest first") {
                    sequence.sortedBy { it.timestamp }
                } else {
                    sequence.sortedByDescending { it.timestamp }
                }
            }
            .toList()
    }

    // Date grouping
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { transaction ->
            formatDateLabel(transaction.timestamp, transaction.date)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Filter Bar
        item {
            FilterBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                categories = categories.map { it.name },
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                selectedSource = selectedSource,
                onSourceSelected = { selectedSource = it },
                selectedDateOption = selectedDateOption,
                onDateOptionSelected = { option ->
                    selectedDateOption = option
                    if (option == "Custom") {
                        showCustomDateDialog = true
                    }
                },
                sortOption = sortOption,
                onSortOptionSelected = { sortOption = it },
                isFilterActive = isFilterActive,
                onClearFilters = {
                    searchQuery = ""
                    selectedCategory = "All"
                    selectedType = "All"
                    selectedSource = "All"
                    selectedDateOption = "All time"
                    customStartDate = 0L
                    customEndDate = 0L
                    sortOption = "Newest first"
                }
            )
        }

        // 2. Result Count
        item {
            Text(
                text = "${filteredTransactions.size} of ${transactions.size} transactions",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = appColors.inkFaint,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }

        // 3. Date-grouped Transaction Groups
        if (groupedTransactions.isEmpty()) {
            item {
                Text(
                    text = "No matching transactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.inkFaint,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 2.dp)
                )
            }
        } else {
            groupedTransactions.forEach { (dateLabel, groupItems) ->
                item(key = "header_$dateLabel") {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 2.dp)
                    )
                }

                item(key = "group_$dateLabel") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusMd,
                        colors = CardDefaults.cardColors(
                            containerColor = appColors.surface
                        ),
                        border = BorderStroke(1.dp, appColors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            groupItems.forEachIndexed { index, transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    onCardClick = { viewingDetailsTransaction = transaction },
                                    onLongClick = { menuTransaction = transaction },
                                    onEditClick = { onEditTransaction(transaction) },
                                    onDeleteClick = { onDeleteTransaction(transaction) },
                                    showBorder = false
                                )
                                if (index < groupItems.size - 1) {
                                    HorizontalDivider(
                                        color = appColors.border,
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // Custom Date Range Picker Dialog
    if (showCustomDateDialog) {
        DateRangePickerDialog(
            onDismiss = { showCustomDateDialog = false },
            onDateRangeSelected = { startMs, endMs ->
                customStartDate = startMs
                customEndDate = endMs
                showCustomDateDialog = false
            }
        )
    }

    // Transaction Details Dialog
    viewingDetailsTransaction?.let { transaction ->
        TransactionDetailsDialog(
            transaction = transaction,
            editLogs = transactionEditLogs.filter { it.transactionId == transaction.id },
            onDismiss = { viewingDetailsTransaction = null },
            onEditClick = { tx ->
                viewingDetailsTransaction = null
                onEditTransaction(tx)
            },
            onDeleteClick = { tx ->
                viewingDetailsTransaction = null
                onDeleteTransaction(tx)
            }
        )
    }

    // Long-Press Action Menu Dialog
    menuTransaction?.let { tx ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { menuTransaction = null },
            title = {
                Text(
                    text = tx.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = appColors.ink
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                menuTransaction = null
                                onEditTransaction(tx)
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = appColors.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Edit Transaction",
                                fontWeight = FontWeight.Bold,
                                color = appColors.ink
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                menuTransaction = null
                                onDeleteTransaction(tx)
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = appColors.negative,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Delete Transaction",
                                fontWeight = FontWeight.Bold,
                                color = appColors.negative
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuTransaction = null }) {
                    Text("Cancel", color = appColors.ink)
                }
            }
        )
    }
}

private fun formatDateLabel(timestamp: Long, fallbackDate: String): String {
    if (timestamp <= 0) return fallbackDate
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    calendar.timeInMillis = timestamp

    return when {
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"

        calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"

        else -> {
            try {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            } catch (e: Exception) {
                fallbackDate
            }
        }
    }
}

private fun filterByDateOption(
    timestamp: Long,
    option: String,
    customStart: Long,
    customEnd: Long
): Boolean {
    if (option == "All time") return true
    val calendar = Calendar.getInstance()

    return when (option) {
        "Today" -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            timestamp >= calendar.timeInMillis
        }
        "This week" -> {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            timestamp >= calendar.timeInMillis
        }
        "This month" -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            timestamp >= calendar.timeInMillis
        }
        "Custom" -> {
            if (customStart > 0 && customEnd > 0) {
                timestamp in customStart..customEnd
            } else true
        }
        else -> true
    }
}
