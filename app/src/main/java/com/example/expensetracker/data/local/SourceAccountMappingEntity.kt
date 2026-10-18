package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_account_mappings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["sourcePattern", "senderOrPackage"])
    ]
)
data class SourceAccountMappingEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val sourcePattern: String,
    val senderOrPackage: String? = null,
    val matchingContext: String? = null,
    val isConfirmed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
