package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "repayment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = RepaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["repaymentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExpenseSplitShareEntity::class,
            parentColumns = ["id"],
            childColumns = ["shareId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["repaymentId"]),
        Index(value = ["shareId"])
    ]
)
data class RepaymentAllocationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val repaymentId: String,
    val shareId: String,
    val allocatedAmountPaise: Long
)
