package com.example.expensetracker.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class ExpenseSplitWithShares(
    @Embedded val split: ExpenseSplitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "splitId"
    )
    val shares: List<ExpenseSplitShareEntity>
)
