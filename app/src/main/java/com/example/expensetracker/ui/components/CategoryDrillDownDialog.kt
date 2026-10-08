package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.ui.util.CurrencyFormatter

@Composable
fun CategoryDrillDownDialog(
    categoryName: String,
    periodTitle: String,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {}
) {
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    val totalSpent = transactions.sumOf { it.amount }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "$categoryName Transactions",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "$periodTitle • Total: ${CurrencyFormatter.format(totalSpent)} (${transactions.size} items)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (transactions.isEmpty()) {
                Text("No transactions found for this category in the selected period.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = transactions,
                        key = { it.id }
                    ) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onCardClick = { selectedTransaction = transaction },
                            onEditClick = {
                                onDismiss()
                                onEditTransaction(transaction)
                            },
                            onDeleteClick = {
                                onDismiss()
                                onDeleteTransaction(transaction)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = null
    )

    // Transaction Details Dialog when a specific item is clicked in drill-down
    selectedTransaction?.let { transaction ->
        TransactionDetailsDialog(
            transaction = transaction,
            onDismiss = { selectedTransaction = null },
            onEditClick = { tx ->
                selectedTransaction = null
                onDismiss()
                onEditTransaction(tx)
            },
            onDeleteClick = { tx ->
                selectedTransaction = null
                onDismiss()
                onDeleteTransaction(tx)
            }
        )
    }
}
