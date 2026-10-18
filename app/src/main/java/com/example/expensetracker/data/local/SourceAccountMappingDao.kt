package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceAccountMappingDao {

    @Query("SELECT * FROM source_account_mappings ORDER BY updatedAt DESC")
    fun getAllMappingsFlow(): Flow<List<SourceAccountMappingEntity>>

    @Query("SELECT * FROM source_account_mappings ORDER BY updatedAt DESC")
    fun getAllMappings(): Flow<List<SourceAccountMappingEntity>>

    @Query("SELECT * FROM source_account_mappings ORDER BY updatedAt DESC")
    fun getAllMappingsSync(): List<SourceAccountMappingEntity>

    @Query("SELECT * FROM source_account_mappings WHERE isConfirmed = 1 ORDER BY updatedAt DESC")
    fun getConfirmedMappingsSync(): List<SourceAccountMappingEntity>

    @Query("SELECT * FROM source_account_mappings WHERE accountId = :accountId")
    fun getMappingsForAccountSync(accountId: String): List<SourceAccountMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMapping(mapping: SourceAccountMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(mapping: SourceAccountMappingEntity)

    @Update
    fun updateMapping(mapping: SourceAccountMappingEntity)

    @Delete
    fun deleteMapping(mapping: SourceAccountMappingEntity)

    @Query("DELETE FROM source_account_mappings WHERE id = :id")
    fun deleteMappingById(id: String)

    @Query("DELETE FROM source_account_mappings WHERE id = :id")
    fun deleteById(id: String)
}
