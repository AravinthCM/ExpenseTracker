package com.example.expensetracker.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class SplitGroupWithMembers(
    @Embedded val group: SplitGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val members: List<SplitGroupMemberEntity>
)
