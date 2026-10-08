package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_templates")
data class PaymentTemplateEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val transactionType: String, // "EXPENSE" (Debit) or "INCOME" (Credit)
    val sampleText: String,
    val templatePattern: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
