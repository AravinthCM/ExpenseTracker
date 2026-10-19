package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "split_group_members",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class SplitGroupMemberEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val name: String,
    val isMe: Boolean = false,
    val openingDebtPaise: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
