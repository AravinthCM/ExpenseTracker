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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SplitOpeningDebtLedgerTest {

    private val group = SplitGroupWithMembers(
        group = SplitGroupEntity(id = "g1", name = "Trip"),
        members = listOf(
            SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true),
            SplitGroupMemberEntity(id = "alice", groupId = "g1", name = "Alice", openingDebtPaise = 100000L),
            SplitGroupMemberEntity(id = "bob", groupId = "g1", name = "Bob", openingDebtPaise = 50000L)
        )
    )

    private val transaction = TransactionEntity(
        id = "tx1", title = "Dinner", amount = 100.0, type = "EXPENSE", category = "Food",
        date = "2026-10-19", source = "MANUAL", timestamp = 1L
    )

    private fun splitFor(memberId: String, amountPaise: Long = 10000L): ExpenseSplitWithShares {
        val split = ExpenseSplitEntity(
            id = "split-$memberId", transactionId = transaction.id, groupId = "g1",
            amountPaise = amountPaise, payerMemberId = "me"
        )
        return ExpenseSplitWithShares(
            split = split,
            shares = listOf(
                ExpenseSplitShareEntity("share-$memberId", split.id, "me", 0L),
                ExpenseSplitShareEntity("share-$memberId-debt", split.id, memberId, amountPaise)
            )
        )
    }

    private fun ledger(
        splits: List<ExpenseSplitWithShares> = emptyList(),
        repayments: List<RepaymentWithAllocations> = emptyList()
    ) = ExpenseSplitCalculator.calculateSettleUpLedger(
        splitsWithShares = splits,
        groupsWithMembers = listOf(group),
        transactions = listOf(transaction),
        repaymentsWithAllocations = repayments
    )

    @Test
    fun openingDebtAloneIsShown() {
        val alice = ledger().memberBalances.single { it.memberId == "alice" }
        assertEquals(100000L, alice.totalOwedPaise)
    }

    @Test
    fun openingDebtAndNewShareAreAdded() {
        val alice = ledger(listOf(splitFor("alice"))).memberBalances.single { it.memberId == "alice" }
        assertEquals(110000L, alice.totalOwedPaise)
    }

    @Test
    fun repaymentAllocatedToShareAndExcessCreditsInitialDebtOnce() {
        val split = splitFor("alice")
        val repayment = RepaymentWithAllocations(
            repayment = RepaymentEntity(
                id = "rep1", groupId = "g1", memberId = "alice", amountPaise = 30000L,
                unallocatedAmountPaise = 20000L, paymentDate = "2026-10-19"
            ),
            allocations = listOf(
                RepaymentAllocationEntity("alloc1", "rep1", "share-alice-debt", 10000L)
            )
        )

        val alice = ledger(listOf(split), listOf(repayment)).memberBalances.single { it.memberId == "alice" }
        assertEquals(80000L, alice.totalOwedPaise)
    }

    @Test
    fun noOpeningDebtStillUsesSplitShare() {
        val noOpeningGroup = group.copy(members = group.members.map { it.copy(openingDebtPaise = 0L) })
        val result = ExpenseSplitCalculator.calculateSettleUpLedger(
            splitsWithShares = listOf(splitFor("alice")),
            groupsWithMembers = listOf(noOpeningGroup),
            transactions = listOf(transaction)
        )

        assertEquals(10000L, result.memberBalances.single { it.memberId == "alice" }.totalOwedPaise)
    }

    @Test
    fun multipleMembersKeepInitialAndShareAmountsIndependent() {
        val result = ledger(listOf(splitFor("alice"), splitFor("bob", 20000L)))
        assertEquals(110000L, result.memberBalances.single { it.memberId == "alice" }.totalOwedPaise)
        assertEquals(70000L, result.memberBalances.single { it.memberId == "bob" }.totalOwedPaise)
    }

    @Test
    fun positiveOpeningDebtDoesNotCreateDebtForMe() {
        val result = ledger()
        assertEquals(null, result.memberBalances.find { it.memberId == "me" })
        assertNotNull(result.memberBalances.find { it.memberId == "alice" })
    }
}
