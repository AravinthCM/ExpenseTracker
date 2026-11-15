package com.example.expensetracker.ui.components

import androidx.compose.runtime.Composable
import com.example.expensetracker.data.local.TransactionEditLogEntity
import com.example.expensetracker.model.Transaction

@Composable
fun TransactionDetailsDialog(
    transaction: Transaction,
    editLogs: List<TransactionEditLogEntity> = emptyList(),
    onDismiss: () -> Unit,
    onEditClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit
) {
    TransactionDetailBottomSheet(
        transaction = transaction,
        editLogs = editLogs,
        onDismiss = onDismiss,
        onEdit = onEditClick,
        onDelete = onDeleteClick
    )
}
