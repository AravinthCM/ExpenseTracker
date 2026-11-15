package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_edit_logs",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["editedAt"])
    ]
)
data class TransactionEditLogEntity(
    @PrimaryKey
    val id: String,
    val transactionId: String,
    val editedAt: Long,
    val editSource: String,
    val changedFields: String,
    val details: String
)
