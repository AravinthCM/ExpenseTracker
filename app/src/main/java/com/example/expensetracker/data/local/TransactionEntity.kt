package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val amount: Double,
    val type: String,
    val category: String,
    val date: String,
    val source: String,
    val timestamp: Long,
    val originalText: String = ""
)

fun TransactionEntity.toDomainModel(): Transaction {
    val transactionType = try {
        TransactionType.valueOf(type)
    } catch (e: Exception) {
        TransactionType.EXPENSE
    }

    val transactionCategory = try {
        TransactionCategory.valueOf(category)
    } catch (e: Exception) {
        TransactionCategory.OTHER
    }

    val transactionSource = try {
        TransactionSource.valueOf(source)
    } catch (e: Exception) {
        TransactionSource.MANUAL
    }

    return Transaction(
        id = id,
        title = title,
        amount = amount,
        type = transactionType,
        category = transactionCategory,
        date = date,
        source = transactionSource,
        timestamp = timestamp,
        originalText = originalText
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        title = title,
        amount = amount,
        type = type.name,
        category = category.name,
        date = date,
        source = source.name,
        timestamp = timestamp,
        originalText = originalText
    )
}
