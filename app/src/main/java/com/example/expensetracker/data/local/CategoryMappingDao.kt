package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryMappingDao {

    @Query("SELECT * FROM category_mappings ORDER BY priority ASC, id ASC")
    fun getAllMappings(): Flow<List<CategoryMappingEntity>>

    @Query("SELECT * FROM category_mappings ORDER BY priority ASC, id ASC")
    fun getAllMappingsSync(): List<CategoryMappingEntity>

    @Query("SELECT * FROM category_mappings WHERE pattern = :pattern LIMIT 1")
    fun getMappingByPatternSync(pattern: String): CategoryMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMapping(mapping: CategoryMappingEntity)

    @Update
    fun updateMapping(mapping: CategoryMappingEntity)

    @Delete
    fun deleteMapping(mapping: CategoryMappingEntity)

    @Query("UPDATE category_mappings SET categoryName = :newCategory WHERE categoryName = :oldCategory")
    fun updateCategoryForMappings(oldCategory: String, newCategory: String)
}
