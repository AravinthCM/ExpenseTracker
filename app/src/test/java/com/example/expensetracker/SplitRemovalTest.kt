package com.example.expensetracker

import com.example.expensetracker.data.backup.TestDatabaseHarness
import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitRemovalTest {

    @Test
    fun deletingSplitLeavesTransactionGroupAndMembersIntact() {
        val db = TestDatabaseHarness()
        val group = SplitGroupEntity(id = "group", name = "Trip")
        val me = SplitGroupMemberEntity(id = "me", groupId = group.id, name = "Me", isMe = true)
        val member = SplitGroupMemberEntity(id = "member", groupId = group.id, name = "Alex")
        val transaction = TransactionEntity(
            id = "tx", title = "Dinner", amount = 100.0, type = "EXPENSE", category = "Food",
            date = "2026-10-19", source = "MANUAL", timestamp = 1L
        )
        val split = ExpenseSplitEntity(
            id = "split", transactionId = transaction.id, groupId = group.id,
            amountPaise = 10000L, payerMemberId = me.id
        )

        db.transactionDao().insertTransaction(transaction)
        db.splitGroupDao().insertGroup(group)
        db.splitGroupDao().insertMembers(listOf(me, member))
        db.expenseSplitDao().insertSplit(split)
        db.expenseSplitDao().insertShares(
            listOf(
                ExpenseSplitShareEntity("share-me", split.id, me.id, 5000L),
                ExpenseSplitShareEntity("share-member", split.id, member.id, 5000L)
            )
        )

        db.expenseSplitDao().deleteSplitById(split.id)

        assertEquals(transaction, db.transactionDao().getTransactionByIdSync(transaction.id))
        assertTrue(db.expenseSplitDao().getAllSplitsWithSharesSync().isEmpty())
        assertEquals(1, db.splitGroupDao().getAllGroupsWithMembersSync().size)
        assertEquals(2, db.splitGroupDao().getAllGroupsWithMembersSync().single().members.size)
    }
}
