package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GPayRequestDao {

    @Query("SELECT * FROM gpay_requests ORDER BY postTime DESC")
    fun getAllRequests(): Flow<List<GPayRequestEntity>>

    @Query("SELECT * FROM gpay_requests ORDER BY postTime DESC")
    fun getAllRequestsSync(): List<GPayRequestEntity>

    @Query("SELECT * FROM gpay_requests WHERE requestKey = :requestKey LIMIT 1")
    fun findByRequestKeySync(requestKey: String): GPayRequestEntity?

    @Query("SELECT * FROM gpay_requests WHERE id = :id LIMIT 1")
    fun getByIdSync(id: String): GPayRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: GPayRequestEntity)

    @Query("DELETE FROM gpay_requests WHERE id = :id")
    fun deleteById(id: String)
}
