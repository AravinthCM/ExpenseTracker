package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_mappings")
data class CategoryMappingEntity(
    @PrimaryKey
    val id: String,
    val pattern: String,
    val categoryName: String,
    val priority: Int = 0
)
