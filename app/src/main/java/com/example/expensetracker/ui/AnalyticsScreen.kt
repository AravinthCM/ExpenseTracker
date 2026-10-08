package com.example.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.expensetracker.ui.theme.BorderSubtle
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.components.CategoryDonutChart
import com.example.expensetracker.ui.components.CategoryDrillDownDialog
import com.example.expensetracker.ui.components.CategorySpendingItem
import com.example.expensetracker.ui.components.DateRangePickerDialog
import com.example.expensetracker.ui.components.IncomeExpenseBar
import com.example.expensetracker.ui.components.SpendingTrendChart
import com.example.expensetracker.ui.components.TrendPoint
import com.example.expensetracker.ui.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.service.DetectedRecurringCandidate
import com.example.expensetracker.ui.components.RecurringSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    transactions: List<Transaction>,
    categories: List<CategoryEntity> = emptyList(),
    recurringEntities: List<RecurringExpenseEntity> = emptyList(),
    onNavigateBack: () -> Unit,
    onConfirmCandidate: (DetectedRecurringCandidate) -> Unit = {},
    onDismissCandidate: (DetectedRecurringCandidate) -> Unit = {},
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {}
) {
    var selectedPeriod by remember { mutableStateOf("This month") }
    var customStartDate by remember { mutableLongStateOf(0L) }
    var customEndDate by remember { mutableLongStateOf(0L) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    var selectedDrillDownCategory by remember { mutableStateOf<String?>(null) }

    val periodOptions = listOf("This week", "This month", "Last month", "This year", "Custom")

    // Filter transactions by selected period
    val periodFilteredTransactions = remember(
        transactions,
        selectedPeriod,
        customStartDate,
        customEndDate
    ) {
        transactions.filter { tx ->
            filterTransactionByPeriod(tx.timestamp, selectedPeriod, customStartDate, customEndDate)
        }
    }

    // Calculations excluding transfers
    val incomeTransactions = periodFilteredTransactions.filter { it.type == TransactionType.INCOME }
    val expenseTransactions = periodFilteredTransactions.filter { it.type == TransactionType.EXPENSE }
    val transferTransactions = periodFilteredTransactions.filter { it.type == TransactionType.TRANSFER }

    val totalIncome = incomeTransactions.sumOf { it.amount }
    val totalExpenses = expenseTransactions.sumOf { it.amount }
    val totalTransfers = transferTransactions.sumOf { it.amount }
    val netBalance = totalIncome - totalExpenses

    // Category breakdown calculation
    val categoryColors = remember {
        listOf(
            Color(0xFFE53935), // Red / Food
            Color(0xFF1E88E5), // Blue / Transport
            Color(0xFF8E24AA), // Purple / Shopping
            Color(0xFF00ACC1), // Cyan / Bills
            Color(0xFFFB8C00), // Orange / Fuel
            Color(0xFF43A047), // Green / Salary
            Color(0xFF757575), // Gray / Other
            Color(0xFFD81B60),
            Color(0xFF3949AB)
        )
    }

    val categorySpendingList = remember(expenseTransactions, categories) {
        val groupedMap = expenseTransactions.groupBy { it.category.name }

        groupedMap.entries.mapIndexed { index, entry ->
            val catNameFormatted = entry.key.lowercase().replaceFirstChar { it.uppercase() }
            val amount = entry.value.sumOf { it.amount }
            val percentage = if (totalExpenses > 0) ((amount / totalExpenses) * 100).toFloat() else 0f
            val color = categoryColors[index % categoryColors.size]

            CategorySpendingItem(
                categoryName = catNameFormatted,
                amount = amount,
                percentage = percentage,
                color = color
            )
        }.sortedByDescending { it.amount }
    }

    // Spending trend data calculation
    val trendPoints = remember(expenseTransactions, selectedPeriod) {
        calculateSpendingTrend(expenseTransactions, selectedPeriod)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Period Selector Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(periodOptions) { option ->
                        FilterChip(
                            selected = selectedPeriod == option,
                            onClick = {
                                selectedPeriod = option
                                if (option == "Custom") {
                                    showCustomDateDialog = true
                                }
                            },
                            label = { Text(option) }
                        )
                    }
                }
            }

            // Empty State Check
            if (periodFilteredTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No transaction data",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "There are no transactions for this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // 2. Summary Cards (Income, Expenses, Net & Transfers)
                item {
                    val appColors = LocalAppColors.current
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryStatCard(
                                title = "Income",
                                amount = totalIncome,
                                amountColor = appColors.incomeGreen,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatCard(
                                title = "Expenses",
                                amount = totalExpenses,
                                amountColor = appColors.expenseRed,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryStatCard(
                                title = "Net",
                                amount = netBalance,
                                amountColor = if (netBalance >= 0) appColors.incomeGreen else appColors.expenseRed,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatCard(
                                title = "Transfers",
                                amount = totalTransfers,
                                amountColor = appColors.transferPurple,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Income vs Expense Comparison Bar
                item {
                    IncomeExpenseBar(
                        incomeAmount = totalIncome,
                        expenseAmount = totalExpenses
                    )
                }

                // 4. Spending by Category (Chart + Breakdown List)
                if (totalExpenses > 0 && categorySpendingList.isNotEmpty()) {
                    item {
                        val appColors = LocalAppColors.current
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = appColors.cardBackground
                            ),
                            border = BorderStroke(1.dp, appColors.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Spending by Category",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = appColors.textPrimary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                CategoryDonutChart(
                                    categoryItems = categorySpendingList,
                                    totalExpense = totalExpenses
                                )
                            }
                        }
                    }
                }

                // 5. Spending Trend Chart
                if (trendPoints.isNotEmpty()) {
                    item {
                        SpendingTrendChart(trendPoints = trendPoints)
                    }
                }
            }

            // 6. Automatic Recurring Expenses & Subscriptions Section
            item {
                RecurringSection(
                    transactions = transactions,
                    recurringEntities = recurringEntities,
                    onConfirmCandidate = onConfirmCandidate,
                    onDismissCandidate = onDismissCandidate
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
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

    // Category Drill-Down Dialog
    selectedDrillDownCategory?.let { catName ->
        val matchingTx = periodFilteredTransactions.filter {
            it.type == TransactionType.EXPENSE &&
            it.category.name.equals(catName, ignoreCase = true)
        }

        CategoryDrillDownDialog(
            categoryName = catName,
            periodTitle = selectedPeriod,
            transactions = matchingTx,
            onDismiss = { selectedDrillDownCategory = null },
            onEditTransaction = onEditTransaction,
            onDeleteTransaction = onDeleteTransaction
        )
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    amount: Double,
    amountColor: Color,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = appColors.cardBackground
        ),
        border = BorderStroke(1.dp, appColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = appColors.textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = CurrencyFormatter.format(amount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = amountColor
            )
        }
    }
}

private fun filterTransactionByPeriod(
    timestamp: Long,
    period: String,
    customStart: Long,
    customEnd: Long
): Boolean {
    val cal = Calendar.getInstance()

    return when (period) {
        "This week" -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            timestamp >= cal.timeInMillis
        }
        "This month" -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            timestamp >= cal.timeInMillis
        }
        "Last month" -> {
            cal.add(Calendar.MONTH, -1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startLastMonth = cal.timeInMillis

            cal.add(Calendar.MONTH, 1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val startThisMonth = cal.timeInMillis

            timestamp in startLastMonth until startThisMonth
        }
        "This year" -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            timestamp >= cal.timeInMillis
        }
        "Custom" -> {
            if (customStart > 0 && customEnd > 0) {
                timestamp in customStart..customEnd
            } else true
        }
        else -> true
    }
}

private fun calculateSpendingTrend(
    expenseTransactions: List<Transaction>,
    period: String
): List<TrendPoint> {
    if (expenseTransactions.isEmpty()) return emptyList()

    val sdf = when (period) {
        "This week" -> SimpleDateFormat("EEE", Locale.getDefault())
        "This month", "Last month" -> SimpleDateFormat("dd MMM", Locale.getDefault())
        "This year" -> SimpleDateFormat("MMM", Locale.getDefault())
        else -> SimpleDateFormat("dd MMM", Locale.getDefault())
    }

    val grouped = expenseTransactions.groupBy { sdf.format(Date(it.timestamp)) }
    return grouped.map { (label, txs) ->
        TrendPoint(label = label, amount = txs.sumOf { it.amount })
    }.take(7)
}
