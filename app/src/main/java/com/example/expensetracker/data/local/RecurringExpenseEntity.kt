package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey
    val id: String,
    val merchant: String,
    val amount: Double,
    val frequency: String,
    val intervalDays: Int,
    val lastTransactionTimestamp: Long,
    val confirmedAt: Long,
    val active: Boolean = true,
    val dismissed: Boolean = false
)
