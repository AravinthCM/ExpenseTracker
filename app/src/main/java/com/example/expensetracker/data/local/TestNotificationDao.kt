package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TestNotificationDao {

    @Query("SELECT * FROM test_notifications ORDER BY receivedAt DESC")
    fun getAllTestNotifications(): Flow<List<TestNotificationEntity>>

    @Query("SELECT * FROM test_notifications ORDER BY receivedAt DESC")
    fun getAllTestNotificationsSync(): List<TestNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: TestNotificationEntity)

    @Query("DELETE FROM test_notifications")
    fun clearAll()

    @Query("DELETE FROM test_notifications WHERE id NOT IN (SELECT id FROM test_notifications ORDER BY receivedAt DESC LIMIT 500)")
    fun pruneOldRecords()
}
