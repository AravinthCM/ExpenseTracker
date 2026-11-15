package com.example.expensetracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.DetectedRecurringCandidate
import com.example.expensetracker.ui.components.CategoryDrillDownDialog
import com.example.expensetracker.ui.components.DateRangePickerDialog
import com.example.expensetracker.ui.components.RecurringSection
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────── Models ─────────────────────────

private data class CategoryRowData(
    val name: String,
    val amount: Double,
    val percentage: Float,
    val color: Color,
    val count: Int,
    val deltaPercent: Double?, // null = no comparison available
    val isNew: Boolean
)

private val CategoryPalette = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFFFB8C00), Color(0xFF43A047),
    Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF757575)
)

private val PeriodOptions = listOf("This week", "This month", "Last month", "This year", "Custom")
private const val COLLAPSED_CATEGORY_COUNT = 5

// ───────────────────────── Screen ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AnalyticsScreen(
    transactions: List<Transaction>,
    categories: List<CategoryEntity> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    selectedAccountId: String? = null,
    recurringEntities: List<RecurringExpenseEntity> = emptyList(),
    onSelectAccount: (String?) -> Unit = {},
    onNavigateBack: () -> Unit,
    onConfirmCandidate: (DetectedRecurringCandidate) -> Unit = {},
    onDismissCandidate: (DetectedRecurringCandidate) -> Unit = {},
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {}
) {
    val appColors = LocalAppColors.current

    var selectedPeriod by remember { mutableStateOf("This month") }
    var customStartDate by remember { mutableLongStateOf(0L) }
    var customEndDate by remember { mutableLongStateOf(0L) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var selectedDrillDownCategory by remember { mutableStateOf<String?>(null) }
    var showAllCategories by remember { mutableStateOf(false) }

    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    val validSelectedAccountId = remember(selectedAccountId, activeAccounts) {
        selectedAccountId?.takeIf { id -> activeAccounts.any { it.id == id } }
    }
    val selectedAccountName = remember(validSelectedAccountId, activeAccounts) {
        validSelectedAccountId?.let { id -> activeAccounts.find { it.id == id }?.name } ?: "All accounts"
    }

    // ── Period ranges (current + comparison) ──
    val range = remember(selectedPeriod, customStartDate, customEndDate) {
        resolveRange(selectedPeriod, customStartDate, customEndDate)
    }
    val previousRange = remember(range, selectedPeriod) {
        range?.let { previousRange(selectedPeriod, it) }
    }

    // ── Filtered data ──
    val scopedTransactions = remember(transactions, validSelectedAccountId) {
        if (validSelectedAccountId == null) transactions
        else transactions.filter { it.accountId == validSelectedAccountId }
    }
    val currentTx = remember(scopedTransactions, range) {
        scopedTransactions.filter { range == null || it.timestamp in range }
    }
    val previousExpenses = remember(scopedTransactions, previousRange) {
        previousRange?.let { p ->
            scopedTransactions.filter { it.type == TransactionType.EXPENSE && it.timestamp in p }
        } ?: emptyList()
    }

    val expenseTransactions = remember(currentTx) { currentTx.filter { it.type == TransactionType.EXPENSE } }
    val totalIncome = remember(currentTx) { currentTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } }
    val totalExpenses = remember(expenseTransactions) { expenseTransactions.sumOf { it.amount } }
    val previousTotal = remember(previousExpenses) { previousExpenses.sumOf { it.amount } }
    val netBalance = totalIncome - totalExpenses
    val savingsRate: Double? = if (totalIncome > 0) (netBalance / totalIncome) * 100.0 else null
    val totalDelta: Double? = if (previousTotal > 0) ((totalExpenses - previousTotal) / previousTotal) * 100.0 else null

    // ── Category breakdown (sorted FIRST so colors follow rank) ──
    val categoryRows = remember(expenseTransactions, previousExpenses, totalExpenses) {
        val prevByCat = previousExpenses
            .groupBy { it.displayCategoryName.lowercase() }
            .mapValues { (_, v) -> v.sumOf { it.amount } }

        expenseTransactions
            .groupBy { it.displayCategoryName }
            .map { (name, txs) -> Triple(name, txs.sumOf { it.amount }, txs.size) }
            .sortedByDescending { it.second }
            .mapIndexed { index, (name, amount, count) ->
                val prev = prevByCat[name.lowercase()] ?: 0.0
                CategoryRowData(
                    name = name.lowercase().replaceFirstChar { it.uppercase() },
                    amount = amount,
                    percentage = if (totalExpenses > 0) ((amount / totalExpenses) * 100).toFloat() else 0f,
                    color = CategoryPalette[index % CategoryPalette.size],
                    count = count,
                    deltaPercent = if (prev > 0) ((amount - prev) / prev) * 100.0 else null,
                    isNew = previousTotal > 0 && prev == 0.0
                )
            }
    }

    val periodLabel = periodDisplayLabel(selectedPeriod, customStartDate, customEndDate)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Compact, pinned filter bar ──
        stickyHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.bg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterDropdown(
                    label = periodLabel,
                    options = PeriodOptions,
                    selectedIndex = PeriodOptions.indexOf(selectedPeriod),
                    onSelect = { index ->
                        selectedPeriod = PeriodOptions[index]
                        if (selectedPeriod == "Custom") showCustomDateDialog = true
                    }
                )
                if (activeAccounts.size > 1) {
                    val accountOptions = listOf("All accounts") + activeAccounts.map { it.name }
                    FilterDropdown(
                        label = selectedAccountName,
                        options = accountOptions,
                        selectedIndex = validSelectedAccountId
                            ?.let { id -> activeAccounts.indexOfFirst { it.id == id } + 1 } ?: 0,
                        onSelect = { index ->
                            onSelectAccount(if (index == 0) null else activeAccounts[index - 1].id)
                        }
                    )
                }
            }
        }

        if (currentTx.isEmpty()) {
            item { EmptyState(selectedAccountName, periodLabel) }
        } else {
            // ── Hero: donut + headline + KPI strip ──
            item {
                SpendingHeroCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    totalExpenses = totalExpenses,
                    totalDelta = totalDelta,
                    comparisonLabel = comparisonLabel(selectedPeriod),
                    categoryRows = categoryRows,
                    totalIncome = totalIncome,
                    netBalance = netBalance,
                    savingsRate = savingsRate
                )
            }

            // ── Ranked categories ──
            if (categoryRows.isNotEmpty()) {
                item {
                    val visibleRows =
                        if (showAllCategories) categoryRows else categoryRows.take(COLLAPSED_CATEGORY_COUNT)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.surface),
                        border = BorderStroke(1.dp, appColors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Where your money went",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.ink
                                )
                                Text(
                                    "Tap for details",
                                    fontSize = 11.sp,
                                    color = appColors.inkFaint
                                )
                            }
                            Spacer(Modifier.height(6.dp))

                            visibleRows.forEach { row ->
                                CategoryRow(
                                    data = row,
                                    comparisonLabel = comparisonLabel(selectedPeriod),
                                    onClick = { selectedDrillDownCategory = row.name }
                                )
                            }

                            if (categoryRows.size > COLLAPSED_CATEGORY_COUNT) {
                                TextButton(
                                    onClick = { showAllCategories = !showAllCategories },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        if (showAllCategories) "Show less"
                                        else "Show all ${categoryRows.size} categories"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                RecurringSection(
                    transactions = currentTx,
                    recurringEntities = recurringEntities,
                    onConfirmCandidate = onConfirmCandidate,
                    onDismissCandidate = onDismissCandidate
                )
            }
        }
    }

    if (showCustomDateDialog) {
        DateRangePickerDialog(
            onDismiss = {
                showCustomDateDialog = false
                // Don't leave the user on an empty "Custom" filter
                if (customStartDate == 0L) selectedPeriod = "This month"
            },
            onDateRangeSelected = { startMs, endMs ->
                customStartDate = startMs
                customEndDate = endMs
                showCustomDateDialog = false
            }
        )
    }

    selectedDrillDownCategory?.let { catName ->
        val matchingTx = currentTx.filter {
            it.type == TransactionType.EXPENSE && it.displayCategoryName.equals(catName, ignoreCase = true)
        }
        CategoryDrillDownDialog(
            categoryName = catName,
            periodTitle = "$periodLabel ($selectedAccountName)",
            transactions = matchingTx,
            onDismiss = { selectedDrillDownCategory = null },
            onEditTransaction = onEditTransaction,
            onDeleteTransaction = onDeleteTransaction
        )
    }
}

// ───────────────────────── Filter dropdown ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val appColors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = appColors.surface,
            border = BorderStroke(1.dp, appColors.border)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = appColors.inkMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                    trailingIcon = if (index == selectedIndex) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = appColors.accent) }
                    } else null
                )
            }
        }
    }
}

// ───────────────────────── Hero card ─────────────────────────

@Composable
private fun SpendingHeroCard(
    modifier: Modifier = Modifier,
    totalExpenses: Double,
    totalDelta: Double?,
    comparisonLabel: String,
    categoryRows: List<CategoryRowData>,
    totalIncome: Double,
    netBalance: Double,
    savingsRate: Double?
) {
    val appColors = LocalAppColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surface),
        border = BorderStroke(1.dp, appColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SpendDonut(
                rows = categoryRows,
                totalExpense = totalExpenses,
                size = 200.dp,
                strokeWidth = 24.dp
            )

            Spacer(Modifier.height(12.dp))

            if (totalDelta != null) {
                val up = totalDelta > 0
                val tint = if (up) appColors.expenseRed else appColors.incomeGreen
                Surface(shape = RoundedCornerShape(20.dp), color = tint.copy(alpha = 0.12f)) {
                    Text(
                        text = "${if (up) "▲" else "▼"} ${abs(totalDelta).roundToInt()}% vs $comparisonLabel",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Slim KPI strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.surfaceAlt)
                    .padding(vertical = 10.dp)
            ) {
                MiniStat("Income", CurrencyFormatter.format(totalIncome), appColors.incomeGreen, Modifier.weight(1f))
                MiniStat(
                    "Net",
                    CurrencyFormatter.format(netBalance),
                    if (netBalance >= 0) appColors.incomeGreen else appColors.expenseRed,
                    Modifier.weight(1f)
                )
                MiniStat(
                    "Saved",
                    savingsRate?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—",
                    if ((savingsRate ?: 0.0) >= 0) appColors.accent else appColors.expenseRed,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = appColors.inkFaint)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpendDonut(
    rows: List<CategoryRowData>,
    totalExpense: Double,
    size: Dp,
    strokeWidth: Dp
) {
    val appColors = LocalAppColors.current

    var started by remember(rows) { mutableStateOf(false) }
    LaunchedEffect(rows) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(700),
        label = "donutProgress"
    )

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokePx = strokeWidth.toPx()
            if (rows.isEmpty() || totalExpense <= 0) {
                drawArc(
                    color = appColors.surfaceAlt, startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, style = Stroke(width = strokePx)
                )
            } else {
                val gap = if (rows.size > 1) 2f else 0f
                var cumulative = 0f
                rows.forEach { row ->
                    val full = (row.percentage / 100f) * 360f
                    val start = -90f + cumulative * progress
                    val sweep = (full * progress - gap).coerceAtLeast(0.5f)
                    drawArc(
                        color = row.color,
                        startAngle = start + gap / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                    cumulative += full
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Spent", fontSize = 12.sp, color = appColors.inkFaint)
            Text(
                text = CurrencyFormatter.format(totalExpense),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.ink,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (rows.isNotEmpty()) {
                Text(
                    "${rows.size} ${if (rows.size == 1) "category" else "categories"}",
                    fontSize = 11.sp,
                    color = appColors.inkFaint
                )
            }
        }
    }
}

// ───────────────────────── Category row ─────────────────────────

@Composable
private fun CategoryRow(
    data: CategoryRowData,
    comparisonLabel: String,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(data.color, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(
                text = data.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = CurrencyFormatter.format(data.amount),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.ink,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(6.dp))

        // Share-of-spend bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(appColors.surfaceAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((data.percentage / 100f).coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(data.color)
            )
        }

        Spacer(Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pctText = if (data.percentage < 1f) "<1%" else "${data.percentage.roundToInt()}%"
            Text(
                text = "$pctText of spend · ${data.count} ${if (data.count == 1) "txn" else "txns"}",
                fontSize = 11.5.sp,
                color = appColors.inkFaint
            )
            when {
                data.deltaPercent != null -> {
                    val up = data.deltaPercent > 0
                    val tint = when {
                        abs(data.deltaPercent) < 1 -> appColors.inkFaint
                        up -> appColors.expenseRed
                        else -> appColors.incomeGreen
                    }
                    Text(
                        text = "${if (up) "▲" else "▼"} ${abs(data.deltaPercent).roundToInt()}% vs $comparisonLabel",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = tint
                    )
                }
                data.isNew -> Text(
                    text = "New this period",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = appColors.accent
                )
            }
        }
    }
}

// ───────────────────────── Empty state ─────────────────────────

@Composable
private fun EmptyState(accountName: String, periodLabel: String) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
        border = BorderStroke(1.dp, appColors.border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(appColors.bg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, null, tint = appColors.inkFaint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("No transactions here", fontWeight = FontWeight.Bold, color = appColors.ink)
            Text(
                "$accountName • $periodLabel",
                color = appColors.inkFaint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Try a different period or account.",
                color = appColors.inkFaint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ───────────────────────── Period helpers ─────────────────────────

private fun Calendar.startOfDay() = apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

/** Returns the selected period as a millisecond range, or null for "no filter". */
private fun resolveRange(period: String, customStart: Long, customEnd: Long): LongRange? {
    val now = System.currentTimeMillis()
    return when (period) {
        "This week" -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek); startOfDay()
        }.timeInMillis..now
        "This month" -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); startOfDay()
        }.timeInMillis..now
        "Last month" -> {
            val start = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); startOfDay()
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); startOfDay()
            }.timeInMillis - 1
            start..end
        }
        "This year" -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1); startOfDay()
        }.timeInMillis..now
        "Custom" -> if (customStart > 0 && customEnd > 0) customStart..customEnd else null
        else -> null
    }
}

/** Like-for-like comparison window (same point last month, same days last week, etc.). */
private fun previousRange(period: String, current: LongRange): LongRange {
    fun shift(ms: Long, field: Int, amount: Int) =
        Calendar.getInstance().apply { timeInMillis = ms; add(field, amount) }.timeInMillis

    return when (period) {
        "This week" -> shift(current.first, Calendar.DAY_OF_YEAR, -7)..shift(current.last, Calendar.DAY_OF_YEAR, -7)
        "This month", "Last month" -> shift(current.first, Calendar.MONTH, -1)..shift(current.last, Calendar.MONTH, -1)
        "This year" -> shift(current.first, Calendar.YEAR, -1)..shift(current.last, Calendar.YEAR, -1)
        else -> {
            val length = current.last - current.first + 1
            (current.first - length)..(current.first - 1)
        }
    }
}

private fun comparisonLabel(period: String) = when (period) {
    "This week" -> "last week"
    "This month" -> "last month"
    "Last month" -> "month before"
    "This year" -> "last year"
    else -> "previous period"
}

private fun periodDisplayLabel(period: String, customStart: Long, customEnd: Long): String {
    if (period == "Custom" && customStart > 0 && customEnd > 0) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        return "${sdf.format(Date(customStart))} – ${sdf.format(Date(customEnd))}"
    }
    return period
}
