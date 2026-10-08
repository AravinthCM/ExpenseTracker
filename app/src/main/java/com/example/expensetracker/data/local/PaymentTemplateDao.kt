package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentTemplateDao {

    @Query("SELECT * FROM payment_templates ORDER BY updatedAt DESC")
    fun getAllTemplatesFlow(): Flow<List<PaymentTemplateEntity>>

    @Query("SELECT * FROM payment_templates ORDER BY updatedAt DESC")
    fun getAllTemplatesSync(): List<PaymentTemplateEntity>

    @Query("SELECT * FROM payment_templates WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun getEnabledTemplatesSync(): List<PaymentTemplateEntity>

    @Query("SELECT * FROM payment_templates WHERE id = :id LIMIT 1")
    fun getTemplateById(id: String): PaymentTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(template: PaymentTemplateEntity)

    @Update
    fun update(template: PaymentTemplateEntity)

    @Delete
    fun delete(template: PaymentTemplateEntity)

    @Query("DELETE FROM payment_templates WHERE id = :id")
    fun deleteById(id: String)
}
