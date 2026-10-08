package com.example.expensetracker.data.local

import androidx.room.TypeConverter
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType

class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType?): String {
        return type?.name ?: TransactionType.EXPENSE.name
    }

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType {
        return if (value != null) {
            try {
                TransactionType.valueOf(value)
            } catch (e: Exception) {
                TransactionType.EXPENSE
            }
        } else {
            TransactionType.EXPENSE
        }
    }

    @TypeConverter
    fun fromTransactionCategory(category: TransactionCategory?): String {
        return category?.name ?: TransactionCategory.OTHER.name
    }

    @TypeConverter
    fun toTransactionCategory(value: String?): TransactionCategory {
        return if (value != null) {
            try {
                TransactionCategory.valueOf(value)
            } catch (e: Exception) {
                TransactionCategory.OTHER
            }
        } else {
            TransactionCategory.OTHER
        }
    }

    @TypeConverter
    fun fromTransactionSource(source: TransactionSource?): String {
        return source?.name ?: TransactionSource.MANUAL.name
    }

    @TypeConverter
    fun toTransactionSource(value: String?): TransactionSource {
        return if (value != null) {
            try {
                TransactionSource.valueOf(value)
            } catch (e: Exception) {
                TransactionSource.MANUAL
            }
        } else {
            TransactionSource.MANUAL
        }
    }
}
