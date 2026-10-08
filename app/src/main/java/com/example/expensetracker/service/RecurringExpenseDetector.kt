package com.example.expensetracker.service

import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class DetectedRecurringCandidate(
    val merchant: String,
    val displayMerchant: String,
    val amount: Double,
    val frequency: String,
    val intervalDays: Int,
    val confidence: String, // "High confidence", "Medium confidence"
    val lastTransactionTimestamp: Long,
    val matchingTransactionCount: Int
)

data class ConfirmedRecurringStatus(
    val entity: RecurringExpenseEntity,
    val nextExpectedTimestamp: Long,
    val isMissed: Boolean,
    val statusText: String
)

object RecurringExpenseDetector {

    // Single clearly named constant for amount matching tolerance (15%)
    const val AMOUNT_TOLERANCE_PERCENT = 0.15

    fun normalizeMerchantTitle(title: String): String {
        return title
            .lowercase(Locale.getDefault())
            .trim()
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .replace(Regex("""\s+"""), " ")
    }

    fun detectCandidates(
        transactions: List<Transaction>,
        existingConfirmedOrDismissed: List<RecurringExpenseEntity>
    ): List<DetectedRecurringCandidate> {
        // Exclude INCOME and TRANSFER transactions strictly
        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }

        val excludedMerchants = existingConfirmedOrDismissed
            .map { normalizeMerchantTitle(it.merchant) }
            .toSet()

        // Group expenses by normalized merchant title
        val groupedByMerchant = expenseTransactions.groupBy { normalizeMerchantTitle(it.title) }

        val candidates = mutableListOf<DetectedRecurringCandidate>()

        for ((normMerchant, txList) in groupedByMerchant) {
            if (normMerchant.isBlank()) continue

            // Filter out already confirmed or dismissed merchants
            if (excludedMerchants.any { normMerchant.contains(it) || it.contains(normMerchant) }) {
                continue
            }

            // Rule 1: Requires at least 3 matching transactions
            if (txList.size < 3) continue

            // Sort ascending by timestamp
            val sortedTx = txList.sortedBy { it.timestamp }

            // Amount consistency check
            val amounts = sortedTx.map { it.amount }
            val avgAmount = amounts.average()
            val maxDiffRatio = amounts.maxOf { abs(it - avgAmount) } / avgAmount

            if (maxDiffRatio > AMOUNT_TOLERANCE_PERCENT) {
                continue
            }

            // Calculate intervals between consecutive transactions in days
            val intervalsInDays = mutableListOf<Double>()
            for (i in 0 until sortedTx.size - 1) {
                val diffMs = sortedTx[i + 1].timestamp - sortedTx[i].timestamp
                val diffDays = diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                intervalsInDays.add(diffDays)
            }

            val avgIntervalDays = intervalsInDays.average()
            val (frequency, intervalNominalDays) = matchFrequency(avgIntervalDays) ?: continue

            // Determine confidence
            val isHighConfidence = sortedTx.size >= 4 && maxDiffRatio < 0.08
            val confidenceText = if (isHighConfidence) "High confidence" else "Medium confidence"

            val displayTitle = sortedTx.last().title

            candidates.add(
                DetectedRecurringCandidate(
                    merchant = displayTitle,
                    displayMerchant = displayTitle,
                    amount = sortedTx.last().amount,
                    frequency = frequency,
                    intervalDays = intervalNominalDays,
                    confidence = confidenceText,
                    lastTransactionTimestamp = sortedTx.last().timestamp,
                    matchingTransactionCount = sortedTx.size
                )
            )
        }

        return candidates
    }

    private fun matchFrequency(avgDays: Double): Pair<String, Int>? {
        return when (avgDays.toInt()) {
            in 5..9 -> Pair("Weekly", 7)
            in 10..18 -> Pair("Every 2 weeks", 14)
            in 24..36 -> Pair("Monthly", 30)
            in 80..100 -> Pair("Quarterly", 90)
            in 340..390 -> Pair("Yearly", 365)
            else -> null
        }
    }

    fun evaluateConfirmedStatus(
        confirmedEntities: List<RecurringExpenseEntity>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<ConfirmedRecurringStatus> {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())

        return confirmedEntities.map { entity ->
            val intervalMs = entity.intervalDays * 24 * 60 * 60 * 1000L
            val nextExpectedMs = entity.lastTransactionTimestamp + intervalMs

            // Grace period tolerance (e.g. 5 days after expected date)
            val gracePeriodMs = 5 * 24 * 60 * 60 * 1000L
            val isMissed = currentTimeMs > (nextExpectedMs + gracePeriodMs)

            val statusText = if (isMissed) {
                "No matching transaction detected yet"
            } else {
                "Next expected: around ${sdf.format(Date(nextExpectedMs))}"
            }

            ConfirmedRecurringStatus(
                entity = entity,
                nextExpectedTimestamp = nextExpectedMs,
                isMissed = isMissed,
                statusText = statusText
            )
        }
    }

    fun findMatchingConfirmedRecurring(
        newTx: Transaction,
        confirmedList: List<RecurringExpenseEntity>
    ): RecurringExpenseEntity? {
        if (newTx.type != TransactionType.EXPENSE) return null

        val normTxTitle = normalizeMerchantTitle(newTx.title)

        return confirmedList.find { entity ->
            val normEntityMerchant = normalizeMerchantTitle(entity.merchant)
            val merchantMatch = normTxTitle == normEntityMerchant ||
                    normTxTitle.contains(normEntityMerchant) ||
                    normEntityMerchant.contains(normTxTitle)

            val amountDiffRatio = abs(newTx.amount - entity.amount) / max(newTx.amount, entity.amount)
            merchantMatch && amountDiffRatio <= AMOUNT_TOLERANCE_PERCENT
        }
    }
}
