package com.example.expensetracker.ui.components

import androidx.compose.runtime.Composable
import com.example.expensetracker.model.Transaction

@Composable
fun TransactionDetailsDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onEditClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit
) {
    TransactionDetailBottomSheet(
        transaction = transaction,
        onDismiss = onDismiss,
        onEdit = onEditClick,
        onDelete = onDeleteClick
    )
}
