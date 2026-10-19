package com.example.expensetracker.service

import com.example.expensetracker.model.TransactionType

data class NormalizedTransaction(
    val amount: Double,
    val name: String,
    val type: TransactionType,
    val source: String,
    val timestamp: Long,
    val originalText: String,
    val referenceNumber: String? = null,
    val accountId: String? = null,
    val balance: Double? = null
)
