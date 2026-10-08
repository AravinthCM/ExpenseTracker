package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "repayments",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SplitGroupMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedTransactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["linkedTransactionId"], unique = true),
        Index(value = ["groupId"]),
        Index(value = ["memberId"])
    ]
)
data class RepaymentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val memberId: String,
    val amountPaise: Long,
    val unallocatedAmountPaise: Long = 0L,
    val paymentDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val linkedTransactionId: String? = null,
    val note: String? = null
)
