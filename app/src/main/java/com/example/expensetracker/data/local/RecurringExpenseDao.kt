package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM recurring_expenses WHERE active = 1 ORDER BY confirmedAt DESC")
    fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses")
    fun getAllRecurringExpensesSync(): List<RecurringExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecurringExpense(entity: RecurringExpenseEntity)

    @Update
    fun updateRecurringExpense(entity: RecurringExpenseEntity)

    @Delete
    fun deleteRecurringExpense(entity: RecurringExpenseEntity)
}
