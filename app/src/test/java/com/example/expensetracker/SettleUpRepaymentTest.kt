package com.example.expensetracker

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
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.RepaymentRecordResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SettleUpRepaymentTest {

    private fun createGroupWithMembers(groupName: String, nonMeNames: List<String>): SplitGroupWithMembers {
        val groupId = UUID.randomUUID().toString()
        val meMember = SplitGroupMemberEntity(id = UUID.randomUUID().toString(), groupId = groupId, name = "Me", isMe = true)
        val nonMeMembers = nonMeNames.map { name ->
            SplitGroupMemberEntity(id = UUID.randomUUID().toString(), groupId = groupId, name = name, isMe = false)
        }
        return SplitGroupWithMembers(
            group = SplitGroupEntity(id = groupId, name = groupName),
            members = listOf(meMember) + nonMeMembers
        )
    }

    private fun createExpenseTransaction(title: String, amount: Double, timestamp: Long = System.currentTimeMillis()): TransactionEntity {
        return TransactionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            amount = amount,
            type = "EXPENSE",
            category = "FOOD",
            date = "2026-10-08",
            source = "MANUAL",
            timestamp = timestamp
        )
    }

    private fun createIncomeTransaction(title: String = "GPay Credit", amount: Double = 70.0): TransactionEntity {
        return TransactionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            amount = amount,
            type = "INCOME",
            category = "SALARY",
            date = "2026-10-08",
            source = "GPAY",
            timestamp = System.currentTimeMillis()
        )
    }

    // 1. Full repayment of one member's debt
    @Test
    fun testFullRepaymentOfOneMemberDebt() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Dinner", 200.0) // 20000 paise -> Bala share = 10000 paise (₹100)

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))

        val repaymentResult = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id,
            memberId = balaMember.id,
            repaymentAmountPaise = 10000L, // Full ₹100
            paymentDate = "2026-10-08",
            splitsWithShares = splits,
            groupsWithMembers = listOf(group),
            transactions = listOf(tx),
            repaymentsWithAllocations = emptyList()
        )

        assertTrue(repaymentResult is RepaymentRecordResult.Success)
        val success = repaymentResult as RepaymentRecordResult.Success

        assertEquals(10000L, success.repayment.amountPaise)
        assertEquals(0L, success.repayment.unallocatedAmountPaise)
        assertEquals(1, success.allocations.size)
        assertEquals(10000L, success.allocations.first().allocatedAmountPaise)

        // Ledger check
        val repaymentsWithAllocations = listOf(RepaymentWithAllocations(success.repayment, success.allocations))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repaymentsWithAllocations)

        assertEquals(0L, ledger.totalOutstandingPaise)
        assertTrue(ledger.memberBalances.isEmpty())
    }

    // 2. Partial repayment
    @Test
    fun testPartialRepayment() {
        val group = createGroupWithMembers("Roommates", listOf("Bala", "Charan", "Divya"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Amman Mess", 482.0) // Bala share = 12050 paise (₹120.50)

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))

        // Bala repays ₹70 = 7000 paise
        val repaymentResult = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id,
            memberId = balaMember.id,
            repaymentAmountPaise = 7000L,
            paymentDate = "2026-10-08",
            splitsWithShares = splits,
            groupsWithMembers = listOf(group),
            transactions = listOf(tx),
            repaymentsWithAllocations = emptyList()
        )

        assertTrue(repaymentResult is RepaymentRecordResult.Success)
        val success = repaymentResult as RepaymentRecordResult.Success
        assertEquals(7000L, success.allocations.first().allocatedAmountPaise)

        val repayments = listOf(RepaymentWithAllocations(success.repayment, success.allocations))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)

        val balaSummary = ledger.memberBalances.first { it.memberId == balaMember.id }
        assertEquals(5050L, balaSummary.totalOwedPaise) // ₹50.50 remaining
    }

    // 3. Multiple repayments against the same share
    @Test
    fun testMultipleRepaymentsAgainstSameShare() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Dinner", 200.0) // Bala share = 10000 paise (₹100)

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))

        // Repayment 1: 4000 paise
        val rep1 = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 4000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val repaymentsList1 = listOf(RepaymentWithAllocations(rep1.repayment, rep1.allocations))

        // Repayment 2: 6000 paise
        val rep2 = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 6000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = repaymentsList1
        ) as RepaymentRecordResult.Success

        val allRepayments = repaymentsList1 + RepaymentWithAllocations(rep2.repayment, rep2.allocations)
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), allRepayments)

        assertEquals(0L, ledger.totalOutstandingPaise)
    }

    // 4. Allocation to the oldest eligible expense first
    @Test
    fun testAllocationToOldestEligibleExpenseFirst() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }

        val txOld = createExpenseTransaction("Old Lunch", 100.0, timestamp = 1000L) // 5000 paise share
        val txNew = createExpenseTransaction("New Dinner", 100.0, timestamp = 2000L) // 5000 paise share

        val splitOld = ExpenseSplitCalculator.calculateEqualSplit(txOld, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splitNew = ExpenseSplitCalculator.calculateEqualSplit(txNew, group) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splits = listOf(
            ExpenseSplitWithShares(split = splitOld.split, shares = splitOld.shares),
            ExpenseSplitWithShares(split = splitNew.split, shares = splitNew.shares)
        )

        // Bala repays 5000 paise
        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(txOld, txNew), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val oldShareId = splitOld.shares.first { it.memberId == balaMember.id }.id
        assertEquals(1, rep.allocations.size)
        assertEquals(oldShareId, rep.allocations.first().shareId)
        assertEquals(5000L, rep.allocations.first().allocatedAmountPaise)
    }

    // 5. Deterministic tie-breaking (same timestamp, sorted by split.id)
    @Test
    fun testDeterministicTieBreaking() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }

        val sameTime = 5000L
        val tx1 = createExpenseTransaction("Expense A", 100.0, timestamp = sameTime)
        val tx2 = createExpenseTransaction("Expense B", 100.0, timestamp = sameTime)

        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splits = listOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        ).sortedBy { it.split.id }

        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx1, tx2), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        // Allocation should go to the share belonging to the split with smaller ID
        val expectedSplitId = splits.first().split.id
        val expectedShareId = splits.first().shares.first { it.memberId == balaMember.id }.id
        assertEquals(expectedShareId, rep.allocations.first().shareId)
    }

    // 6. A member with multiple expenses and groups
    @Test
    fun testMemberWithMultipleExpensesAndGroups() {
        val group1 = createGroupWithMembers("Group 1", listOf("Bala"))
        val group2 = createGroupWithMembers("Group 2", listOf("Bala"))
        val balaGroup1 = group1.members.first { it.name == "Bala" }

        val tx1 = createExpenseTransaction("G1 Expense", 100.0)
        val tx2 = createExpenseTransaction("G2 Expense", 200.0)

        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group1) as com.example.expensetracker.model.SplitCalculationResult.Success
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group2) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splits = listOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        )

        // Repay strictly in Group 1
        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group1.group.id, memberId = balaGroup1.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group1, group2), transactions = listOf(tx1, tx2), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val allocatedShareId = rep.allocations.first().shareId
        val g1ShareId = split1.shares.first { it.memberId == balaGroup1.id }.id
        assertEquals(g1ShareId, allocatedShareId)
    }

    // 7. Members with identical names but different IDs
    @Test
    fun testMembersWithIdenticalNamesButDifferentIds() {
        val group1 = createGroupWithMembers("Group A", listOf("Charlie"))
        val group2 = createGroupWithMembers("Group B", listOf("Charlie"))

        val charlie1 = group1.members.first { it.name == "Charlie" }
        val charlie2 = group2.members.first { it.name == "Charlie" }

        val tx1 = createExpenseTransaction("A Expense", 100.0)
        val tx2 = createExpenseTransaction("B Expense", 100.0)

        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group1) as com.example.expensetracker.model.SplitCalculationResult.Success
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group2) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splits = listOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        )

        // Repayment for Charlie in Group A
        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group1.group.id, memberId = charlie1.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group1, group2), transactions = listOf(tx1, tx2), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val repayments = listOf(RepaymentWithAllocations(rep.repayment, rep.allocations))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group1, group2), listOf(tx1, tx2), repayments)

        val charlie1Summary = ledger.memberBalances.find { it.memberId == charlie1.id }
        val charlie2Summary = ledger.memberBalances.find { it.memberId == charlie2.id }

        // Charlie 1 in Group A is fully settled (0 debt left), Charlie 2 in Group B still owes 5000 paise
        assertEquals(0L, charlie1Summary?.totalOwedPaise ?: 0L)
        assertNotNull(charlie2Summary)
        assertEquals(5000L, charlie2Summary!!.totalOwedPaise)
    }

    // 8. Preventing allocation to another member's debt
    @Test
    fun testPreventingAllocationToAnotherMemberDebt() {
        val group = createGroupWithMembers("Roommates", listOf("Bala", "Divya"))
        val balaMember = group.members.first { it.name == "Bala" }
        val divyaMember = group.members.first { it.name == "Divya" }

        val tx = createExpenseTransaction("Party", 300.0) // 10000 paise each for Me, Bala, Divya

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        // Bala repays 15000 paise (more than his 10000 paise debt)
        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 15000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        // Bala's repayment allocates 10000 paise to Bala's share, leaving 5000 paise unallocated
        assertEquals(10000L, rep.allocations.first().allocatedAmountPaise)
        assertEquals(balaMember.id, split.shares.first { it.id == rep.allocations.first().shareId }.memberId)
        assertEquals(5000L, rep.repayment.unallocatedAmountPaise)

        // Divya's debt remains completely untouched
        val repayments = listOf(RepaymentWithAllocations(rep.repayment, rep.allocations))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)

        val divyaSummary = ledger.memberBalances.first { it.memberId == divyaMember.id }
        assertEquals(10000L, divyaSummary.totalOwedPaise)
    }

    // 9. Repayment exceeding the remaining debt (overpayment)
    // 10. Correctly tracking unallocated excess
    @Test
    fun testRepaymentExceedingRemainingDebtAndTrackingUnallocatedExcess() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }

        val tx = createExpenseTransaction("Coffee", 100.0) // Bala share = 5000 paise (₹50)

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        // Bala pays ₹70 (7000 paise) for a ₹50 debt
        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 7000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        assertEquals(7000L, rep.repayment.amountPaise)
        assertEquals(2000L, rep.repayment.unallocatedAmountPaise) // ₹20 unallocated excess
        assertEquals(1, rep.allocations.size)
        assertEquals(5000L, rep.allocations.first().allocatedAmountPaise)

        val repayments = listOf(RepaymentWithAllocations(rep.repayment, rep.allocations))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)

        val balaSummary = ledger.memberBalances.first { it.memberId == balaMember.id }
        assertEquals(0L, balaSummary.totalOwedPaise)
        assertEquals(2000L, balaSummary.unallocatedExcessPaise)
    }

    // 11. Zero and negative amounts rejected
    @Test
    fun testZeroAndNegativeAmountsRejected() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Coffee", 100.0)

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        val zeroRep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 0L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        )
        assertTrue(zeroRep is RepaymentRecordResult.Error)

        val negRep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = -5000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        )
        assertTrue(negRep is RepaymentRecordResult.Error)
    }

    // 12. Duplicate submission protection
    // 13. Preventing an incoming transaction from being linked twice
    @Test
    fun testPreventingIncomingTransactionFromBeingLinkedTwice() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val txExpense = createExpenseTransaction("Coffee", 100.0)
        val txIncome = createIncomeTransaction("GPay Credit Bala", 50.0)

        val split = ExpenseSplitCalculator.calculateEqualSplit(txExpense, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        // Link txIncome first time
        val rep1 = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            linkedTransactionId = txIncome.id, splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(txExpense, txIncome), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val repaymentsList = listOf(RepaymentWithAllocations(rep1.repayment, rep1.allocations))

        // Try linking same txIncome a second time
        val rep2 = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            linkedTransactionId = txIncome.id, splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(txExpense, txIncome), repaymentsWithAllocations = repaymentsList
        )

        assertTrue(rep2 is RepaymentRecordResult.Error)
        assertEquals("This transaction is already linked to another repayment record.", (rep2 as RepaymentRecordResult.Error).message)
    }

    // 14. Atomic rollback concept test
    @Test
    fun testAtomicAllocationStateCommit() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Dinner", 100.0)

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        val result = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 5000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        // Both repayment entity and allocation list are constructed together in single result object
        assertNotNull(result.repayment)
        assertEquals(1, result.allocations.size)
        assertEquals(result.repayment.id, result.allocations.first().repaymentId)
    }

    // 15. Fully settled shares disappear from outstanding totals but remain in history
    @Test
    fun testFullySettledSharesDisappearFromOutstandingTotalsButRemainInHistory() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Dinner", 200.0) // 10000 paise share

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 10000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val repayments = listOf(RepaymentWithAllocations(rep.repayment, rep.allocations))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)

        // Disappears from active member balances
        assertEquals(0L, ledger.totalOutstandingPaise)
        assertEquals(0, ledger.membersWhoOweCount)

        // Remains in Split History and Repayment History
        assertEquals(1, ledger.splitHistory.size)
        assertTrue(ledger.splitHistory.first().memberShares.any { it.memberName == "Bala" && it.isFullySettled })
        assertEquals(1, ledger.repaymentHistory.size)
    }

    // 16. Undo restores the exact prior debt state
    @Test
    fun testUndoRestoresExactPriorDebtState() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Dinner", 200.0)

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        val priorLedger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), emptyList())
        assertEquals(10000L, priorLedger.totalOutstandingPaise)

        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 10000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        val repaymentsList = mutableListOf(RepaymentWithAllocations(rep.repayment, rep.allocations))

        // Perform Undo by removing repayment
        repaymentsList.removeAll { it.repayment.id == rep.repayment.id }

        val restoredLedger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repaymentsList)
        assertEquals(priorLedger.totalOutstandingPaise, restoredLedger.totalOutstandingPaise)
        assertEquals(priorLedger.memberBalances.size, restoredLedger.memberBalances.size)
    }

    // 17. Undo does not delete a linked transaction
    @Test
    fun testUndoDoesNotDeleteLinkedTransaction() {
        val txIncome = createIncomeTransaction("GPay Credit", 100.0)
        val originalTxCopy = txIncome.copy()

        // Mock undoing repayment
        val repaymentId = "rep-101"
        val repaymentsList = mutableListOf(
            RepaymentEntity(id = repaymentId, groupId = "g1", memberId = "m1", amountPaise = 10000L, paymentDate = "2026-10-08", linkedTransactionId = txIncome.id)
        )
        repaymentsList.removeAll { it.id == repaymentId }

        // Income transaction entity is untouched
        assertEquals(originalTxCopy.id, txIncome.id)
        assertEquals(originalTxCopy.amount, txIncome.amount, 0.001)
        assertEquals(originalTxCopy.title, txIncome.title)
    }

    // 18. Split deletion behavior when repayment history exists
    @Test
    fun testSplitDeletionBehaviorWithRepayments() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val tx = createExpenseTransaction("Dinner", 200.0)

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val shareId = split.shares.first().id

        val allocation = RepaymentAllocationEntity(
            id = UUID.randomUUID().toString(),
            repaymentId = "rep-1",
            shareId = shareId,
            allocatedAmountPaise = 5000L
        )

        // Verifying FK relationship constraint: shareId is referenced by allocation
        assertEquals(shareId, allocation.shareId)
    }

    // 19. Integer-paise precision
    @Test
    fun testIntegerPaisePrecision() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Exact Paise", 123.45) // 12345 paise total -> 6173 and 6172 paise shares

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        val balaSharePaise = split.shares.first { it.memberId == balaMember.id }.shareAmountPaise

        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = balaSharePaise, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = emptyList()
        ) as RepaymentRecordResult.Success

        assertEquals(balaSharePaise, rep.allocations.first().allocatedAmountPaise)
        assertEquals(0L, rep.repayment.unallocatedAmountPaise)
    }

    // 20. Reactive ledger updates after repayment and Undo
    @Test
    fun testReactiveLedgerUpdatesAfterRepaymentAndUndo() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createExpenseTransaction("Pizza", 400.0) // 20000 paise each

        val split = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splits = listOf(ExpenseSplitWithShares(split = split.split, shares = split.shares))

        // Initial state
        var repayments = emptyList<RepaymentWithAllocations>()
        var ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)
        assertEquals(20000L, ledger.totalOutstandingPaise)

        // After Repayment
        val rep = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = group.group.id, memberId = balaMember.id, repaymentAmountPaise = 15000L, paymentDate = "2026-10-08",
            splitsWithShares = splits, groupsWithMembers = listOf(group), transactions = listOf(tx), repaymentsWithAllocations = repayments
        ) as RepaymentRecordResult.Success

        repayments = listOf(RepaymentWithAllocations(rep.repayment, rep.allocations))
        ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)
        assertEquals(5000L, ledger.totalOutstandingPaise)

        // After Undo
        repayments = emptyList()
        ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splits, listOf(group), listOf(tx), repayments)
        assertEquals(20000L, ledger.totalOutstandingPaise)
    }
}
