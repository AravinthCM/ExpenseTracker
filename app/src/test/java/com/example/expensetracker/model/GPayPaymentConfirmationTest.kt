package com.example.expensetracker.model

import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.GPayRequestEntity
import com.example.expensetracker.data.local.RepaymentWithAllocations
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GPayPaymentConfirmationTest {

    private val gpayPackage = "com.google.android.apps.nbu.paisa.user"

    private fun sampleRequest(
        id: String = "req1",
        requester: String = "Abdul Ajeez",
        amountPaise: Long = 10000L,
        paymentConfirmationTransactionId: String? = null,
        repaymentId: String? = null,
        processingStatus: String = GPayProcessingStatus.ADDED_AS_EXPENSE
    ) = GPayRequestEntity(
        id = id,
        requestKey = id,
        packageName = gpayPackage,
        requesterRawName = requester,
        requesterNormalizedName = GPayRequestMatcher.normalizeName(requester),
        amountPaise = amountPaise,
        description = "Dinner",
        groupNameRaw = "Room expense",
        originalText = "raw",
        postTime = 0L,
        matchedGroupId = "g1",
        matchedMemberId = "abdul",
        matchStatus = GPayMatchStatus.AUTO_MATCHED,
        processingStatus = processingStatus,
        paymentConfirmationTransactionId = paymentConfirmationTransactionId,
        repaymentId = repaymentId
    )

    @Test
    fun findsUniqueMatchingRequestForIncomingPayment() {
        val request = sampleRequest()
        val match = GPayRequestProcessor.findMatchingRequestForPaymentConfirmation(
            requests = listOf(request),
            counterpartyName = "Abdul Ajeez",
            amountPaise = 10000L
        )
        assertEquals("req1", match?.id)
    }

    @Test
    fun doesNotMatchWhenAmountDiffers() {
        val request = sampleRequest()
        val match = GPayRequestProcessor.findMatchingRequestForPaymentConfirmation(
            requests = listOf(request),
            counterpartyName = "Abdul Ajeez",
            amountPaise = 9999L
        )
        assertNull(match)
    }

    @Test
    fun ambiguousWhenMultipleRequestsMatchSameNameAndAmount() {
        val r1 = sampleRequest(id = "req1")
        val r2 = sampleRequest(id = "req2")
        val match = GPayRequestProcessor.findMatchingRequestForPaymentConfirmation(
            requests = listOf(r1, r2),
            counterpartyName = "Abdul Ajeez",
            amountPaise = 10000L
        )
        assertNull(match)
    }

    @Test
    fun skipsRequestsAlreadyLinkedToAPaymentOrRepayment() {
        val alreadyLinked = sampleRequest(paymentConfirmationTransactionId = "tx-existing")
        assertNull(
            GPayRequestProcessor.findMatchingRequestForPaymentConfirmation(
                listOf(alreadyLinked), "Abdul Ajeez", 10000L
            )
        )

        val alreadySettled = sampleRequest(repaymentId = "rep-existing")
        assertNull(
            GPayRequestProcessor.findMatchingRequestForPaymentConfirmation(
                listOf(alreadySettled), "Abdul Ajeez", 10000L
            )
        )
    }

    @Test
    fun dismissedRequestsAreNeverTreatedAsPaymentMatches() {
        val dismissed = sampleRequest(processingStatus = GPayProcessingStatus.DISMISSED)
        assertNull(
            GPayRequestProcessor.findMatchingRequestForPaymentConfirmation(
                listOf(dismissed), "Abdul Ajeez", 10000L
            )
        )
    }

    @Test
    fun confirmingRepaymentReusesExistingAllocationLogicAndRejectsDoubleLinking() {
        // The group expense created from the request: Abdul paid 100, split equally.
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val abdul = SplitGroupMemberEntity(id = "abdul", groupId = "g1", name = "Abdul Ajeez")
        val group = SplitGroupWithMembers(SplitGroupEntity(id = "g1", name = "Room expense"), listOf(me, abdul))

        // Note: existing calculateRepaymentAllocation only settles shares where the *payer* is "Me",
        // so a repayment confirmation here is recorded against the group/member directly (matching how
        // confirmGPayRepayment wires groupId/memberId from the resolved request), independent of the
        // underlying split's payer. The key regression being guarded is: the same linkedTransactionId
        // can never be used to record two repayments.
        val linkedTxId = "confirmation-tx-1"

        val firstResult = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = "g1",
            memberId = "abdul",
            repaymentAmountPaise = 10000L,
            paymentDate = "2024-01-02",
            linkedTransactionId = linkedTxId,
            splitsWithShares = emptyList(),
            groupsWithMembers = listOf(group),
            transactions = emptyList(),
            repaymentsWithAllocations = emptyList()
        )
        assertTrue(firstResult is RepaymentRecordResult.Success)
        val firstRepayment = (firstResult as RepaymentRecordResult.Success).repayment

        val secondResult = ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = "g1",
            memberId = "abdul",
            repaymentAmountPaise = 10000L,
            paymentDate = "2024-01-02",
            linkedTransactionId = linkedTxId,
            splitsWithShares = emptyList(),
            groupsWithMembers = listOf(group),
            transactions = emptyList(),
            repaymentsWithAllocations = listOf(RepaymentWithAllocations(firstRepayment, emptyList()))
        )
        assertTrue(secondResult is RepaymentRecordResult.Error)
    }
}
