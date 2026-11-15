package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GPayMemberMappingDao {

    @Query("SELECT * FROM gpay_member_mappings")
    fun getAllMappings(): Flow<List<GPayMemberMappingEntity>>

    @Query("SELECT * FROM gpay_member_mappings")
    fun getAllMappingsSync(): List<GPayMemberMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: GPayMemberMappingEntity)

    @Query("DELETE FROM gpay_member_mappings WHERE id = :id")
    fun deleteById(id: String)
}
