package com.example.expensetracker.service

import com.example.expensetracker.model.TransactionType

data class ParsedTransaction(
    val amount: Double,
    val name: String,
    val type: TransactionType,
    val source: String = "GPAY",
    val timestamp: Long = System.currentTimeMillis()
)
