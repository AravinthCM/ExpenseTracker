package com.example.expensetracker.model

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class TransactionCategory {
    FOOD,
    GROCERY,
    TRANSPORT,
    SHOPPING,
    BILLS,
    SALARY,
    OTHER
}

enum class TransactionSource {
    MANUAL,
    NOTIFICATION,
    GPAY,
    BANK_SMS,
    BANK_EMAIL
}

data class Transaction(
    val id: String,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: TransactionCategory,
    val date: String = "Today",
    val source: TransactionSource = TransactionSource.MANUAL,
    val timestamp: Long = System.currentTimeMillis(),
    val originalText: String = "",
    val accountId: String? = null,
    val referenceNumber: String? = null,
    val balanceIncludedInAccount: Boolean? = null
)
