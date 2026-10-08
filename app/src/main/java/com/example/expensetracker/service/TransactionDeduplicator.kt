package com.example.expensetracker.service

import android.content.Context
import android.util.Log
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionType
import java.util.UUID
import kotlin.math.abs

sealed class DeduplicationResult {
    data class NewTransaction(
        val transaction: NormalizedTransaction
    ) : DeduplicationResult()

    data class DuplicateTransaction(
        val existingTransaction: NormalizedTransaction,
        val newTransaction: NormalizedTransaction,
        val timeDifferenceMs: Long,
        val confirmedSources: List<String>
    ) : DeduplicationResult()
}

fun NormalizedTransaction.toEntity(mappedCategory: String = "Other"): TransactionEntity {
    return TransactionEntity(
        id = UUID.randomUUID().toString(),
        title = name,
        amount = amount,
        type = type.name,
        category = mappedCategory,
        date = "Today",
        source = source,
        timestamp = timestamp,
        originalText = originalText
    )
}

object TransactionDeduplicator {

    private const val TAG = "ExpenseTrackerNotification"
    var DEDUPLICATION_WINDOW_MS = 2 * 60 * 1000L // 2 minutes window

    private data class TrackedEntry(
        val primaryTransaction: NormalizedTransaction,
        val sources: MutableSet<String> = mutableSetOf(primaryTransaction.source)
    )

    private val recentTransactions = mutableListOf<TrackedEntry>()

    fun clearHistory() {
        recentTransactions.clear()
    }

    fun processAndSave(
        db: AppDatabase,
        transaction: NormalizedTransaction,
        mappedCategory: String = "Other",
        context: Context? = null
    ): DeduplicationResult {
        try {
            // 1. Query Room for recent transactions in time window
            val minTime = transaction.timestamp - DEDUPLICATION_WINDOW_MS
            val maxTime = transaction.timestamp + DEDUPLICATION_WINDOW_MS
            val roomEntities = db.transactionDao().getTransactionsInWindow(minTime, maxTime)

            // 2. Check for duplicate match in Room
            val roomMatch = roomEntities.find { entity ->
                isDuplicateMatchWithEntity(entity, transaction)
            }

            if (roomMatch != null) {
                val timeDiffMs = abs(transaction.timestamp - roomMatch.timestamp)
                val timeDiffSec = timeDiffMs / 1000

                Log.d(
                    TAG,
                    """
                    🔄 DUPLICATE TRANSACTION DETECTED
                    Amount : ₹${transaction.amount}
                    Type   : ${transaction.type.name}
                    Source : ${transaction.source} matched ${roomMatch.source} (Room)
                    Timestamp difference: ${timeDiffSec}s
                    """.trimIndent()
                )

                val existingNormalized = NormalizedTransaction(
                    amount = roomMatch.amount,
                    name = roomMatch.title,
                    type = transaction.type,
                    source = roomMatch.source,
                    timestamp = roomMatch.timestamp,
                    originalText = ""
                )

                val result = DeduplicationResult.DuplicateTransaction(
                    existingTransaction = existingNormalized,
                    newTransaction = transaction,
                    timeDifferenceMs = timeDiffMs,
                    confirmedSources = listOf(roomMatch.source, transaction.source)
                )
                Log.d(TAG, "DEDUP_RESULT: Duplicate detected. Matched source=${roomMatch.source} with ${transaction.source}")
                return result
            }

            // 3. Check in-memory deduplicator history as fallback
            val memMatch = recentTransactions.findLast { entry ->
                isDuplicateMatch(entry.primaryTransaction, transaction)
            }

            if (memMatch != null) {
                val timeDiffMs = abs(transaction.timestamp - memMatch.primaryTransaction.timestamp)
                memMatch.sources.add(transaction.source)
                val timeDiffSec = timeDiffMs / 1000

                Log.d(
                    TAG,
                    """
                    🔄 DUPLICATE TRANSACTION DETECTED
                    Amount : ₹${transaction.amount}
                    Type   : ${transaction.type.name}
                    Source : ${transaction.source} matched ${memMatch.primaryTransaction.source} (Memory)
                    Timestamp difference: ${timeDiffSec}s
                    """.trimIndent()
                )

                val result = DeduplicationResult.DuplicateTransaction(
                    existingTransaction = memMatch.primaryTransaction,
                    newTransaction = transaction,
                    timeDifferenceMs = timeDiffMs,
                    confirmedSources = memMatch.sources.toList()
                )
                Log.d(TAG, "DEDUP_RESULT: Duplicate detected in memory. Sources=${memMatch.sources}")
                return result
            }

            // 4. No duplicate found -> Process NEW TRANSACTION
            Log.d(TAG, "DEDUP_RESULT: New transaction. Amount=${transaction.amount} Name=${transaction.name} Source=${transaction.source}")
            Log.d(
                TAG,
                """
                🆕 NEW TRANSACTION PROCESSED
                Amount   : ₹${transaction.amount}
                Type     : ${transaction.type.name}
                Name     : ${transaction.name}
                Category : $mappedCategory
                Source   : ${transaction.source}
                Timestamp: ${transaction.timestamp}
                """.trimIndent()
            )

            // Convert and Save into Room database
            val entityToSave = transaction.toEntity(mappedCategory)
            db.transactionDao().insertTransaction(entityToSave)

            Log.d(TAG, "ROOM_SAVE_RESULT: Saved entity ID=${entityToSave.id} Title='${entityToSave.title}' Amount=₹${entityToSave.amount} Source=${entityToSave.source}")
            Log.d(
                TAG,
                """
                💾 TRANSACTION SAVED TO ROOM
                ID       : ${entityToSave.id}
                Title    : ${entityToSave.title}
                Amount   : ₹${entityToSave.amount}
                Category : ${entityToSave.category}
                """.trimIndent()
            )

            // Show notification ONLY after successful Room insertion
            context?.let { ctx ->
                TransactionNotificationHelper.showTransactionAddedNotification(
                    context = ctx,
                    type = transaction.type,
                    amount = transaction.amount,
                    name = transaction.name,
                    source = transaction.source
                )
            }

            // Add to in-memory history
            recentTransactions.add(TrackedEntry(transaction))
            pruneOldEntries(transaction.timestamp)

            return DeduplicationResult.NewTransaction(transaction)

        } catch (e: Exception) {
            Log.e(TAG, "⚠️ TRANSACTION SAVE FAILED", e)
            throw e
        }
    }

    fun process(transaction: NormalizedTransaction): DeduplicationResult {
        // Find duplicate match in recent transactions
        val match = recentTransactions.findLast { entry ->
            isDuplicateMatch(entry.primaryTransaction, transaction)
        }

        return if (match != null) {
            val timeDiffMs = abs(transaction.timestamp - match.primaryTransaction.timestamp)
            match.sources.add(transaction.source)

            val timeDiffSec = timeDiffMs / 1000
            Log.d(
                TAG,
                """
                🔄 DUPLICATE TRANSACTION DETECTED
                Amount : ₹${transaction.amount}
                Type   : ${transaction.type.name}
                Source : ${transaction.source} matched ${match.primaryTransaction.source}
                Timestamp difference: ${timeDiffSec}s
                """.trimIndent()
            )

            DeduplicationResult.DuplicateTransaction(
                existingTransaction = match.primaryTransaction,
                newTransaction = transaction,
                timeDifferenceMs = timeDiffMs,
                confirmedSources = match.sources.toList()
            )
        } else {
            recentTransactions.add(TrackedEntry(transaction))
            pruneOldEntries(transaction.timestamp)

            Log.d(
                TAG,
                """
                🆕 NEW TRANSACTION PROCESSED
                Amount   : ₹${transaction.amount}
                Type     : ${transaction.type.name}
                Name     : ${transaction.name}
                Source   : ${transaction.source}
                Timestamp: ${transaction.timestamp}
                """.trimIndent()
            )

            DeduplicationResult.NewTransaction(transaction)
        }
    }

    private fun isDuplicateMatch(
        existing: NormalizedTransaction,
        candidate: NormalizedTransaction
    ): Boolean {
        if (existing.type != candidate.type) return false
        if (abs(existing.amount - candidate.amount) >= 0.001) return false
        val timeDiff = abs(candidate.timestamp - existing.timestamp)
        if (timeDiff > DEDUPLICATION_WINDOW_MS) return false
        if (existing.source.equals(candidate.source, ignoreCase = true)) {
            if (existing.name.isNotBlank() && candidate.name.isNotBlank() &&
                !existing.name.equals(candidate.name, ignoreCase = true)
            ) {
                return false
            }
        }
        return true
    }

    private fun isDuplicateMatchWithEntity(
        entity: TransactionEntity,
        candidate: NormalizedTransaction
    ): Boolean {
        if (!entity.type.equals(candidate.type.name, ignoreCase = true)) return false
        if (abs(entity.amount - candidate.amount) >= 0.001) return false
        val timeDiff = abs(candidate.timestamp - entity.timestamp)
        if (timeDiff > DEDUPLICATION_WINDOW_MS) return false
        if (entity.source.equals(candidate.source, ignoreCase = true)) {
            if (entity.title.isNotBlank() && candidate.name.isNotBlank() &&
                !entity.title.equals(candidate.name, ignoreCase = true)
            ) {
                return false
            }
        }
        return true
    }

    private fun pruneOldEntries(currentTimestamp: Long) {
        val maxRetentionMs = 10 * 60 * 1000L
        recentTransactions.removeAll { entry ->
            abs(currentTimestamp - entry.primaryTransaction.timestamp) > maxRetentionMs
        }
    }
}
