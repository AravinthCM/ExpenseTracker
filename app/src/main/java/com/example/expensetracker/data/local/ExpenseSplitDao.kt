package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseSplitDao {

    @Transaction
    @Query("SELECT * FROM expense_splits ORDER BY createdAt DESC")
    fun getAllSplitsWithShares(): Flow<List<ExpenseSplitWithShares>>

    @Transaction
    @Query("SELECT * FROM expense_splits ORDER BY createdAt DESC")
    fun getAllSplitsWithSharesSync(): List<ExpenseSplitWithShares>

    @Transaction
    @Query("SELECT * FROM expense_splits WHERE transactionId = :transactionId AND groupId = :groupId")
    fun getSplitForTransactionAndGroupSync(transactionId: String, groupId: String): ExpenseSplitWithShares?

    @Transaction
    @Query("SELECT * FROM expense_splits WHERE transactionId = :transactionId")
    fun getSplitForTransaction(transactionId: String): Flow<ExpenseSplitWithShares?>

    @Transaction
    @Query("SELECT * FROM expense_splits WHERE transactionId = :transactionId LIMIT 1")
    fun getSplitForTransactionSync(transactionId: String): ExpenseSplitWithShares?

    @Transaction
    @Query("SELECT * FROM expense_splits WHERE groupId = :groupId")
    fun getSplitsForGroupSync(groupId: String): List<ExpenseSplitWithShares>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSplit(split: ExpenseSplitEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertShares(shares: List<ExpenseSplitShareEntity>)

    @Query("DELETE FROM expense_split_shares WHERE splitId = :splitId")
    fun deleteSharesForSplit(splitId: String)

    @Transaction
    fun saveSplitWithShares(split: ExpenseSplitEntity, shares: List<ExpenseSplitShareEntity>) {
        insertSplit(split)
        deleteSharesForSplit(split.id)
        val updatedShares = shares.map { it.copy(splitId = split.id) }
        insertShares(updatedShares)
    }

    @Delete
    fun deleteSplit(split: ExpenseSplitEntity)

    @Query("DELETE FROM expense_splits WHERE id = :id")
    fun deleteSplitById(id: String)
}
