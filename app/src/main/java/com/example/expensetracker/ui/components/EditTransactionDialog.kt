package com.example.expensetracker.ui.components

import androidx.compose.runtime.Composable
import com.example.expensetracker.model.Transaction

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    AddOrEditTransactionBottomSheet(
        initialTransaction = transaction,
        availableCategories = availableCategories,
        onDismiss = onDismiss,
        onSave = onSave
    )
}
