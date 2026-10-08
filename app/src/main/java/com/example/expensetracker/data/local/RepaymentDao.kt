package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RepaymentDao {

    @Transaction
    @Query("SELECT * FROM repayments ORDER BY timestamp DESC")
    fun getAllRepaymentsWithAllocations(): Flow<List<RepaymentWithAllocations>>

    @Transaction
    @Query("SELECT * FROM repayments ORDER BY timestamp DESC")
    fun getAllRepaymentsWithAllocationsSync(): List<RepaymentWithAllocations>

    @Transaction
    @Query("SELECT * FROM repayments WHERE memberId = :memberId AND groupId = :groupId ORDER BY timestamp DESC")
    fun getRepaymentsForMemberAndGroupSync(memberId: String, groupId: String): List<RepaymentWithAllocations>

    @Query("SELECT * FROM repayments WHERE linkedTransactionId = :transactionId LIMIT 1")
    fun getRepaymentForLinkedTransactionSync(transactionId: String): RepaymentEntity?

    @Query("SELECT COUNT(*) FROM repayments WHERE linkedTransactionId = :transactionId")
    fun isTransactionLinkedToRepaymentSync(transactionId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRepayment(repayment: RepaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAllocations(allocations: List<RepaymentAllocationEntity>)

    @Transaction
    fun saveRepaymentWithAllocations(repayment: RepaymentEntity, allocations: List<RepaymentAllocationEntity>) {
        insertRepayment(repayment)
        if (allocations.isNotEmpty()) {
            insertAllocations(allocations)
        }
    }

    @Delete
    fun deleteRepayment(repayment: RepaymentEntity)

    @Query("DELETE FROM repayments WHERE id = :id")
    fun deleteRepaymentById(id: String)
}
