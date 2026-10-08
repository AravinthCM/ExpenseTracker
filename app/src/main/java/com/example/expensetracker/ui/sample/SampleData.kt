package com.example.expensetracker.ui.sample

import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType

/**
 * Temporary sample dataset used for initial application state during development.
 * This can be safely removed or replaced when local database persistence is introduced.
 */
object SampleData {
    val initialTransactions = listOf(
        Transaction(
            id = "1",
            title = "Grocery",
            amount = 450.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.GROCERY,
            date = "Today",
            source = TransactionSource.MANUAL
        ),
        Transaction(
            id = "2",
            title = "Swiggy",
            amount = 180.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.FOOD,
            date = "Today",
            source = TransactionSource.MANUAL
        ),
        Transaction(
            id = "3",
            title = "Salary",
            amount = 25000.0,
            type = TransactionType.INCOME,
            category = TransactionCategory.SALARY,
            date = "Yesterday",
            source = TransactionSource.MANUAL
        ),
        Transaction(
            id = "4",
            title = "Petrol",
            amount = 800.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.TRANSPORT,
            date = "Yesterday",
            source = TransactionSource.MANUAL
        ),
        Transaction(
            id = "5",
            title = "Amazon",
            amount = 1299.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.SHOPPING,
            date = "18 Sep",
            source = TransactionSource.MANUAL
        )
    )
}
