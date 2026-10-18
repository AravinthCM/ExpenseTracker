package com.example.expensetracker.service

import com.example.expensetracker.data.local.TransactionEntity
import kotlin.math.abs

enum class DuplicateConfidence {
    HIGH_CONFIDENCE,
    UNCERTAIN
}

data class SuspiciousDuplicateGroup(
    val groupKey: String,
    val confidence: DuplicateConfidence,
    val reason: String,
    val transactions: List<TransactionEntity>
)

object DuplicateReviewHelper {

    fun findDuplicates(transactions: List<TransactionEntity>): List<SuspiciousDuplicateGroup> {
        val result = mutableListOf<SuspiciousDuplicateGroup>()
        val processedIds = mutableSetOf<String>()

        // 1. Group by extracted or stored bank reference number
        val withRefs = transactions.map { tx ->
            val ref = tx.referenceNumber?.takeIf { it.isNotBlank() }
                ?: NotificationParser.extractReferenceNumber(tx.originalText)
            tx to ref
        }

        val refGroups = withRefs.filter { it.second != null }
            .groupBy { it.second!! }

        for ((ref, pairList) in refGroups) {
            if (pairList.size > 1) {
                val groupEntities = pairList.map { it.first }
                result.add(
                    SuspiciousDuplicateGroup(
                        groupKey = "REF_$ref",
                        confidence = DuplicateConfidence.HIGH_CONFIDENCE,
                        reason = "Exact match on bank reference number ($ref). Identical underlying financial event.",
                        transactions = groupEntities
                    )
                )
                processedIds.addAll(groupEntities.map { it.id })
            }
        }

        // 2. Identify remaining potential duplicates by amount, title, type, and time proximity
        val remaining = transactions.filterNot { processedIds.contains(it.id) }
        val remainingGrouped = remaining.groupBy { "${it.type}|${it.amount}|${it.title.trim().lowercase()}" }

        for ((_, group) in remainingGrouped) {
            if (group.size <= 1) continue

            // Sort by timestamp
            val sorted = group.sortedBy { it.timestamp }
            var currentCluster = mutableListOf(sorted[0])

            for (i in 1 until sorted.size) {
                val prev = currentCluster.last()
                val curr = sorted[i]
                val timeDiffMs = abs(curr.timestamp - prev.timestamp)

                if (timeDiffMs <= 2 * 60 * 1000L) { // Within 2-minute concurrency window
                    currentCluster.add(curr)
                } else {
                    if (currentCluster.size > 1) {
                        result.add(
                            SuspiciousDuplicateGroup(
                                groupKey = "TIME_${currentCluster[0].id}",
                                confidence = DuplicateConfidence.HIGH_CONFIDENCE,
                                reason = "Identical amount (₹${currentCluster[0].amount}), type, and merchant created within 2 minutes (${abs(currentCluster.last().timestamp - currentCluster.first().timestamp) / 1000}s apart). Typical concurrent notification race.",
                                transactions = currentCluster.toList()
                            )
                        )
                        processedIds.addAll(currentCluster.map { it.id })
                    }
                    currentCluster = mutableListOf(curr)
                }
            }

            if (currentCluster.size > 1) {
                result.add(
                    SuspiciousDuplicateGroup(
                        groupKey = "TIME_${currentCluster[0].id}",
                        confidence = DuplicateConfidence.HIGH_CONFIDENCE,
                        reason = "Identical amount (₹${currentCluster[0].amount}), type, and merchant created within 2 minutes (${abs(currentCluster.last().timestamp - currentCluster.first().timestamp) / 1000}s apart). Typical concurrent notification race.",
                        transactions = currentCluster.toList()
                    )
                )
                processedIds.addAll(currentCluster.map { it.id })
            }

            // Check if there are any same-day matches outside the 2-minute window
            val unclustered = group.filterNot { processedIds.contains(it.id) }
            val sameDayGroups = unclustered.groupBy { it.date }
            for ((date, dayList) in sameDayGroups) {
                if (dayList.size > 1) {
                    result.add(
                        SuspiciousDuplicateGroup(
                            groupKey = "SAMEDAY_${dayList[0].id}",
                            confidence = DuplicateConfidence.UNCERTAIN,
                            reason = "Identical amount (₹${dayList[0].amount}) and merchant on date '$date' without matching bank reference. May be legitimate repeated purchases.",
                            transactions = dayList
                        )
                    )
                    processedIds.addAll(dayList.map { it.id })
                }
            }
        }

        return result
    }
}
