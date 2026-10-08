package com.example.expensetracker.service

import com.example.expensetracker.model.TransactionType

data class TemplateMatchResult(
    val matched: Boolean,
    val templateId: String? = null,
    val templateName: String? = null,
    val amount: Double? = null,
    val merchant: String? = null,
    val account: String? = null,
    val reference: String? = null,
    val balance: Double? = null,
    val dateStr: String? = null,
    val transactionType: TransactionType? = null
)
