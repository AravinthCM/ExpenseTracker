package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "expense_split_shares",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseSplitEntity::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SplitGroupMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["splitId"]),
        Index(value = ["memberId"]),
        Index(value = ["splitId", "memberId"], unique = true)
    ]
)
data class ExpenseSplitShareEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val splitId: String,
    val memberId: String,
    val shareAmountPaise: Long
)
