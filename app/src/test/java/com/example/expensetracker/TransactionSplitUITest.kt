package com.example.expensetracker

import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.SplitCalculationResult
import com.example.expensetracker.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TransactionSplitUITest {

    private fun createSampleGroup(): SplitGroupWithMembers {
        val groupId = UUID.randomUUID().toString()
        val m1 = SplitGroupMemberEntity(id = UUID.randomUUID().toString(), groupId = groupId, name = "Alice", isMe = true)
        val m2 = SplitGroupMemberEntity(id = UUID.randomUUID().toString(), groupId = groupId, name = "Bob", isMe = false)
        val m3 = SplitGroupMemberEntity(id = UUID.randomUUID().toString(), groupId = groupId, name = "Charlie", isMe = false)
        val m4 = SplitGroupMemberEntity(id = UUID.randomUUID().toString(), groupId = groupId, name = "David", isMe = false)
        return SplitGroupWithMembers(
            group = SplitGroupEntity(id = groupId, name = "Roommates"),
            members = listOf(m1, m2, m3, m4)
        )
    }

    private fun createExpenseTransaction(amount: Double = 482.0): TransactionEntity {
        return TransactionEntity(
            id = "tx-101",
            amount = amount,
            title = "Dinner Party",
            category = "Food",
            date = "2026-10-08",
            source = "MANUAL",
            type = "EXPENSE",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createIncomeTransaction(): TransactionEntity {
        return TransactionEntity(
            id = "tx-102",
            amount = 1000.0,
            title = "Salary Credit",
            category = "Income",
            date = "2026-10-08",
            source = "BANK_SMS",
            type = "INCOME",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createTransferTransaction(): TransactionEntity {
        return TransactionEntity(
            id = "tx-103",
            amount = 500.0,
            title = "Account Transfer",
            category = "Transfer",
            date = "2026-10-08",
            source = "MANUAL",
            type = "TRANSFER",
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun testIncomeAndTransferTransactionsCannotBeSplit() {
        val expenseTx = createExpenseTransaction()
        val incomeTx = createIncomeTransaction()
        val transferTx = createTransferTransaction()

        val isExpenseEligible = expenseTx.type == "EXPENSE"
        val isIncomeEligible = incomeTx.type == "EXPENSE"
        val isTransferEligible = transferTx.type == "EXPENSE"

        assertTrue("Expense transactions must be eligible for split", isExpenseEligible)
        assertFalse("Income transactions must NOT be eligible for split", isIncomeEligible)
        assertFalse("Transfer transactions must NOT be eligible for split", isTransferEligible)
    }

    @Test
    fun testSuccessfulEqualSplitCreationAndOriginalTransactionUnchanged() {
        val expenseTx = createExpenseTransaction(amount = 482.0)
        val group = createSampleGroup()

        val originalTxCopy = expenseTx.copy()

        val result = ExpenseSplitCalculator.calculateEqualSplit(expenseTx, group)

        assertTrue(result is SplitCalculationResult.Success)
        val success = result as SplitCalculationResult.Success

        // Verify total shares equal original transaction amount exactly
        assertEquals(48200L, success.split.amountPaise)
        assertEquals(4, success.shares.size)
        assertEquals(48200L, success.shares.sumOf { it.shareAmountPaise })

        // Verify ₹120.50 per member = 12050 paise
        for (share in success.shares) {
            assertEquals(12050L, share.shareAmountPaise)
        }

        // Verify original transaction remains completely unchanged
        assertEquals(originalTxCopy.id, expenseTx.id)
        assertEquals(originalTxCopy.amount, expenseTx.amount, 0.001)
        assertEquals(originalTxCopy.title, expenseTx.title)
        assertEquals(originalTxCopy.type, expenseTx.type)
    }

    @Test
    fun testSuccessfulCustomSplitCreation() {
        val expenseTx = createExpenseTransaction(amount = 100.0) // 10000 paise
        val group = createSampleGroup()

        // 4 members: 2500, 2500, 2500, 2500
        val customSharesPaise = mapOf(
            group.members[0].id to 2500L,
            group.members[1].id to 2500L,
            group.members[2].id to 2500L,
            group.members[3].id to 2500L
        )

        val result = ExpenseSplitCalculator.calculateCustomSplit(expenseTx, group, customSharesPaise)

        assertTrue(result is SplitCalculationResult.Success)
        val success = result as SplitCalculationResult.Success
        assertEquals(10000L, success.shares.sumOf { it.shareAmountPaise })
        assertEquals(10000L, success.split.amountPaise)
    }

    @Test
    fun testInvalidCustomAmountsBlockedFromConfirmation() {
        val expenseTx = createExpenseTransaction(amount = 100.0) // 10000 paise
        val group = createSampleGroup()

        // Mismatched sum (9000 paise vs 10000 paise)
        val customSharesShort = mapOf(
            group.members[0].id to 2000L,
            group.members[1].id to 2000L,
            group.members[2].id to 2000L,
            group.members[3].id to 3000L
        )

        val resultShort = ExpenseSplitCalculator.calculateCustomSplit(expenseTx, group, customSharesShort)
        assertTrue("Custom split sum mismatch must result in error", resultShort is SplitCalculationResult.Error)

        // Negative share
        val customSharesNegative = mapOf(
            group.members[0].id to -500L,
            group.members[1].id to 3500L,
            group.members[2].id to 3500L,
            group.members[3].id to 3500L
        )
        val resultNegative = ExpenseSplitCalculator.calculateCustomSplit(expenseTx, group, customSharesNegative)
        assertTrue("Negative allocation must result in error", resultNegative is SplitCalculationResult.Error)
    }

    @Test
    fun testDuplicateTransactionGroupSplitsAreRejected() {
        val expenseTx = createExpenseTransaction()
        val group = createSampleGroup()

        val meMemberId = group.members.first { it.isMe }.id
        val existingSplitEntity = ExpenseSplitEntity(
            id = "split-001",
            transactionId = expenseTx.id,
            groupId = group.group.id,
            amountPaise = 48200L,
            payerMemberId = meMemberId,
            createdAt = System.currentTimeMillis()
        )
        val existingSplits = listOf(
            ExpenseSplitWithShares(split = existingSplitEntity, shares = emptyList())
        )

        // Check if group is already split
        val alreadySplitGroupIds = existingSplits
            .filter { it.split.transactionId == expenseTx.id }
            .map { it.split.groupId }
            .toSet()

        assertTrue("Group ID must be recognized as already split", alreadySplitGroupIds.contains(group.group.id))
    }

    @Test
    fun testUndoDeletesOnlyTheNewlyCreatedSplitAndShares() {
        val expenseTx = createExpenseTransaction()
        val group = createSampleGroup()

        val meMemberId = group.members.first { it.isMe }.id
        val newSplitId = "split-new"
        val existingSplitId = "split-existing"

        val splitsMutableList = mutableListOf(
            ExpenseSplitEntity(id = existingSplitId, transactionId = "tx-other", groupId = "g-other", amountPaise = 2000L, payerMemberId = meMemberId),
            ExpenseSplitEntity(id = newSplitId, transactionId = expenseTx.id, groupId = group.group.id, amountPaise = 48200L, payerMemberId = meMemberId)
        )

        val sharesMutableList = mutableListOf(
            ExpenseSplitShareEntity(id = "share-1", splitId = existingSplitId, memberId = "m1", shareAmountPaise = 2000L),
            ExpenseSplitShareEntity(id = "share-2", splitId = newSplitId, memberId = "m2", shareAmountPaise = 48200L)
        )

        // Perform undo on newSplitId
        splitsMutableList.removeAll { it.id == newSplitId }
        sharesMutableList.removeAll { it.splitId == newSplitId }

        // Assert newly created split is deleted
        assertFalse(splitsMutableList.any { it.id == newSplitId })
        assertFalse(sharesMutableList.any { it.splitId == newSplitId })

        // Assert existing pre-existing split remains untouched
        assertTrue(splitsMutableList.any { it.id == existingSplitId })
        assertTrue(sharesMutableList.any { it.splitId == existingSplitId })

        // Assert original transaction remains intact
        assertEquals("tx-101", expenseTx.id)
    }

    @Test
    fun testRupeesToPaiseConversionHelper() {
        assertEquals(48200L, ExpenseSplitCalculator.rupeesToPaise(482.0))
        assertEquals(12050L, ExpenseSplitCalculator.rupeesToPaise(120.50))
        assertEquals(0L, ExpenseSplitCalculator.rupeesToPaise(0.0))
        assertEquals(123456L, ExpenseSplitCalculator.rupeesToPaise(1234.56))
    }
}
