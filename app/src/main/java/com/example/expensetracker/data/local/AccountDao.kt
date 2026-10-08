package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isPrimary DESC, name ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isPrimary DESC, name ASC")
    fun getAllAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY isPrimary DESC, name ASC")
    fun getActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY isPrimary DESC, name ASC")
    fun getActiveAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    fun getAccountByIdSync(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isPrimary = 1 AND isArchived = 0 LIMIT 1")
    fun getPrimaryAccountSync(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isPrimary = 1 AND isArchived = 0 LIMIT 1")
    fun getPrimaryAccountFlow(): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAccount(account: AccountEntity)

    @Update
    fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    fun deleteAccountById(id: String)

    @Query("UPDATE accounts SET isPrimary = 0 WHERE isPrimary = 1")
    fun clearPrimaryFlag()

    @Query("UPDATE accounts SET isPrimary = 1 WHERE id = :accountId")
    fun setPrimaryFlag(accountId: String)

    @Transaction
    fun setPrimaryAccount(accountId: String) {
        clearPrimaryFlag()
        setPrimaryFlag(accountId)
    }

    @Query("UPDATE accounts SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :accountId")
    fun setArchivedStatus(accountId: String, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())
}
