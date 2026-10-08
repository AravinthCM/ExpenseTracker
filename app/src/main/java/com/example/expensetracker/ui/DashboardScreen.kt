package com.example.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.components.BalanceCard
import com.example.expensetracker.ui.components.CategoryDonutChart
import com.example.expensetracker.ui.components.CategorySpendingItem
import com.example.expensetracker.ui.components.TransactionDetailsDialog
import com.example.expensetracker.ui.components.TransactionItem
import com.example.expensetracker.ui.components.getCategoryIconAndColor
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.theme.RadiusMd

@Composable
fun DashboardScreen(
    transactions: List<Transaction>,
    categories: List<CategoryEntity> = emptyList(),
    onAddTransactionClick: () -> Unit,
    onAnalyticsClick: () -> Unit = {},
    onTransactionsClick: () -> Unit = {},
    onManagementClick: () -> Unit = {},
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {}
) {
    val appColors = LocalAppColors.current
    var viewingDetailsTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Balance Calculations
    val totalIncome = transactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }

    val totalExpenses = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    val totalBalance = totalIncome - totalExpenses

    // Donut Category Breakdown (Top spending categories)
    val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
    val totalExpenseSum = expenseTransactions.sumOf { it.amount }

    val categorySpendingMap = expenseTransactions
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }

    val donutItems = categorySpendingMap.entries
        .sortedByDescending { it.value }
        .take(4)
        .map { (cat, amount) ->
            val pct = if (totalExpenseSum > 0) ((amount / totalExpenseSum) * 100).toFloat() else 0f
            val (_, color, _) = getCategoryIconAndColor(cat, appColors.isDark)
            CategorySpendingItem(
                categoryName = cat.name.lowercase().replaceFirstChar { it.uppercase() },
                amount = amount,
                percentage = pct,
                color = color
            )
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Card (Total balance + Split Income/Expenses)
        item {
            BalanceCard(
                totalBalance = totalBalance,
                totalIncome = totalIncome,
                totalExpenses = totalExpenses
            )
        }

        // 2. Spending by Category Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMd,
                colors = CardDefaults.cardColors(
                    containerColor = appColors.surface
                ),
                border = BorderStroke(1.dp, appColors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spending by category",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = appColors.ink
                        )
                        TextButton(
                            onClick = onAnalyticsClick,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = appColors.accent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CategoryDonutChart(
                        categoryItems = donutItems,
                        totalExpense = totalExpenseSum,
                        centerLabel = "Spent"
                    )
                }
            }
        }

        // 3. Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent transactions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = appColors.ink
                )
                TextButton(
                    onClick = onTransactionsClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "View all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = appColors.accent
                    )
                }
            }
        }

        // 4. Recent Transactions List (Top 5)
        val recentList = transactions.sortedByDescending { it.timestamp }.take(5)
        if (recentList.isEmpty()) {
            item {
                Text(
                    text = "No recent transactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.inkFaint,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)
                )
            }
        } else {
            items(
                items = recentList,
                key = { it.id }
            ) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onCardClick = { viewingDetailsTransaction = transaction },
                    onEditClick = { onEditTransaction(transaction) },
                    onDeleteClick = { onDeleteTransaction(transaction) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // Transaction Details Dialog
    viewingDetailsTransaction?.let { transaction ->
        TransactionDetailsDialog(
            transaction = transaction,
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
}
