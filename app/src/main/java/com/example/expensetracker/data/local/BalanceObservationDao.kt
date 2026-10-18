package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceObservationDao {

    @Query("SELECT * FROM balance_observations ORDER BY timestamp DESC")
    fun getAllObservationsFlow(): Flow<List<BalanceObservationEntity>>

    @Query("SELECT * FROM balance_observations ORDER BY timestamp DESC")
    fun getAllObservations(): Flow<List<BalanceObservationEntity>>

    @Query("SELECT * FROM balance_observations ORDER BY timestamp DESC")
    fun getAllObservationsSync(): List<BalanceObservationEntity>

    @Query("SELECT * FROM balance_observations WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedObservationsFlow(): Flow<List<BalanceObservationEntity>>

    @Query("SELECT * FROM balance_observations WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedObservations(): Flow<List<BalanceObservationEntity>>

    @Query("SELECT * FROM balance_observations WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedObservationsSync(): List<BalanceObservationEntity>

    @Query("SELECT * FROM balance_observations WHERE accountId = :accountId ORDER BY timestamp ASC")
    fun getObservationsForAccountSync(accountId: String): List<BalanceObservationEntity>

    @Query("SELECT * FROM balance_observations WHERE accountId = :accountId ORDER BY timestamp ASC")
    fun getObservationsForAccountFlow(accountId: String): Flow<List<BalanceObservationEntity>>

    @Query("SELECT * FROM balance_observations WHERE id = :id LIMIT 1")
    fun getObservationByIdSync(id: String): BalanceObservationEntity?

    @Query("SELECT * FROM balance_observations WHERE id = :id LIMIT 1")
    fun getObservationById(id: String): BalanceObservationEntity?

    @Query("SELECT * FROM balance_observations WHERE referenceNumber = :referenceNumber AND balancePaise = :balancePaise LIMIT 1")
    fun findDuplicateByReference(referenceNumber: String, balancePaise: Long): BalanceObservationEntity?

    @Query("SELECT * FROM balance_observations WHERE referenceNumber = :referenceNumber AND balancePaise = :balancePaise LIMIT 1")
    fun findDuplicateByReferenceSync(referenceNumber: String, balancePaise: Long): BalanceObservationEntity?

    @Query("SELECT * FROM balance_observations WHERE source = :source AND balancePaise = :balancePaise AND timestamp BETWEEN :windowStart AND :windowEnd LIMIT 1")
    fun findDuplicateByTimestampSync(source: String, balancePaise: Long, windowStart: Long, windowEnd: Long): BalanceObservationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(observation: BalanceObservationEntity): Long

    @Update
    fun update(observation: BalanceObservationEntity)

    @Delete
    fun delete(observation: BalanceObservationEntity)

    @Query("DELETE FROM balance_observations WHERE id = :id")
    fun deleteById(id: String)

    @Query("UPDATE balance_observations SET accountId = :accountId, isResolved = 1 WHERE id = :id")
    fun resolveObservation(id: String, accountId: String)

    @Query("UPDATE balance_observations SET isReconciled = 1, reconciledAt = :reconciledAt WHERE id = :id")
    fun markReconciled(id: String, reconciledAt: Long = System.currentTimeMillis())
}
