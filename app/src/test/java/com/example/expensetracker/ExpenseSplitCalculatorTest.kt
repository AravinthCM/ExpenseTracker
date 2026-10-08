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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ExpenseSplitCalculatorTest {

    private fun createDummyExpenseTransaction(id: String = UUID.randomUUID().toString(), amount: Double = 482.0): TransactionEntity {
        return TransactionEntity(
            id = id,
            title = "Dinner",
            amount = amount,
            type = "EXPENSE",
            category = "FOOD",
            date = "Today",
            source = "MANUAL",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createDummyIncomeTransaction(id: String = UUID.randomUUID().toString(), amount: Double = 500.0): TransactionEntity {
        return TransactionEntity(
            id = id,
            title = "Salary",
            amount = amount,
            type = "INCOME",
            category = "SALARY",
            date = "Today",
            source = "MANUAL",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createDummyTransferTransaction(id: String = UUID.randomUUID().toString(), amount: Double = 1000.0): TransactionEntity {
        return TransactionEntity(
            id = id,
            title = "Self Transfer",
            amount = amount,
            type = "TRANSFER",
            category = "OTHER",
            date = "Today",
            source = "MANUAL",
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createDummyGroup(
        memberCount: Int = 4,
        meIndex: Int = 0,
        customMemberIds: List<String>? = null
    ): SplitGroupWithMembers {
        val groupId = UUID.randomUUID().toString()
        val members = (0 until memberCount).map { i ->
            val memberId = customMemberIds?.getOrNull(i) ?: "member-id-$i"
            SplitGroupMemberEntity(
                id = memberId,
                groupId = groupId,
                name = if (i == meIndex) "Me" else "Member $i",
                isMe = (i == meIndex)
            )
        }
        return SplitGroupWithMembers(
            group = SplitGroupEntity(id = groupId, name = "Test Group"),
            members = members
        )
    }

    // 1. ₹482 split equally among four members
    @Test
    fun testRs482SplitEquallyAmongFourMembers() {
        val tx = createDummyExpenseTransaction(amount = 482.0)
        val group = createDummyGroup(memberCount = 4)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue("Equal split of 482 among 4 should succeed", result is SplitCalculationResult.Success)

        val success = result as SplitCalculationResult.Success
        assertEquals(48200L, success.split.amountPaise)
        assertEquals(4, success.shares.size)

        // Each share should be exactly ₹120.50 = 12050 paise
        success.shares.forEach { share ->
            assertEquals(12050L, share.shareAmountPaise)
        }

        val sum = success.shares.sumOf { it.shareAmountPaise }
        assertEquals(48200L, sum)
    }

    // 2. ₹100 split equally among three members with deterministic remainder distribution
    @Test
    fun testRs100SplitEquallyAmongThreeMembersDeterministicRemainder() {
        val tx = createDummyExpenseTransaction(amount = 100.0)
        // Fixed member IDs to test deterministic ordering (sorted by id ascending)
        val memberIds = listOf("c_member", "a_member", "b_member")
        val group = createDummyGroup(memberCount = 3, meIndex = 1, customMemberIds = memberIds)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue(result is SplitCalculationResult.Success)

        val success = result as SplitCalculationResult.Success
        assertEquals(10000L, success.split.amountPaise)
        assertEquals(3, success.shares.size)

        val sum = success.shares.sumOf { it.shareAmountPaise }
        assertEquals("Total shares must equal exactly 10000 paise", 10000L, sum)

        // Sorted IDs: "a_member" (index 0), "b_member" (index 1), "c_member" (index 2)
        // Remainder = 10000 % 3 = 1 paise -> assigned to "a_member"
        val shareMap = success.shares.associate { it.memberId to it.shareAmountPaise }
        assertEquals(3334L, shareMap["a_member"])
        assertEquals(3333L, shareMap["b_member"])
        assertEquals(3333L, shareMap["c_member"])
    }

    // 3. Equal splitting among two members
    @Test
    fun testEqualSplitAmongTwoMembers() {
        val tx = createDummyExpenseTransaction(amount = 250.0)
        val group = createDummyGroup(memberCount = 2)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue(result is SplitCalculationResult.Success)

        val success = result as SplitCalculationResult.Success
        assertEquals(25000L, success.split.amountPaise)
        assertEquals(2, success.shares.size)
        assertEquals(12500L, success.shares[0].shareAmountPaise)
        assertEquals(12500L, success.shares[1].shareAmountPaise)
        assertEquals(25000L, success.shares.sumOf { it.shareAmountPaise })
    }

    // 4. Custom amounts that sum correctly
    @Test
    fun testCustomAmountsSumCorrectly() {
        val tx = createDummyExpenseTransaction(amount = 300.0)
        val memberIds = listOf("m1", "m2", "m3")
        val group = createDummyGroup(memberCount = 3, customMemberIds = memberIds)

        val customMap = mapOf(
            "m1" to 10000L, // ₹100
            "m2" to 15000L, // ₹150
            "m3" to 5000L   // ₹50
        )

        val result = ExpenseSplitCalculator.calculateCustomSplit(tx, group, customMap)
        assertTrue(result is SplitCalculationResult.Success)

        val success = result as SplitCalculationResult.Success
        assertEquals(30000L, success.shares.sumOf { it.shareAmountPaise })
    }

    // 5. Custom amounts that do not sum to the expense total
    @Test
    fun testCustomAmountsMismatchSumFails() {
        val tx = createDummyExpenseTransaction(amount = 300.0)
        val memberIds = listOf("m1", "m2", "m3")
        val group = createDummyGroup(memberCount = 3, customMemberIds = memberIds)

        val customMap = mapOf(
            "m1" to 10000L,
            "m2" to 10000L,
            "m3" to 5000L // Total 25,000 paise vs expense 30,000 paise
        )

        val result = ExpenseSplitCalculator.calculateCustomSplit(tx, group, customMap)
        assertTrue(result is SplitCalculationResult.Error)
        val error = result as SplitCalculationResult.Error
        assertTrue(error.message.contains("does not match expense total"))
    }

    // 6. Negative and zero shares
    @Test
    fun testNegativeOrZeroSharesFails() {
        val tx = createDummyExpenseTransaction(amount = 100.0)
        val memberIds = listOf("m1", "m2")
        val group = createDummyGroup(memberCount = 2, customMemberIds = memberIds)

        val zeroShareMap = mapOf("m1" to 10000L, "m2" to 0L)
        val zeroResult = ExpenseSplitCalculator.calculateCustomSplit(tx, group, zeroShareMap)
        assertTrue(zeroResult is SplitCalculationResult.Error)

        val negativeShareMap = mapOf("m1" to 12000L, "m2" to -2000L)
        val negResult = ExpenseSplitCalculator.calculateCustomSplit(tx, group, negativeShareMap)
        assertTrue(negResult is SplitCalculationResult.Error)
    }

    // 7. Missing group members
    @Test
    fun testMissingGroupMembersInCustomSplitFails() {
        val tx = createDummyExpenseTransaction(amount = 200.0)
        val memberIds = listOf("m1", "m2", "m3")
        val group = createDummyGroup(memberCount = 3, customMemberIds = memberIds)

        // Only m1 and m2 provided, m3 missing
        val incompleteMap = mapOf("m1" to 10000L, "m2" to 10000L)

        val result = ExpenseSplitCalculator.calculateCustomSplit(tx, group, incompleteMap)
        assertTrue(result is SplitCalculationResult.Error)
    }

    // 8. Duplicate member allocations (invalid map keys)
    @Test
    fun testDuplicateOrUnknownMemberAllocationsFails() {
        val tx = createDummyExpenseTransaction(amount = 200.0)
        val memberIds = listOf("m1", "m2")
        val group = createDummyGroup(memberCount = 2, customMemberIds = memberIds)

        val invalidKeysMap = mapOf("m1" to 10000L, "unknown_member" to 10000L)

        val result = ExpenseSplitCalculator.calculateCustomSplit(tx, group, invalidKeysMap)
        assertTrue(result is SplitCalculationResult.Error)
    }

    // 9. Empty or invalid group
    @Test
    fun testInsufficientGroupMembersFails() {
        val tx = createDummyExpenseTransaction(amount = 100.0)
        val singleMemberGroup = createDummyGroup(memberCount = 1)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, singleMemberGroup)
        assertTrue(result is SplitCalculationResult.Error)
        assertEquals("Split group must have at least 2 members.", (result as SplitCalculationResult.Error).message)
    }

    // 10. Income transaction rejected
    @Test
    fun testIncomeTransactionRejected() {
        val tx = createDummyIncomeTransaction()
        val group = createDummyGroup(memberCount = 3)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue(result is SplitCalculationResult.Error)
        assertEquals("Only EXPENSE transactions can be split.", (result as SplitCalculationResult.Error).message)
    }

    // 11. Transfer transaction rejected
    @Test
    fun testTransferTransactionRejected() {
        val tx = createDummyTransferTransaction()
        val group = createDummyGroup(memberCount = 3)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue(result is SplitCalculationResult.Error)
        assertEquals("Only EXPENSE transactions can be split.", (result as SplitCalculationResult.Error).message)
    }

    // 12. Total of calculated shares always equals the original expense amount
    @Test
    fun testTotalSharesAlwaysEqualsOriginalExpense() {
        val testAmounts = listOf(1.0, 99.99, 100.0, 333.33, 482.0, 10000.50)
        val testMemberCounts = listOf(2, 3, 4, 5, 7)

        for (amount in testAmounts) {
            for (count in testMemberCounts) {
                val tx = createDummyExpenseTransaction(amount = amount)
                val group = createDummyGroup(memberCount = count)

                val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
                assertTrue("Equal split should succeed for amount $amount and count $count", result is SplitCalculationResult.Success)

                val success = result as SplitCalculationResult.Success
                val expectedTotalPaise = ExpenseSplitCalculator.rupeesToPaise(amount)
                val actualSumPaise = success.shares.sumOf { it.shareAmountPaise }

                assertEquals("Sum of shares must match original amount in paise exactly", expectedTotalPaise, actualSumPaise)
            }
        }
    }

    // 13. Outstanding debt excludes Me's own share
    @Test
    fun testOutstandingDebtExcludesMeOwnShare() {
        val tx = createDummyExpenseTransaction(amount = 482.0)
        val memberIds = listOf("me-id", "b-id", "c-id", "d-id")
        val group = createDummyGroup(memberCount = 4, meIndex = 0, customMemberIds = memberIds)

        val result = ExpenseSplitCalculator.calculateEqualSplit(tx, group)
        assertTrue(result is SplitCalculationResult.Success)
        val success = result as SplitCalculationResult.Success

        val splitWithShares = ExpenseSplitWithShares(
            split = success.split,
            shares = success.shares
        )

        val balances = ExpenseSplitCalculator.calculateOutstandingBalances(
            splitsWithShares = listOf(splitWithShares),
            members = group.members
        )

        // Non-Me members: b-id, c-id, d-id (3 members)
        assertEquals(3, balances.size)
        assertFalse("Me member should not be in outstanding balances list", balances.any { it.memberId == "me-id" })

        balances.forEach { memberBalance ->
            assertEquals(12050L, memberBalance.amountOwedPaise)
        }

        val totalOwedToMePaise = balances.sumOf { it.amountOwedPaise }
        assertEquals(36150L, totalOwedToMePaise) // ₹361.50
    }
}
