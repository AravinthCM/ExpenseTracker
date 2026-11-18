package com.example.expensetracker.service

import android.content.Context
import android.util.Log
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.data.cloud.WorkManagerSyncScheduler
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
        originalText = originalText,
        accountId = accountId,
        referenceNumber = referenceNumber
    )
}

object TransactionDeduplicator {

    private const val TAG = "ExpenseTrackerNotification"
    private val lock = Any()
    var DEDUPLICATION_WINDOW_MS = 2 * 60 * 1000L // 2 minutes window

    private data class TrackedEntry(
        val primaryTransaction: NormalizedTransaction,
        val sources: MutableSet<String> = mutableSetOf(primaryTransaction.source)
    )

    private val recentTransactions = mutableListOf<TrackedEntry>()

    fun clearHistory() {
        synchronized(lock) {
            recentTransactions.clear()
        }
    }

    fun processAndSave(
        db: AppDatabase,
        transaction: NormalizedTransaction,
        mappedCategory: String = "Other",
        context: Context? = null
    ): DeduplicationResult {
        return synchronized(lock) {
            try {
                // 1. Stable Reference Number Check (Highest confidence, independent of time window)
                val ref = transaction.referenceNumber?.trim()
                if (!ref.isNullOrBlank()) {
                    val refMatch = db.transactionDao().getTransactionByReferenceSync(ref)
                    if (refMatch != null) {
                        val timeDiffMs = abs(transaction.timestamp - refMatch.timestamp)
                        val timeDiffSec = timeDiffMs / 1000

                        Log.d(
                            TAG,
                            """
                            🔄 DUPLICATE TRANSACTION DETECTED BY BANK REFERENCE
                            Reference: $ref
                            Amount   : ₹${transaction.amount}
                            Type     : ${transaction.type.name}
                            Source   : ${transaction.source} matched ${refMatch.source} (Room)
                            Timestamp difference: ${timeDiffSec}s
                            """.trimIndent()
                        )

                        val existingNormalized = NormalizedTransaction(
                            amount = refMatch.amount,
                            name = refMatch.title,
                            type = transaction.type,
                            source = refMatch.source,
                            timestamp = refMatch.timestamp,
                            originalText = refMatch.originalText,
                            referenceNumber = refMatch.referenceNumber
                        )

                        val result = DeduplicationResult.DuplicateTransaction(
                            existingTransaction = existingNormalized,
                            newTransaction = transaction,
                            timeDifferenceMs = timeDiffMs,
                            confirmedSources = listOf(refMatch.source, transaction.source)
                        )
                        Log.d(TAG, "DEDUP_RESULT: Duplicate detected by reference $ref. Matched source=${refMatch.source} with ${transaction.source}")
                        return@synchronized result
                    }
                }

                // 2. Query Room for recent transactions in time window (fallback for missing reference)
                val minTime = transaction.timestamp - DEDUPLICATION_WINDOW_MS
                val maxTime = transaction.timestamp + DEDUPLICATION_WINDOW_MS
                val roomEntities = db.transactionDao().getTransactionsInWindow(minTime, maxTime)

                // 3. Check for duplicate match in Room
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
                        originalText = roomMatch.originalText,
                        referenceNumber = roomMatch.referenceNumber,
                        accountId = roomMatch.accountId
                    )

                    val result = DeduplicationResult.DuplicateTransaction(
                        existingTransaction = existingNormalized,
                        newTransaction = transaction,
                        timeDifferenceMs = timeDiffMs,
                        confirmedSources = listOf(roomMatch.source, transaction.source)
                    )
                    Log.d(TAG, "DEDUP_RESULT: Duplicate detected. Matched source=${roomMatch.source} with ${transaction.source}")
                    return@synchronized result
                }

                // 4. Check in-memory deduplicator history as fallback
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
                    return@synchronized result
                }

                // 5. No duplicate found -> Process NEW TRANSACTION
                Log.d(TAG, "DEDUP_RESULT: New transaction. Amount=${transaction.amount} Name=${transaction.name} Source=${transaction.source} Ref=${transaction.referenceNumber}")
                Log.d(
                    TAG,
                    """
                    🆕 NEW TRANSACTION PROCESSED
                    Amount   : ₹${transaction.amount}
                    Type     : ${transaction.type.name}
                    Name     : ${transaction.name}
                    Category : $mappedCategory
                    Source   : ${transaction.source}
                    Reference: ${transaction.referenceNumber ?: "(none)"}
                    Timestamp: ${transaction.timestamp}
                    """.trimIndent()
                )

                // Convert and Save into Room database.
                // Strictly preserve accountId; never blindly assign to primary account if accountId is unresolved.
                val rawEntity = transaction.toEntity(mappedCategory)
                val entityToSave = rawEntity

                val insertedRowId = db.transactionDao().insertTransaction(entityToSave)

                if (insertedRowId == -1L) {
                    // Database-level uniqueness constraint prevented duplicate insertion
                    Log.w(TAG, "Room insert was ignored due to database constraint on ref: ${entityToSave.referenceNumber}")
                    val existing = if (!entityToSave.referenceNumber.isNullOrBlank()) {
                        db.transactionDao().getTransactionByReferenceSync(entityToSave.referenceNumber)
                    } else null

                    val existingNormalized = if (existing != null) {
                        NormalizedTransaction(
                            amount = existing.amount,
                            name = existing.title,
                            type = transaction.type,
                            source = existing.source,
                            timestamp = existing.timestamp,
                            originalText = existing.originalText,
                            referenceNumber = existing.referenceNumber,
                            accountId = existing.accountId
                        )
                    } else transaction

                    return@synchronized DeduplicationResult.DuplicateTransaction(
                        existingTransaction = existingNormalized,
                        newTransaction = transaction,
                        timeDifferenceMs = 0L,
                        confirmedSources = listOf(transaction.source)
                    )
                }

                context?.let { WorkManagerSyncScheduler(it).scheduleSync() }

                Log.d(TAG, "ROOM_SAVE_RESULT: Saved entity ID=${entityToSave.id} Title='${entityToSave.title}' Amount=₹${entityToSave.amount} Source=${entityToSave.source} Ref=${entityToSave.referenceNumber}")
                Log.d(
                    TAG,
                    """
                    💾 TRANSACTION SAVED TO ROOM
                    ID       : ${entityToSave.id}
                    Title    : ${entityToSave.title}
                    Amount   : ₹${entityToSave.amount}
                    Category : ${entityToSave.category}
                    Reference: ${entityToSave.referenceNumber ?: "(none)"}
                    """.trimIndent()
                )

                // Show notification ONLY after successful Room insertion
                context?.let { ctx ->
                    TransactionNotificationHelper.showTransactionAddedNotification(
                        context = ctx,
                        type = transaction.type,
                        amount = transaction.amount,
                        name = transaction.name,
                        source = transaction.source,
                        balance = transaction.balance
                    )
                }

                // Add to in-memory history
                recentTransactions.add(TrackedEntry(transaction))
                pruneOldEntries(transaction.timestamp)

                DeduplicationResult.NewTransaction(transaction)

            } catch (e: Exception) {
                Log.e(TAG, "⚠️ TRANSACTION SAVE FAILED", e)
                throw e
            }
        }
    }

    fun process(transaction: NormalizedTransaction): DeduplicationResult {
        return synchronized(lock) {
            // Find duplicate match in recent transactions
            val match = recentTransactions.findLast { entry ->
                isDuplicateMatch(entry.primaryTransaction, transaction)
            }

            if (match != null) {
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
                    Reference: ${transaction.referenceNumber ?: "(none)"}
                    Timestamp: ${transaction.timestamp}
                    """.trimIndent()
                )

                DeduplicationResult.NewTransaction(transaction)
            }
        }
    }

    private fun isDuplicateMatch(
        existing: NormalizedTransaction,
        candidate: NormalizedTransaction
    ): Boolean {
        // If both transactions have bank reference numbers, compare them directly
        if (!existing.referenceNumber.isNullOrBlank() && !candidate.referenceNumber.isNullOrBlank()) {
            return existing.referenceNumber.equals(candidate.referenceNumber, ignoreCase = true)
        }
        // If both have different non-empty reference numbers, they are distinct
        if (!existing.referenceNumber.isNullOrBlank() && !candidate.referenceNumber.isNullOrBlank() &&
            !existing.referenceNumber.equals(candidate.referenceNumber, ignoreCase = true)
        ) {
            return false
        }

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
        // If both transactions have bank reference numbers, compare them directly
        if (!entity.referenceNumber.isNullOrBlank() && !candidate.referenceNumber.isNullOrBlank()) {
            return entity.referenceNumber.equals(candidate.referenceNumber, ignoreCase = true)
        }
        // If both have different non-empty reference numbers, they are distinct
        if (!entity.referenceNumber.isNullOrBlank() && !candidate.referenceNumber.isNullOrBlank() &&
            !entity.referenceNumber.equals(candidate.referenceNumber, ignoreCase = true)
        ) {
            return false
        }

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
