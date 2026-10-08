package com.example.expensetracker.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class RepaymentWithAllocations(
    @Embedded val repayment: RepaymentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "repaymentId"
    )
    val allocations: List<RepaymentAllocationEntity>
)
