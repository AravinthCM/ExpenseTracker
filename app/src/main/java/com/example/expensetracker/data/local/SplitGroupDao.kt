package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitGroupDao {

    @Transaction
    @Query("SELECT * FROM split_groups ORDER BY updatedAt DESC")
    fun getAllGroupsWithMembers(): Flow<List<SplitGroupWithMembers>>

    @Transaction
    @Query("SELECT * FROM split_groups ORDER BY updatedAt DESC")
    fun getAllGroupsWithMembersSync(): List<SplitGroupWithMembers>

    @Transaction
    @Query("SELECT * FROM split_groups WHERE id = :id")
    fun getGroupWithMembersByIdSync(id: String): SplitGroupWithMembers?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: SplitGroupEntity)

    @Update
    fun updateGroup(group: SplitGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMembers(members: List<SplitGroupMemberEntity>)

    @Query("DELETE FROM split_group_members WHERE groupId = :groupId")
    fun deleteMembersForGroup(groupId: String)

    @Transaction
    fun saveGroupWithMembers(group: SplitGroupEntity, members: List<SplitGroupMemberEntity>) {
        insertGroup(group)
        deleteMembersForGroup(group.id)
        val updatedMembers = members.map { it.copy(groupId = group.id) }
        insertMembers(updatedMembers)
    }

    @Delete
    fun deleteGroup(group: SplitGroupEntity)

    @Query("DELETE FROM split_groups WHERE id = :id")
    fun deleteGroupById(id: String)
}
