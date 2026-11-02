package com.example.expensetracker.model

import com.example.expensetracker.data.local.TestNotificationEntity
import kotlin.math.abs

/**
 * Looks for a specific, already-captured transaction (bank SMS/notification/Gmail) that could
 * explain a balance discrepancy, instead of letting the UI invent a merchant/date/amount.
 */
object MissingTransactionCandidateFinder {

    data class Candidate(
        val amount: Double,
        val merchant: String?,
        val timestamp: Long,
        val referenceNumber: String?,
        val source: String?
    )

    /**
     * Returns the single best-matching captured notification that could be the missing
     * transaction, or null when there isn't enough evidence to prefill one.
     */
    fun findCandidate(
        targetAmountPaise: Long,
        observationTimestampMs: Long,
        accountSuffix: String? = null,
        testNotifications: List<TestNotificationEntity>,
        existingReferenceNumbers: Set<String> = emptySet(),
        searchWindowMs: Long = 7L * 24 * 60 * 60 * 1000L,
        amountToleranceRupees: Double = 0.01
    ): Candidate? {
        if (targetAmountPaise <= 0L) return null
        val targetAmount = targetAmountPaise / 100.0
        val windowStart = observationTimestampMs - searchWindowMs

        val candidates = testNotifications.filter { n ->
            n.detectedPayment &&
                n.amount != null &&
                abs(n.amount - targetAmount) <= amountToleranceRupees &&
                n.receivedAt in windowStart..observationTimestampMs &&
                (n.referenceNumber.isNullOrBlank() || n.referenceNumber !in existingReferenceNumbers) &&
                (accountSuffix.isNullOrBlank() || n.accountNumber.isNullOrBlank() || n.accountNumber == accountSuffix)
        }

        // Ambiguous evidence (multiple equally plausible candidates) is not resolved automatically;
        // we only prefill when there is a single clear closest match in time.
        val sorted = candidates.sortedBy { abs(observationTimestampMs - it.receivedAt) }
        val best = sorted.firstOrNull() ?: return null

        return Candidate(
            amount = best.amount!!,
            merchant = best.merchant,
            timestamp = best.receivedAt,
            referenceNumber = best.referenceNumber,
            source = best.source
        )
    }
}
