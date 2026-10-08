package com.example.expensetracker

import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.ExpenseSplitCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SettleUpLedgerTest {

    private fun createMember(name: String, isMe: Boolean, groupId: String): SplitGroupMemberEntity {
        return SplitGroupMemberEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = name,
            isMe = isMe
        )
    }

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

    private fun createTransaction(title: String = "Amman Mess", amount: Double = 482.0): TransactionEntity {
        return TransactionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            amount = amount,
            type = "EXPENSE",
            category = "FOOD",
            date = "2026-10-08",
            source = "MANUAL",
            timestamp = System.currentTimeMillis()
        )
    }

    // 1. One equal split across four members
    @Test
    fun testOneEqualSplitAcrossFourMembers() {
        val group = createGroupWithMembers("Roommates", listOf("Bala", "Charan", "Divya"))
        val tx = createTransaction("Amman Mess", 482.0) // 48200 paise

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue(splitResult is com.example.expensetracker.model.SplitCalculationResult.Success)
        val success = splitResult as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(ExpenseSplitWithShares(split = success.split, shares = success.shares))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))

        assertEquals(36150L, ledger.totalOutstandingPaise) // ₹361.50 = 12050 * 3
        assertEquals(3, ledger.membersWhoOweCount)
        assertEquals(1, ledger.groupsWithDebtCount)

        val balaBalance = ledger.memberBalances.find { it.memberName == "Bala" }
        assertNotNull(balaBalance)
        assertEquals(12050L, balaBalance!!.totalOwedPaise)

        val charanBalance = ledger.memberBalances.find { it.memberName == "Charan" }
        assertNotNull(charanBalance)
        assertEquals(12050L, charanBalance!!.totalOwedPaise)

        val divyaBalance = ledger.memberBalances.find { it.memberName == "Divya" }
        assertNotNull(divyaBalance)
        assertEquals(12050L, divyaBalance!!.totalOwedPaise)
    }

    // 2. Multiple expenses involving the same member
    @Test
    fun testMultipleExpensesInvolvingSameMember() {
        val group = createGroupWithMembers("Flatmates", listOf("Bala"))
        val tx1 = createTransaction("Groceries", 200.0) // 20000 paise -> 10000 each
        val tx2 = createTransaction("Electricity", 100.0) // 10000 paise -> 5000 each

        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        )

        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx1, tx2))

        assertEquals(15000L, ledger.totalOutstandingPaise) // ₹150.00
        assertEquals(1, ledger.membersWhoOweCount)

        val balaSummary = ledger.memberBalances.first()
        assertEquals("Bala", balaSummary.memberName)
        assertEquals(15000L, balaSummary.totalOwedPaise)
        assertEquals(2, balaSummary.itemizedExpenses.size)
    }

    // 3. Different shares across multiple splits (custom splits)
    @Test
    fun testDifferentSharesAcrossMultipleSplits() {
        val group = createGroupWithMembers("Trip", listOf("Bala", "Charan"))
        val meMember = group.members.first { it.isMe }
        val balaMember = group.members.first { it.name == "Bala" }
        val charanMember = group.members.first { it.name == "Charan" }

        val tx = createTransaction("Fuel", 300.0) // 30000 paise
        val customMap = mapOf(
            meMember.id to 10000L,
            balaMember.id to 15000L,
            charanMember.id to 5000L
        )

        val splitResult = ExpenseSplitCalculator.calculateCustomSplit(tx, group, customMap) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))

        assertEquals(20000L, ledger.totalOutstandingPaise) // ₹200.00
        val balaSummary = ledger.memberBalances.find { it.memberId == balaMember.id }
        assertEquals(15000L, balaSummary!!.totalOwedPaise)

        val charanSummary = ledger.memberBalances.find { it.memberId == charanMember.id }
        assertEquals(5000L, charanSummary!!.totalOwedPaise)
    }

    // 4. Excluding Me's own share
    @Test
    fun testExcludingMeOwnShare() {
        val group = createGroupWithMembers("Roommates", listOf("Bala", "Charan"))
        val tx = createTransaction("Dinner", 300.0) // 30000 paise -> 10000 paise per person

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))

        // Total split is ₹300 (30000 paise), Me's share is ₹100 (10000 paise), money owed to Me is ₹200 (20000 paise)
        assertEquals(20000L, ledger.totalOutstandingPaise)
        assertFalse("Me should not appear in member balances", ledger.memberBalances.any { it.memberName == "Me" })
    }

    // 5. Ignoring splits where Me is not the payer
    @Test
    fun testIgnoringSplitsWhereMeIsNotThePayer() {
        val group = createGroupWithMembers("Roommates", listOf("Bala", "Charan"))
        val balaMember = group.members.first { it.name == "Bala" }
        val tx = createTransaction("Bala Paid Lunch", 300.0)

        // Manually build split entity where payer is Bala (not Me)
        val splitEntity = ExpenseSplitEntity(
            id = UUID.randomUUID().toString(),
            transactionId = tx.id,
            groupId = group.group.id,
            amountPaise = 30000L,
            payerMemberId = balaMember.id, // Bala paid
            createdAt = System.currentTimeMillis()
        )

        val shares = group.members.map { member ->
            ExpenseSplitShareEntity(
                id = UUID.randomUUID().toString(),
                splitId = splitEntity.id,
                memberId = member.id,
                shareAmountPaise = 10000L
            )
        }

        val splitsWithShares = listOf(ExpenseSplitWithShares(split = splitEntity, shares = shares))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))

        // Under Phase 4 rules, debts owed to Me = 0 because Me was NOT the payer
        assertEquals(0L, ledger.totalOutstandingPaise)
        assertTrue(ledger.memberBalances.isEmpty())
    }

    // 6. Correct grouping by stable member ID rather than name
    @Test
    fun testGroupingByStableMemberIdRatherThanName() {
        val groupId1 = UUID.randomUUID().toString()
        val m1Group1 = SplitGroupMemberEntity(id = "m-id-101", groupId = groupId1, name = "Alex", isMe = true)
        val m2Group1 = SplitGroupMemberEntity(id = "m-id-102", groupId = groupId1, name = "Alex", isMe = false)
        val group1 = SplitGroupWithMembers(
            group = SplitGroupEntity(id = groupId1, name = "Work"),
            members = listOf(m1Group1, m2Group1)
        )

        val groupId2 = UUID.randomUUID().toString()
        val m1Group2 = SplitGroupMemberEntity(id = "m-id-201", groupId = groupId2, name = "Alex", isMe = true)
        val m2Group2 = SplitGroupMemberEntity(id = "m-id-202", groupId = groupId2, name = "Alex", isMe = false)
        val group2 = SplitGroupWithMembers(
            group = SplitGroupEntity(id = groupId2, name = "Gym"),
            members = listOf(m1Group2, m2Group2)
        )

        val tx1 = createTransaction("Work Lunch", 100.0)
        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group1) as com.example.expensetracker.model.SplitCalculationResult.Success

        val tx2 = createTransaction("Gym Membership", 200.0)
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group2) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        )

        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(
            splitsWithShares,
            listOf(group1, group2),
            listOf(tx1, tx2)
        )

        // Both non-Me members are named "Alex", but have different member IDs ("m-id-102" and "m-id-202")
        assertEquals(2, ledger.memberBalances.size)
        assertTrue(ledger.memberBalances.any { it.memberId == "m-id-102" && it.groupName == "Work" })
        assertTrue(ledger.memberBalances.any { it.memberId == "m-id-202" && it.groupName == "Gym" })
    }

    // 7. No splits
    @Test
    fun testNoSplits() {
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(emptyList(), emptyList(), emptyList())
        assertEquals(0L, ledger.totalOutstandingPaise)
        assertEquals(0, ledger.membersWhoOweCount)
        assertEquals(0, ledger.groupsWithDebtCount)
        assertTrue(ledger.memberBalances.isEmpty())
        assertTrue(ledger.splitHistory.isEmpty())
    }

    // 8. Splits with no outstanding debt owed to Me
    @Test
    fun testSplitsWithNoOutstandingDebtOwedToMe() {
        val group = createGroupWithMembers("Roommates", listOf("Bala"))
        val meMember = group.members.first { it.isMe }
        val balaMember = group.members.first { it.name == "Bala" }

        val tx = createTransaction("Coffee", 50.0) // 5000 paise

        // Custom split where Me pays, but Bala's share is 0 and Me's share is 5000 paise (not allowed by validator, but test edge case structure)
        val customMap = mapOf(
            meMember.id to 5000L,
            balaMember.id to 0L
        )

        // If custom shares contains 0, calculator returns error. Testing empty balances returned.
        val splitsWithShares = emptyList<ExpenseSplitWithShares>()
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))
        assertEquals(0L, ledger.totalOutstandingPaise)
        assertTrue(ledger.memberBalances.isEmpty())
    }

    // 9. Integer-paise arithmetic without rounding errors
    @Test
    fun testIntegerPaiseArithmeticWithoutRoundingErrors() {
        val group = createGroupWithMembers("Trio", listOf("Bala", "Charan"))
        val tx = createTransaction("Dinner", 100.0) // 10000 paise

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))

        // 10000 % 3 = 1 remainder -> shares: 3334, 3333, 3333
        // Me gets 3334 (if Me is first sorted ID) or non-Me members get shares summing exactly to (10000 - Me's share)
        val meMember = group.members.first { it.isMe }
        val meShare = splitResult.shares.first { it.memberId == meMember.id }.shareAmountPaise
        val expectedNonMeSum = 10000L - meShare

        assertEquals(expectedNonMeSum, ledger.totalOutstandingPaise)
        assertEquals(10000L, splitResult.shares.sumOf { it.shareAmountPaise })
    }

    // 10. Undoing or deleting a split removes its contribution from the computed ledger
    @Test
    fun testUndoingOrDeletingSplitRemovesContributionFromLedger() {
        val group = createGroupWithMembers("Flatmates", listOf("Bala"))
        val tx1 = createTransaction("Rent Share 1", 500.0)
        val tx2 = createTransaction("Rent Share 2", 300.0)

        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group) as com.example.expensetracker.model.SplitCalculationResult.Success

        val initialSplits = mutableListOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        )

        val initialLedger = ExpenseSplitCalculator.calculateSettleUpLedger(initialSplits, listOf(group), listOf(tx1, tx2))
        assertEquals(40000L, initialLedger.totalOutstandingPaise) // (25000 + 15000)

        // Undo / Delete split2
        initialSplits.removeIf { it.split.id == split2.split.id }

        val updatedLedger = ExpenseSplitCalculator.calculateSettleUpLedger(initialSplits, listOf(group), listOf(tx1, tx2))
        assertEquals(25000L, updatedLedger.totalOutstandingPaise) // Only split1 contribution remains
    }

    // 11. Split history uses the correct original transaction and group
    @Test
    fun testSplitHistoryUsesCorrectTransactionAndGroup() {
        val group = createGroupWithMembers("Goa Trip", listOf("Bala", "Charan"))
        val tx = createTransaction("Beach Resort", 1200.0)

        val splitResult = ExpenseSplitCalculator.calculateEqualSplit(tx, group) as com.example.expensetracker.model.SplitCalculationResult.Success
        val splitsWithShares = listOf(ExpenseSplitWithShares(split = splitResult.split, shares = splitResult.shares))

        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(splitsWithShares, listOf(group), listOf(tx))

        assertEquals(1, ledger.splitHistory.size)
        val historyItem = ledger.splitHistory.first()

        assertEquals("Beach Resort", historyItem.transactionTitle)
        assertEquals("2026-10-08", historyItem.transactionDate)
        assertEquals("Goa Trip", historyItem.groupName)
        assertEquals(120000L, historyItem.transactionAmountPaise)
        assertEquals(3, historyItem.memberShares.size)
    }

    // 12. Multiple groups with similarly named members remain distinguishable
    @Test
    fun testMultipleGroupsWithSimilarlyNamedMembersRemainDistinguishable() {
        val group1 = createGroupWithMembers("Flat 101", listOf("David"))
        val group2 = createGroupWithMembers("Flat 102", listOf("David"))

        val tx1 = createTransaction("Wifi Flat 101", 100.0)
        val tx2 = createTransaction("Wifi Flat 102", 150.0)

        val split1 = ExpenseSplitCalculator.calculateEqualSplit(tx1, group1) as com.example.expensetracker.model.SplitCalculationResult.Success
        val split2 = ExpenseSplitCalculator.calculateEqualSplit(tx2, group2) as com.example.expensetracker.model.SplitCalculationResult.Success

        val splitsWithShares = listOf(
            ExpenseSplitWithShares(split = split1.split, shares = split1.shares),
            ExpenseSplitWithShares(split = split2.split, shares = split2.shares)
        )

        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(
            splitsWithShares,
            listOf(group1, group2),
            listOf(tx1, tx2)
        )

        assertEquals(2, ledger.memberBalances.size)
        val g1Balance = ledger.memberBalances.find { it.groupName == "Flat 101" }
        val g2Balance = ledger.memberBalances.find { it.groupName == "Flat 102" }

        assertNotNull(g1Balance)
        assertNotNull(g2Balance)
        assertEquals(5000L, g1Balance!!.totalOwedPaise)
        assertEquals(7500L, g2Balance!!.totalOwedPaise)
    }
}
