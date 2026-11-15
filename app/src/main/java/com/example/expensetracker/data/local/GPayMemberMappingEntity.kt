package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A confirmed mapping from a GPay requester display name to an existing split group member. */
@Entity(
    tableName = "gpay_member_mappings",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
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
        Index(value = ["groupId", "normalizedRequesterName"], unique = true),
        Index(value = ["memberId"])
    ]
)
data class GPayMemberMappingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val memberId: String,
    val normalizedRequesterName: String,
    val createdAt: Long = System.currentTimeMillis()
)
