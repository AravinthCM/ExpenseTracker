package com.example.expensetracker.model

import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.GPayRequestEntity
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.service.GPayRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GPayRequestProcessorAndExpenseCreationTest {

    private val gpayPackage = "com.google.android.apps.nbu.paisa.user"

    private fun twoMemberGroup(meId: String = "me", abdulId: String = "abdul"): SplitGroupWithMembers {
        val me = SplitGroupMemberEntity(id = meId, groupId = "g1", name = "Me", isMe = true)
        val abdul = SplitGroupMemberEntity(id = abdulId, groupId = "g1", name = "Abdul Ajeez")
        return SplitGroupWithMembers(SplitGroupEntity(id = "g1", name = "Room expense"), listOf(me, abdul))
    }

    private fun parseSample(postTime: Long = 1_700_000_000_000L): GPayRequestParser.ParsedGPayRequest {
        return requireNotNull(
            GPayRequestParser.parse(
                packageName = gpayPackage,
                title = "Pay abdul ajeez ₹100.00 for 'Dinner'",
                text = "New split request in 'Room expense'",
                bigText = null,
                textLines = null,
                postTime = postTime
            )
        )
    }

    // --- Matching / capture ---

    @Test
    fun capturesNewRequestAsAutoMatchedWhenGroupAndMemberResolve() {
        val group = twoMemberGroup()
        val parsed = parseSample()

        val result = GPayRequestProcessor.captureOrUpdate(
            parsed = parsed,
            packageName = gpayPackage,
            notificationKey = "key-1",
            originalText = "raw",
            existingRequests = emptyList(),
            groups = listOf(group),
            memberMappings = emptyList()
        )

        assertTrue(result.isNew)
        assertEquals(GPayMatchStatus.AUTO_MATCHED, result.entity.matchStatus)
        assertEquals("g1", result.entity.matchedGroupId)
        assertEquals("abdul", result.entity.matchedMemberId)
    }

    @Test
    fun ambiguousGroupKeepsRequestUnresolvedAndNeverGuesses() {
        val g1 = twoMemberGroup("me", "abdul").copy(group = SplitGroupEntity(id = "g1", name = "Room expense"))
        val g2 = g1.copy(group = SplitGroupEntity(id = "g2", name = "room expense"))
        val parsed = parseSample()

        val result = GPayRequestProcessor.captureOrUpdate(
            parsed = parsed,
            packageName = gpayPackage,
            notificationKey = "key-1",
            originalText = "raw",
            existingRequests = emptyList(),
            groups = listOf(g1, g2),
            memberMappings = emptyList()
        )

        assertEquals(GPayMatchStatus.AMBIGUOUS_GROUP, result.entity.matchStatus)
        assertNull(result.entity.matchedMemberId)
    }

    @Test
    fun ambiguousMemberKeepsRequestUnresolved() {
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val a1 = SplitGroupMemberEntity(id = "a1", groupId = "g1", name = "Abdul Ajeez")
        val a2 = SplitGroupMemberEntity(id = "a2", groupId = "g1", name = "abdul ajeez")
        val group = SplitGroupWithMembers(SplitGroupEntity(id = "g1", name = "Room expense"), listOf(me, a1, a2))
        val parsed = parseSample()

        val result = GPayRequestProcessor.captureOrUpdate(
            parsed = parsed,
            packageName = gpayPackage,
            notificationKey = "key-1",
            originalText = "raw",
            existingRequests = emptyList(),
            groups = listOf(group),
            memberMappings = emptyList()
        )

        assertEquals(GPayMatchStatus.AMBIGUOUS_MEMBER, result.entity.matchStatus)
        assertEquals("g1", result.entity.matchedGroupId)
        assertNull(result.entity.matchedMemberId)
    }

    @Test
    fun repeatedNotificationForSameRequestUpdatesInPlaceWithoutDuplicate() {
        val group = twoMemberGroup()
        val parsed = parseSample()

        val first = GPayRequestProcessor.captureOrUpdate(
            parsed, gpayPackage, "key-1", "raw v1", emptyList(), listOf(group), emptyList()
        ).entity

        val second = GPayRequestProcessor.captureOrUpdate(
            parsed, gpayPackage, "key-1", "raw v2 (updated)", listOf(first), listOf(group), emptyList()
        )

        assertTrue(!second.isNew)
        assertEquals(first.id, second.entity.id)
        assertEquals("raw v2 (updated)", second.entity.originalText)
    }

    @Test
    fun dismissedRequestReceivedAgainIsRecognizedAsRepeatNotReprocessed() {
        val group = twoMemberGroup()
        val parsed = parseSample()
        val dismissed = GPayRequestProcessor.captureOrUpdate(
            parsed, gpayPackage, "key-1", "raw", emptyList(), listOf(group), emptyList()
        ).entity.copy(processingStatus = GPayProcessingStatus.DISMISSED)

        val repeat = GPayRequestProcessor.captureOrUpdate(
            parsed, gpayPackage, "key-1", "raw again", listOf(dismissed), listOf(group), emptyList()
        )

        assertTrue(!repeat.isNew)
        assertTrue(repeat.isRepeatOfProcessed)
        assertEquals(GPayProcessingStatus.DISMISSED, repeat.entity.processingStatus)
    }

    @Test
    fun genuinelyDifferentRequestsAreNotDeduped() {
        val group = twoMemberGroup()
        val parsedDay1 = parseSample(postTime = 1_700_000_000_000L)
        val parsedDay2 = parseSample(postTime = 1_700_000_000_000L + 24L * 60 * 60 * 1000)

        val first = GPayRequestProcessor.captureOrUpdate(
            parsedDay1, gpayPackage, "key-1", "raw", emptyList(), listOf(group), emptyList()
        ).entity

        val second = GPayRequestProcessor.captureOrUpdate(
            parsedDay2, gpayPackage, "key-2", "raw", listOf(first), listOf(group), emptyList()
        )

        assertTrue(second.isNew)
        assertNotNull(second.entity)
    }

    // --- Expense creation / payer assignment / splits ---

    @Test
    fun createsExpenseWithRequesterAsPayerAndEqualSplit() {
        val group = twoMemberGroup()
        val request = GPayRequestEntity(
            requestKey = "k",
            packageName = gpayPackage,
            requesterRawName = "Abdul Ajeez",
            requesterNormalizedName = "abdul ajeez",
            amountPaise = 10000L,
            description = "Dinner",
            groupNameRaw = "Room expense",
            originalText = "raw",
            postTime = 1_700_000_000_000L,
            matchedGroupId = "g1",
            matchedMemberId = "abdul",
            matchStatus = GPayMatchStatus.AUTO_MATCHED
        )

        val result = GPayExpenseCreator.createFromRequest(request, group) as GPayExpenseCreationResult.Success
        assertEquals("abdul", result.split.payerMemberId)
        assertEquals(10000L, result.split.amountPaise)
        assertEquals("Dinner", result.transaction.title)
        assertEquals(2, result.shares.size)
        assertEquals(5000L, result.shares.first { it.memberId == "me" }.shareAmountPaise)
        assertEquals(5000L, result.shares.first { it.memberId == "abdul" }.shareAmountPaise)
    }

    @Test
    fun refusesToCreateExpenseWhenNotConfidentlyMatched() {
        val group = twoMemberGroup()
        val request = GPayRequestEntity(
            requestKey = "k",
            packageName = gpayPackage,
            requesterRawName = "Abdul Ajeez",
            requesterNormalizedName = "abdul ajeez",
            amountPaise = 10000L,
            description = "Dinner",
            groupNameRaw = "Room expense",
            originalText = "raw",
            postTime = 1_700_000_000_000L,
            matchedGroupId = "g1",
            matchedMemberId = null,
            matchStatus = GPayMatchStatus.NEEDS_MEMBER
        )
        val result = GPayExpenseCreator.createFromRequest(request, group)
        assertTrue(result is GPayExpenseCreationResult.Error)
    }

    @Test
    fun equalSplitForNonMePayerPreservesPaiseRemainderDeterministically() {
        // 3 members, ₹100.01 = 10001 paise, should distribute deterministically by member id.
        val me = SplitGroupMemberEntity(id = "a-me", groupId = "g1", name = "Me", isMe = true)
        val abdul = SplitGroupMemberEntity(id = "b-abdul", groupId = "g1", name = "Abdul")
        val chitra = SplitGroupMemberEntity(id = "c-chitra", groupId = "g1", name = "Chitra")
        val group = SplitGroupWithMembers(SplitGroupEntity(id = "g1", name = "Trip"), listOf(me, abdul, chitra))

        val tx = com.example.expensetracker.data.local.TransactionEntity(
            id = "tx1", title = "Snacks", amount = 100.01, type = "EXPENSE",
            category = "OTHER", date = "2024-01-01", source = "GPAY", timestamp = 0L
        )

        val result = ExpenseSplitCalculator.calculateEqualSplitForPayer(tx, group, "b-abdul") as SplitCalculationResult.Success
        val sharesById = result.shares.associateBy { it.memberId }
        // 10001 / 3 = 3333 base, remainder 2 -> first two members by sorted id get +1
        assertEquals(3334L, sharesById["a-me"]!!.shareAmountPaise)
        assertEquals(3334L, sharesById["b-abdul"]!!.shareAmountPaise)
        assertEquals(3333L, sharesById["c-chitra"]!!.shareAmountPaise)
        assertEquals(10001L, result.shares.sumOf { it.shareAmountPaise })
    }

    @Test
    fun customSplitForNonMePayerValidatesAndAssignsPayer() {
        val group = twoMemberGroup()
        val tx = com.example.expensetracker.data.local.TransactionEntity(
            id = "tx1", title = "Dinner", amount = 100.0, type = "EXPENSE",
            category = "OTHER", date = "2024-01-01", source = "GPAY", timestamp = 0L
        )
        val result = ExpenseSplitCalculator.calculateCustomSplitForPayer(
            tx, group, "abdul", mapOf("me" to 3000L, "abdul" to 7000L)
        ) as SplitCalculationResult.Success
        assertEquals("abdul", result.split.payerMemberId)
        assertEquals(3000L, result.shares.first { it.memberId == "me" }.shareAmountPaise)
        assertEquals(7000L, result.shares.first { it.memberId == "abdul" }.shareAmountPaise)
    }

    @Test
    fun existingDebtRecalculatesAfterNonMePayerExpense_matchesSpecExample() {
        // Spec example: before the new expense there is a ₹150 balance involving Abdul; Abdul then
        // pays ₹100 for dinner, split equally (user's share ₹50). Net debt should become ₹100 —
        // i.e. the new ₹50 owed to Abdul is netted against the existing ₹150 balance.
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val abdul = SplitGroupMemberEntity(id = "abdul", groupId = "g1", name = "Abdul", openingDebtPaise = 15000L)
        val members = listOf(me, abdul)

        val tx = com.example.expensetracker.data.local.TransactionEntity(
            id = "tx1", title = "Dinner", amount = 100.0, type = "EXPENSE",
            category = "OTHER", date = "2024-01-01", source = "GPAY", timestamp = 0L
        )
        val group = SplitGroupWithMembers(SplitGroupEntity(id = "g1", name = "Group A"), members)
        val splitResult = ExpenseSplitCalculator.calculateEqualSplitForPayer(tx, group, "abdul") as SplitCalculationResult.Success
        val splitWithShares = ExpenseSplitWithShares(splitResult.split, splitResult.shares)

        val balances = ExpenseSplitCalculator.calculateOutstandingBalances(listOf(splitWithShares), members)

        assertEquals(10000L, balances.single().amountOwedPaise)
    }
}
