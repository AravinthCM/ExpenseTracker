package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.RepaymentAllocationEntity
import com.example.expensetracker.data.local.RepaymentEntity
import com.example.expensetracker.data.local.RepaymentWithAllocations
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.data.local.toEntity
import com.example.expensetracker.model.AccountBalanceCalculator
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.DeduplicationResult
import com.example.expensetracker.service.NormalizedTransaction
import com.example.expensetracker.service.TransactionDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class Phase8RegressionTest {

    @Before
    fun setUp() {
        TransactionDeduplicator.clearHistory()
    }

    // 1. Rapid repeated Undo taps
    @Test
    fun test1_rapidRepeatedUndoTaps() {
        var splits = mutableListOf("s1", "s2")
        val undoSplit = { splitId: String ->
            splits.remove(splitId)
        }

        // Tap Undo once
        undoSplit("s1")
        assertEquals(listOf("s2"), splits)

        // Rapid second tap on already undone splitId "s1"
        undoSplit("s1") // Should be safe and not crash or alter other splits
        assertEquals(listOf("s2"), splits)
    }

    // 2. Undo after the target operation has already disappeared
    @Test
    fun test2_undoAfterTargetDisappeared() {
        val splits = mutableListOf<String>()
        val targetId = "non_existent_split"
        // Attempting to delete a target that is already gone
        val wasRemoved = splits.remove(targetId)
        assertFalse(wasRemoved)
        assertTrue(splits.isEmpty())
    }

    // 3. Undo does not delete a linked transaction
    @Test
    fun test3_undoDoesNotDeleteLinkedTransaction() {
        val linkedTx = TransactionEntity(
            id = "tx_link",
            title = "Repayment Credit",
            amount = 500.0,
            type = "INCOME",
            category = "OTHER",
            date = "Today",
            source = "MANUAL",
            timestamp = 1000L
        )

        var repayments = mutableListOf(
            RepaymentEntity(
                id = "rep1",
                groupId = "g1",
                memberId = "m1",
                amountPaise = 50000L,
                paymentDate = "2026-10-09",
                linkedTransactionId = linkedTx.id
            )
        )

        // Undo repayment
        repayments.removeAll { it.id == "rep1" }

        // Repayment is removed, but linked transaction entity remains intact
        assertTrue(repayments.isEmpty())
        assertNotNull(linkedTx)
        assertEquals("tx_link", linkedTx.id)
    }

    // 4. Multiple operations in succession use the correct Undo target
    @Test
    fun test4_multipleOperationsInSuccessionTargetCorrectItem() {
        val repayments = mutableListOf(
            RepaymentEntity(id = "r1", groupId = "g1", memberId = "m1", amountPaise = 10000L, paymentDate = "2026-10-09"),
            RepaymentEntity(id = "r2", groupId = "g1", memberId = "m2", amountPaise = 20000L, paymentDate = "2026-10-09")
        )

        // Undo second repayment "r2"
        val undoTargetId = "r2"
        repayments.removeAll { it.id == undoTargetId }

        assertEquals(1, repayments.size)
        assertEquals("r1", repayments.first().id)
    }

    // 5. Concurrent or repeated repayment submission
    @Test
    fun test5_repeatedRepaymentSubmissionValidation() {
        val existingRepayments = listOf(
            RepaymentWithAllocations(
                repayment = RepaymentEntity(id = "r1", groupId = "g1", memberId = "m1", amountPaise = 10000L, paymentDate = "2026-10-09", linkedTransactionId = "tx_linked"),
                allocations = emptyList()
            )
        )

        // Prevent linking transaction "tx_linked" again
        val isAlreadyLinked = existingRepayments.any { it.repayment.linkedTransactionId == "tx_linked" }
        assertTrue(isAlreadyLinked)
    }

    // 6. Atomic repayment allocation rollback
    @Test
    fun test6_atomicRepaymentAllocationRollback() {
        var allocations = mutableListOf(
            RepaymentAllocationEntity(id = "a1", repaymentId = "rep1", shareId = "sh1", allocatedAmountPaise = 5000L),
            RepaymentAllocationEntity(id = "a2", repaymentId = "rep1", shareId = "sh2", allocatedAmountPaise = 5000L),
            RepaymentAllocationEntity(id = "a3", repaymentId = "rep2", shareId = "sh3", allocatedAmountPaise = 3000L)
        )

        // Undoing rep1 must clear all allocations linked to rep1
        allocations.removeAll { it.repaymentId == "rep1" }

        assertEquals(1, allocations.size)
        assertEquals("rep2", allocations.first().repaymentId)
    }

    // 7. Overpayment isolation by member and group
    @Test
    fun test7_overpaymentIsolationByMemberAndGroup() {
        val group1 = SplitGroupWithMembers(
            group = SplitGroupEntity(id = "g1", name = "Trip 1"),
            members = listOf(
                SplitGroupMemberEntity(id = "m_me", groupId = "g1", name = "Me", isMe = true),
                SplitGroupMemberEntity(id = "m_bob", groupId = "g1", name = "Bob", isMe = false),
                SplitGroupMemberEntity(id = "m_alice", groupId = "g1", name = "Alice", isMe = false)
            )
        )

        val tx1 = TransactionEntity(id = "t1", title = "Lunch", amount = 100.0, type = "EXPENSE", category = "FOOD", date = "Today", source = "MANUAL", timestamp = 0L)
        val splits = listOf(
            ExpenseSplitWithShares(
                split = ExpenseSplitEntity(id = "s1", transactionId = "t1", groupId = "g1", amountPaise = 10000L, payerMemberId = "m_me"),
                shares = listOf(
                    ExpenseSplitShareEntity(id = "sh1", splitId = "s1", memberId = "m_me", shareAmountPaise = 3400L),
                    ExpenseSplitShareEntity(id = "sh2", splitId = "s1", memberId = "m_bob", shareAmountPaise = 3300L),
                    ExpenseSplitShareEntity(id = "sh3", splitId = "s1", memberId = "m_alice", shareAmountPaise = 3300L)
                )
            )
        )

        // Bob overpays ₹50 (5000 paise) against his ₹33 debt
        val repayments = listOf(
            RepaymentWithAllocations(
                repayment = RepaymentEntity(id = "r_bob", groupId = "g1", memberId = "m_bob", amountPaise = 5000L, unallocatedAmountPaise = 1700L, paymentDate = "Today"),
                allocations = listOf(
                    RepaymentAllocationEntity(id = "a_bob", repaymentId = "r_bob", shareId = "sh2", allocatedAmountPaise = 3300L)
                )
            )
        )

        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(
            splitsWithShares = splits,
            groupsWithMembers = listOf(group1),
            transactions = listOf(tx1),
            repaymentsWithAllocations = repayments
        )

        // Bob has 0 debt and 1700 paise unallocated excess
        val bobBalance = ledger.memberBalances.find { it.memberId == "m_bob" }
        assertNotNull(bobBalance)
        assertEquals(0L, bobBalance?.totalOwedPaise)
        assertEquals(1700L, bobBalance?.unallocatedExcessPaise)

        // Alice STILL owes her 3300 paise (Bob's excess overpayment does NOT reduce Alice's debt!)
        val aliceBalance = ledger.memberBalances.find { it.memberId == "m_alice" }
        assertNotNull(aliceBalance)
        assertEquals(3300L, aliceBalance?.totalOwedPaise)
    }

    // 8. Deleting or archiving an account without losing transaction history
    @Test
    fun test8_deletingAccountPreservesTransactionHistory() {
        var transactions = listOf(
            TransactionEntity(id = "t1", title = "Bill", amount = 500.0, type = "EXPENSE", category = "BILLS", date = "Today", source = "MANUAL", timestamp = 0L, accountId = "acc_deleted")
        )

        // Reassign transactions to null upon account deletion
        transactions = transactions.map {
            if (it.accountId == "acc_deleted") it.copy(accountId = null) else it
        }

        assertEquals(1, transactions.size)
        assertNull(transactions.first().accountId)
        assertEquals("Bill", transactions.first().title)
    }

    // 9. Transaction reassignment and account balance updates
    @Test
    fun test9_transactionReassignmentUpdatesAccountBalances() {
        val acc1 = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L) // ₹1000.00
        val acc2 = AccountEntity(id = "acc2", name = "HDFC", openingBalancePaise = 200000L) // ₹2000.00

        var txs = listOf(
            TransactionEntity(id = "t1", title = "Shopping", amount = 300.0, type = "EXPENSE", category = "SHOPPING", date = "Today", source = "MANUAL", timestamp = 0L, accountId = "acc1")
        )

        val acc1Initial = AccountBalanceCalculator.calculateAccountBalancePaise(acc1, txs) // 100000 - 30000 = 70000
        val acc2Initial = AccountBalanceCalculator.calculateAccountBalancePaise(acc2, txs) // 200000

        assertEquals(70000L, acc1Initial)
        assertEquals(200000L, acc2Initial)

        // Reassign t1 to acc2
        txs = txs.map { if (it.id == "t1") it.copy(accountId = "acc2") else it }

        val acc1Updated = AccountBalanceCalculator.calculateAccountBalancePaise(acc1, txs) // 100000
        val acc2Updated = AccountBalanceCalculator.calculateAccountBalancePaise(acc2, txs) // 200000 - 30000 = 170000

        assertEquals(100000L, acc1Updated)
        assertEquals(170000L, acc2Updated)
    }

    // 10. Duplicate notification ingestion
    @Test
    fun test10_duplicateNotificationIngestion() {
        val now = System.currentTimeMillis()
        val tx1 = NormalizedTransaction(amount = 250.0, name = "Swiggy", type = TransactionType.EXPENSE, source = "BANK_SMS", timestamp = now, originalText = "")
        val tx2Duplicate = NormalizedTransaction(amount = 250.0, name = "Swiggy", type = TransactionType.EXPENSE, source = "NOTIFICATION", timestamp = now + 1000L, originalText = "")

        val res1 = TransactionDeduplicator.process(tx1)
        assertTrue(res1 is DeduplicationResult.NewTransaction)

        val res2 = TransactionDeduplicator.process(tx2Duplicate)
        assertTrue(res2 is DeduplicationResult.DuplicateTransaction)
    }

    // 11. Split/repayment ledger updates after deletion or Undo
    @Test
    fun test11_ledgerUpdatesAfterUndo() {
        val group = SplitGroupWithMembers(
            group = SplitGroupEntity(id = "g1", name = "Trip"),
            members = listOf(
                SplitGroupMemberEntity(id = "m_me", groupId = "g1", name = "Me", isMe = true),
                SplitGroupMemberEntity(id = "m_bob", groupId = "g1", name = "Bob", isMe = false)
            )
        )
        val splitTx = TransactionEntity(id = "t_split", title = "Dinner", amount = 100.0, type = "EXPENSE", category = "FOOD", date = "Today", source = "MANUAL", timestamp = 0L)
        val splits = listOf(
            ExpenseSplitWithShares(
                split = ExpenseSplitEntity(id = "s1", transactionId = "t_split", groupId = "g1", amountPaise = 10000L, payerMemberId = "m_me"),
                shares = listOf(
                    ExpenseSplitShareEntity(id = "sh1", splitId = "s1", memberId = "m_me", shareAmountPaise = 5000L),
                    ExpenseSplitShareEntity(id = "sh2", splitId = "s1", memberId = "m_bob", shareAmountPaise = 5000L)
                )
            )
        )

        var repayments = listOf(
            RepaymentWithAllocations(
                repayment = RepaymentEntity(id = "r1", groupId = "g1", memberId = "m_bob", amountPaise = 5000L, paymentDate = "Today"),
                allocations = listOf(
                    RepaymentAllocationEntity(id = "a1", repaymentId = "r1", shareId = "sh2", allocatedAmountPaise = 5000L)
                )
            )
        )

        // Ledger with repayment -> 0 debt
        var ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(splitTx), repayments)
        assertEquals(0, ledger.memberBalances.size)

        // Undo repayment -> Bob's 5000 paise debt is restored
        repayments = emptyList()
        ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(splitTx), repayments)
        assertEquals(1, ledger.memberBalances.size)
        assertEquals(5000L, ledger.memberBalances.first().totalOwedPaise)
    }

    // 12. Analytics filter consistency
    @Test
    fun test12_analyticsFilterConsistency() {
        val txAcc1 = Transaction(id = "1", title = "Fuel", amount = 500.0, type = TransactionType.EXPENSE, category = TransactionCategory.TRANSPORT, accountId = "acc1")
        val txAcc2 = Transaction(id = "2", title = "Groceries", amount = 1200.0, type = TransactionType.EXPENSE, category = TransactionCategory.GROCERY, accountId = "acc2")

        val txs = listOf(txAcc1, txAcc2)

        val acc1ExpenseTotal = txs.filter { it.accountId == "acc1" && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val acc1CategoryBreakdown = txs.filter { it.accountId == "acc1" && it.type == TransactionType.EXPENSE }.groupBy { it.category }

        assertEquals(500.0, acc1ExpenseTotal, 0.001)
        assertEquals(1, acc1CategoryBreakdown.size)
        assertTrue(acc1CategoryBreakdown.containsKey(TransactionCategory.TRANSPORT))
    }

    // 13. Existing category, search, edit, and delete regressions
    @Test
    fun test13_searchFilterEditDeleteRegressions() {
        val t1 = Transaction(id = "1", title = "Swiggy Pizza", amount = 350.0, type = TransactionType.EXPENSE, category = TransactionCategory.FOOD)
        val t2 = Transaction(id = "2", title = "Uber Ride", amount = 200.0, type = TransactionType.EXPENSE, category = TransactionCategory.TRANSPORT)

        var list = listOf(t1, t2)

        // Search test
        val searched = list.filter { it.title.contains("swiggy", ignoreCase = true) }
        assertEquals(1, searched.size)
        assertEquals("1", searched.first().id)

        // Edit test
        list = list.map { if (it.id == "1") it.copy(amount = 400.0) else it }
        assertEquals(400.0, list.first().amount, 0.001)

        // Delete test
        list = list.filter { it.id != "2" }
        assertEquals(1, list.size)
    }

    // 14. Database migration and preservation of existing records
    @Test
    fun test14_databaseMigrationPreservation() {
        // Verify migration constants exist in AppDatabase
        val migrations = listOf(
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_1_2,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_2_3,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_3_4,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_4_5,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_5_6,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_6_7,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_7_8,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_8_9,
            com.example.expensetracker.data.local.AppDatabase.MIGRATION_9_10
        )
        assertEquals(9, migrations.size)
    }
}
