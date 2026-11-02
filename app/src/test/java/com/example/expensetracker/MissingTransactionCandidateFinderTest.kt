package com.example.expensetracker

import com.example.expensetracker.data.local.TestNotificationEntity
import com.example.expensetracker.model.MissingTransactionCandidateFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissingTransactionCandidateFinderTest {

    private fun notification(
        id: String,
        receivedAt: Long,
        amount: Double?,
        detectedPayment: Boolean = true,
        merchant: String? = "Swiggy",
        referenceNumber: String? = "REF-$id",
        accountNumber: String? = "528"
    ) = TestNotificationEntity(
        id = id,
        receivedAt = receivedAt,
        packageName = "com.google.android.gm",
        notificationId = 1,
        status = if (detectedPayment) "SUCCESS" else "FAIL",
        detectedPayment = detectedPayment,
        amount = amount,
        merchant = merchant,
        source = "BANK_EMAIL",
        referenceNumber = referenceNumber,
        accountNumber = accountNumber
    )

    @Test
    fun matchingCapturedNotification_isPrefilledAsCandidate() {
        val notifications = listOf(notification(id = "1", receivedAt = 9_500L, amount = 500.0))
        val candidate = MissingTransactionCandidateFinder.findCandidate(
            targetAmountPaise = 50000L,
            observationTimestampMs = 10_000L,
            testNotifications = notifications
        )
        assertEquals(500.0, candidate?.amount)
        assertEquals("Swiggy", candidate?.merchant)
        assertEquals("REF-1", candidate?.referenceNumber)
    }

    @Test
    fun noEvidenceMatchingAmount_doesNotInventATransaction() {
        val notifications = listOf(notification(id = "1", receivedAt = 9_500L, amount = 120.0))
        val candidate = MissingTransactionCandidateFinder.findCandidate(
            targetAmountPaise = 50000L,
            observationTimestampMs = 10_000L,
            testNotifications = notifications
        )
        assertNull(candidate)
    }

    @Test
    fun candidateAlreadySavedByReference_isExcluded() {
        val notifications = listOf(notification(id = "1", receivedAt = 9_500L, amount = 500.0, referenceNumber = "REF-1"))
        val candidate = MissingTransactionCandidateFinder.findCandidate(
            targetAmountPaise = 50000L,
            observationTimestampMs = 10_000L,
            testNotifications = notifications,
            existingReferenceNumbers = setOf("REF-1")
        )
        assertNull(candidate)
    }

    @Test
    fun candidateOutsideSearchWindow_isIgnored() {
        val notifications = listOf(notification(id = "1", receivedAt = 0L, amount = 500.0))
        val candidate = MissingTransactionCandidateFinder.findCandidate(
            targetAmountPaise = 50000L,
            observationTimestampMs = 10_000L,
            testNotifications = notifications,
            searchWindowMs = 5_000L
        )
        assertNull(candidate)
    }

    @Test
    fun candidateForDifferentAccountSuffix_isIgnored() {
        val notifications = listOf(notification(id = "1", receivedAt = 9_500L, amount = 500.0, accountNumber = "999"))
        val candidate = MissingTransactionCandidateFinder.findCandidate(
            targetAmountPaise = 50000L,
            observationTimestampMs = 10_000L,
            accountSuffix = "528",
            testNotifications = notifications
        )
        assertNull(candidate)
    }

    @Test
    fun undetectedNotification_isNotUsedAsCandidate() {
        val notifications = listOf(notification(id = "1", receivedAt = 9_500L, amount = 500.0, detectedPayment = false))
        val candidate = MissingTransactionCandidateFinder.findCandidate(
            targetAmountPaise = 50000L,
            observationTimestampMs = 10_000L,
            testNotifications = notifications
        )
        assertNull(candidate)
    }
}
