package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "balance_observations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["referenceNumber"]),
        Index(value = ["timestamp"])
    ]
)
data class BalanceObservationEntity(
    @PrimaryKey
    val id: String,
    val accountId: String? = null,
    val accountSuffix: String? = null,
    val balancePaise: Long,
    val balanceType: String = "AVAILABLE",
    val currency: String = "INR",
    val timestamp: Long,
    val source: String,
    val senderOrPackage: String? = null,
    val referenceNumber: String? = null,
    val rawText: String = "",
    val isResolved: Boolean = false,
    val isReconciled: Boolean = false,
    val reconciledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
