package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionEditLogDao {

    @Query("SELECT * FROM transaction_edit_logs ORDER BY editedAt DESC")
    fun getAllLogs(): Flow<List<TransactionEditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(log: TransactionEditLogEntity)

    @Query("SELECT * FROM transaction_edit_logs WHERE transactionId = :transactionId ORDER BY editedAt DESC")
    fun getLogsForTransaction(transactionId: String): Flow<List<TransactionEditLogEntity>>

    @Query("SELECT * FROM transaction_edit_logs WHERE transactionId = :transactionId ORDER BY editedAt DESC")
    fun getLogsForTransactionSync(transactionId: String): List<TransactionEditLogEntity>
}
