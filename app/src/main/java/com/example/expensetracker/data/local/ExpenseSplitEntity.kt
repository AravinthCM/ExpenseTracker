package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "expense_splits",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SplitGroupMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["payerMemberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId", "groupId"], unique = true),
        Index(value = ["groupId"]),
        Index(value = ["payerMemberId"])
    ]
)
data class ExpenseSplitEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val groupId: String,
    val amountPaise: Long,
    val payerMemberId: String,
    val createdAt: Long = System.currentTimeMillis()
)
