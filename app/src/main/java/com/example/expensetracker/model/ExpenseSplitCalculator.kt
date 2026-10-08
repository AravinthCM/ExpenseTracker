package com.example.expensetracker.model

import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.RepaymentAllocationEntity
import com.example.expensetracker.data.local.RepaymentEntity
import com.example.expensetracker.data.local.RepaymentWithAllocations
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import java.util.UUID

sealed class SplitCalculationResult {
    data class Success(
        val split: ExpenseSplitEntity,
        val shares: List<ExpenseSplitShareEntity>
    ) : SplitCalculationResult()

    data class Error(val message: String) : SplitCalculationResult()
}

sealed class RepaymentRecordResult {
    data class Success(
        val repayment: RepaymentEntity,
        val allocations: List<RepaymentAllocationEntity>
    ) : RepaymentRecordResult()

    data class Error(val message: String) : RepaymentRecordResult()
}

data class MemberBalance(
    val memberId: String,
    val memberName: String,
    val amountOwedPaise: Long
)

object ExpenseSplitCalculator {

    fun rupeesToPaise(amountRupees: Double): Long = Math.round(amountRupees * 100.0)

    fun paiseToRupees(paise: Long): Double = paise / 100.0

    fun calculateEqualSplit(
        transaction: TransactionEntity,
        groupWithMembers: SplitGroupWithMembers
    ): SplitCalculationResult {
        if (!transaction.type.equals("EXPENSE", ignoreCase = true)) {
            return SplitCalculationResult.Error("Only EXPENSE transactions can be split.")
        }

        val members = groupWithMembers.members
        if (members.size < 2) {
            return SplitCalculationResult.Error("Split group must have at least 2 members.")
        }

        val meMembers = members.filter { it.isMe }
        if (meMembers.size != 1) {
            return SplitCalculationResult.Error("Split group must have exactly one 'Me' member.")
        }

        val totalPaise = rupeesToPaise(transaction.amount)
        if (totalPaise <= 0) {
            return SplitCalculationResult.Error("Transaction amount must be greater than zero.")
        }

        val payerMember = meMembers.first()
        val splitId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Deterministic remainder distribution: sort members by member.id ascending
        val sortedMembers = members.sortedBy { it.id }
        val numMembers = sortedMembers.size
        val baseSharePaise = totalPaise / numMembers
        val remainderPaise = (totalPaise % numMembers).toInt()

        val shares = sortedMembers.mapIndexed { index, member ->
            val extra = if (index < remainderPaise) 1L else 0L
            ExpenseSplitShareEntity(
                id = UUID.randomUUID().toString(),
                splitId = splitId,
                memberId = member.id,
                shareAmountPaise = baseSharePaise + extra
            )
        }

        val splitEntity = ExpenseSplitEntity(
            id = splitId,
            transactionId = transaction.id,
            groupId = groupWithMembers.group.id,
            amountPaise = totalPaise,
            payerMemberId = payerMember.id,
            createdAt = now
        )

        return SplitCalculationResult.Success(splitEntity, shares)
    }

    fun calculateCustomSplit(
        transaction: TransactionEntity,
        groupWithMembers: SplitGroupWithMembers,
        customSharesPaise: Map<String, Long>
    ): SplitCalculationResult {
        if (!transaction.type.equals("EXPENSE", ignoreCase = true)) {
            return SplitCalculationResult.Error("Only EXPENSE transactions can be split.")
        }

        val members = groupWithMembers.members
        if (members.size < 2) {
            return SplitCalculationResult.Error("Split group must have at least 2 members.")
        }

        val meMembers = members.filter { it.isMe }
        if (meMembers.size != 1) {
            return SplitCalculationResult.Error("Split group must have exactly one 'Me' member.")
        }

        val totalPaise = rupeesToPaise(transaction.amount)
        if (totalPaise <= 0) {
            return SplitCalculationResult.Error("Transaction amount must be greater than zero.")
        }

        // Verify member set matches customShares keys
        val groupMemberIds = members.map { it.id }.toSet()
        if (customSharesPaise.keys != groupMemberIds) {
            return SplitCalculationResult.Error("Every group member must have exactly one custom share allocated.")
        }

        // Verify shares > 0
        if (customSharesPaise.values.any { it <= 0 }) {
            return SplitCalculationResult.Error("Share amounts must be greater than zero.")
        }

        // Verify total sum equals transaction total
        val customSum = customSharesPaise.values.sum()
        if (customSum != totalPaise) {
            return SplitCalculationResult.Error("Sum of custom shares ($customSum paise) does not match expense total ($totalPaise paise).")
        }

        val payerMember = meMembers.first()
        val splitId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val shares = members.map { member ->
            val sharePaise = customSharesPaise[member.id]!!
            ExpenseSplitShareEntity(
                id = UUID.randomUUID().toString(),
                splitId = splitId,
                memberId = member.id,
                shareAmountPaise = sharePaise
            )
        }

        val splitEntity = ExpenseSplitEntity(
            id = splitId,
            transactionId = transaction.id,
            groupId = groupWithMembers.group.id,
            amountPaise = totalPaise,
            payerMemberId = payerMember.id,
            createdAt = now
        )

        return SplitCalculationResult.Success(splitEntity, shares)
    }

    fun calculateOutstandingBalances(
        splitsWithShares: List<ExpenseSplitWithShares>,
        members: List<SplitGroupMemberEntity>
    ): List<MemberBalance> {
        val memberMap = members.associateBy { it.id }
        val meMemberIds = members.filter { it.isMe }.map { it.id }.toSet()

        val balances = mutableMapOf<String, Long>()

        for (splitWithShares in splitsWithShares) {
            val payerId = splitWithShares.split.payerMemberId
            for (share in splitWithShares.shares) {
                // If the payer is Me, non-Me members owe their allocated shares to Me
                if (share.memberId != payerId && meMemberIds.contains(payerId)) {
                    val current = balances.getOrDefault(share.memberId, 0L)
                    balances[share.memberId] = current + share.shareAmountPaise
                }
            }
        }

        return members.filter { !it.isMe }.map { nonMeMember ->
            MemberBalance(
                memberId = nonMeMember.id,
                memberName = nonMeMember.name,
                amountOwedPaise = balances.getOrDefault(nonMeMember.id, 0L)
            )
        }
    }

    fun calculateRepaymentAllocation(
        groupId: String,
        memberId: String,
        repaymentAmountPaise: Long,
        paymentDate: String,
        linkedTransactionId: String? = null,
        note: String? = null,
        splitsWithShares: List<ExpenseSplitWithShares>,
        groupsWithMembers: List<SplitGroupWithMembers>,
        transactions: List<TransactionEntity>,
        repaymentsWithAllocations: List<RepaymentWithAllocations>
    ): RepaymentRecordResult {
        if (repaymentAmountPaise <= 0L) {
            return RepaymentRecordResult.Error("Repayment amount must be greater than zero.")
        }

        if (linkedTransactionId != null && repaymentsWithAllocations.any { it.repayment.linkedTransactionId == linkedTransactionId }) {
            return RepaymentRecordResult.Error("This transaction is already linked to another repayment record.")
        }

        val groupMap = groupsWithMembers.associateBy { it.group.id }
        val transactionMap = transactions.associateBy { it.id }
        val allMembersMap = groupsWithMembers.flatMap { it.members }.associateBy { it.id }

        val member = allMembersMap[memberId]
            ?: return RepaymentRecordResult.Error("Member not found.")

        // Map existing allocations by shareId
        val existingAllocationsByShareId = repaymentsWithAllocations
            .flatMap { it.allocations }
            .groupBy { it.shareId }
            .mapValues { entry -> entry.value.sumOf { it.allocatedAmountPaise } }

        data class CandidateShare(
            val share: ExpenseSplitShareEntity,
            val remainingShareDebt: Long,
            val txTimestamp: Long,
            val splitId: String
        )

        val candidateShares = mutableListOf<CandidateShare>()

        for (splitWithShares in splitsWithShares) {
            val split = splitWithShares.split
            if (split.groupId != groupId) continue
            val payerMember = allMembersMap[split.payerMemberId]
            if (payerMember == null || !payerMember.isMe) continue

            for (share in splitWithShares.shares) {
                if (share.memberId != memberId) continue
                val allocatedSoFar = existingAllocationsByShareId[share.id] ?: 0L
                val remainingDebt = share.shareAmountPaise - allocatedSoFar
                if (remainingDebt > 0L) {
                    val tx = transactionMap[split.transactionId]
                    val timestamp = tx?.timestamp ?: split.createdAt
                    candidateShares.add(CandidateShare(share, remainingDebt, timestamp, split.id))
                }
            }
        }

        // Sort candidate shares: oldest transaction timestamp first, tie-break by split.id asc
        candidateShares.sortWith(compareBy<CandidateShare> { it.txTimestamp }.thenBy { it.splitId })

        var remainingToAllocate = repaymentAmountPaise
        val newAllocations = mutableListOf<RepaymentAllocationEntity>()
        val repaymentId = UUID.randomUUID().toString()

        for (candidate in candidateShares) {
            if (remainingToAllocate <= 0L) break
            val amountToAllocate = minOf(remainingToAllocate, candidate.remainingShareDebt)
            newAllocations.add(
                RepaymentAllocationEntity(
                    id = UUID.randomUUID().toString(),
                    repaymentId = repaymentId,
                    shareId = candidate.share.id,
                    allocatedAmountPaise = amountToAllocate
                )
            )
            remainingToAllocate -= amountToAllocate
        }

        val unallocatedAmountPaise = remainingToAllocate // Excess overpayment

        val repaymentEntity = RepaymentEntity(
            id = repaymentId,
            groupId = groupId,
            memberId = memberId,
            amountPaise = repaymentAmountPaise,
            unallocatedAmountPaise = unallocatedAmountPaise,
            paymentDate = paymentDate,
            timestamp = System.currentTimeMillis(),
            linkedTransactionId = linkedTransactionId,
            note = note
        )

        return RepaymentRecordResult.Success(repaymentEntity, newAllocations)
    }

    fun calculateSettleUpLedger(
        splitsWithShares: List<ExpenseSplitWithShares>,
        groupsWithMembers: List<SplitGroupWithMembers>,
        transactions: List<TransactionEntity>,
        repaymentsWithAllocations: List<RepaymentWithAllocations> = emptyList()
    ): SettleUpLedger {
        val groupMap = groupsWithMembers.associateBy { it.group.id }
        val transactionMap = transactions.associateBy { it.id }
        val allMembersMap = groupsWithMembers.flatMap { it.members }.associateBy { it.id }

        val totalAllocatedByShareId = repaymentsWithAllocations
            .flatMap { it.allocations }
            .groupBy { it.shareId }
            .mapValues { entry -> entry.value.sumOf { it.allocatedAmountPaise } }

        val unallocatedByMemberId = repaymentsWithAllocations
            .groupBy { it.repayment.memberId }
            .mapValues { entry -> entry.value.sumOf { it.repayment.unallocatedAmountPaise } }

        val shareToTxTitleMap = mutableMapOf<String, String>()
        val memberItemizedMap = mutableMapOf<String, MutableList<ItemizedMemberExpense>>()

        for (splitWithShares in splitsWithShares) {
            val split = splitWithShares.split
            val payerMember = allMembersMap[split.payerMemberId]
            if (payerMember == null || !payerMember.isMe) continue

            val groupWithMembers = groupMap[split.groupId]
            val tx = transactionMap[split.transactionId]
            val groupName = groupWithMembers?.group?.name ?: "Group"
            val txTitle = tx?.title ?: "Expense"
            val txDate = tx?.date ?: ""

            for (share in splitWithShares.shares) {
                shareToTxTitleMap[share.id] = txTitle
                val shareMember = allMembersMap[share.memberId]
                if (shareMember == null || shareMember.isMe || share.memberId == split.payerMemberId) continue

                val allocatedPaise = totalAllocatedByShareId[share.id] ?: 0L
                val remainingPaise = maxOf(0L, share.shareAmountPaise - allocatedPaise)
                val isFullySettled = (remainingPaise == 0L)

                val list = memberItemizedMap.getOrPut(share.memberId) { mutableListOf() }
                list.add(
                    ItemizedMemberExpense(
                        splitId = split.id,
                        shareId = share.id,
                        transactionId = split.transactionId,
                        transactionTitle = txTitle,
                        transactionDate = txDate,
                        groupName = groupName,
                        originalShareAmountPaise = share.shareAmountPaise,
                        allocatedPaise = allocatedPaise,
                        remainingAmountPaise = remainingPaise,
                        isFullySettled = isFullySettled
                    )
                )
            }
        }

        val memberBalances = groupsWithMembers.flatMap { it.members }.filter { !it.isMe }.mapNotNull { member ->
            val group = groupMap[member.groupId] ?: return@mapNotNull null
            val itemizedList = memberItemizedMap[member.id] ?: emptyList()
            val totalOwedPaise = itemizedList.sumOf { it.remainingAmountPaise }
            val unallocatedExcess = unallocatedByMemberId[member.id] ?: 0L

            if (totalOwedPaise <= 0L && unallocatedExcess <= 0L) return@mapNotNull null

            MemberDebtSummary(
                memberId = member.id,
                memberName = member.name,
                groupId = group.group.id,
                groupName = group.group.name,
                totalOwedPaise = totalOwedPaise,
                unallocatedExcessPaise = unallocatedExcess,
                itemizedExpenses = itemizedList.filter { !it.isFullySettled }
            )
        }.sortedByDescending { it.totalOwedPaise }

        val totalOutstandingPaise = memberBalances.sumOf { it.totalOwedPaise }
        val membersWhoOweCount = memberBalances.count { it.totalOwedPaise > 0L }
        val groupsWithDebtCount = memberBalances.filter { it.totalOwedPaise > 0L }.map { it.groupId }.distinct().size

        val splitHistory = splitsWithShares.map { splitWithShares ->
            val split = splitWithShares.split
            val groupWithMembers = groupMap[split.groupId]
            val tx = transactionMap[split.transactionId]

            val memberShares = splitWithShares.shares.map { share ->
                val mName = allMembersMap[share.memberId]?.name ?: "Member"
                val allocated = totalAllocatedByShareId[share.id] ?: 0L
                val remaining = maxOf(0L, share.shareAmountPaise - allocated)
                MemberShareHistoryStatus(
                    memberName = mName,
                    originalSharePaise = share.shareAmountPaise,
                    allocatedPaise = allocated,
                    remainingSharePaise = remaining,
                    isFullySettled = (remaining == 0L)
                )
            }

            SplitHistoryItem(
                splitId = split.id,
                transactionId = split.transactionId,
                transactionTitle = tx?.title ?: "Expense",
                transactionDate = tx?.date ?: "",
                transactionAmountPaise = split.amountPaise,
                groupName = groupWithMembers?.group?.name ?: "Group",
                memberShares = memberShares
            )
        }

        val repaymentHistory = repaymentsWithAllocations.map { repaymentWithAllocations ->
            val repayment = repaymentWithAllocations.repayment
            val group = groupMap[repayment.groupId]
            val member = allMembersMap[repayment.memberId]
            val linkedTx = repayment.linkedTransactionId?.let { transactionMap[it] }

            val itemizedAllocations = repaymentWithAllocations.allocations.map { alloc ->
                val title = shareToTxTitleMap[alloc.shareId] ?: "Shared Expense"
                Pair(title, alloc.allocatedAmountPaise)
            }

            RepaymentHistoryItem(
                repaymentId = repayment.id,
                memberId = repayment.memberId,
                memberName = member?.name ?: "Member",
                groupId = repayment.groupId,
                groupName = group?.group?.name ?: "Group",
                amountPaise = repayment.amountPaise,
                unallocatedAmountPaise = repayment.unallocatedAmountPaise,
                paymentDate = repayment.paymentDate,
                timestamp = repayment.timestamp,
                linkedTransactionTitle = linkedTx?.title,
                note = repayment.note,
                itemizedAllocations = itemizedAllocations
            )
        }

        return SettleUpLedger(
            totalOutstandingPaise = totalOutstandingPaise,
            membersWhoOweCount = membersWhoOweCount,
            groupsWithDebtCount = groupsWithDebtCount,
            memberBalances = memberBalances,
            splitHistory = splitHistory,
            repaymentHistory = repaymentHistory
        )
    }

    fun calculateSettleUpLedgerFromDomain(
        splitsWithShares: List<ExpenseSplitWithShares>,
        groupsWithMembers: List<SplitGroupWithMembers>,
        transactions: List<com.example.expensetracker.model.Transaction>,
        repaymentsWithAllocations: List<RepaymentWithAllocations> = emptyList()
    ): SettleUpLedger {
        val domainAsEntities = transactions.map { tx ->
            TransactionEntity(
                id = tx.id,
                title = tx.title,
                amount = tx.amount,
                type = tx.type.name,
                category = tx.category.name,
                date = tx.date,
                source = tx.source.name,
                timestamp = tx.timestamp,
                originalText = tx.originalText
            )
        }
        return calculateSettleUpLedger(splitsWithShares, groupsWithMembers, domainAsEntities, repaymentsWithAllocations)
    }
}

data class ItemizedMemberExpense(
    val splitId: String,
    val shareId: String,
    val transactionId: String,
    val transactionTitle: String,
    val transactionDate: String,
    val groupName: String,
    val originalShareAmountPaise: Long,
    val allocatedPaise: Long,
    val remainingAmountPaise: Long,
    val isFullySettled: Boolean
)

data class MemberDebtSummary(
    val memberId: String,
    val memberName: String,
    val groupId: String,
    val groupName: String,
    val totalOwedPaise: Long,
    val unallocatedExcessPaise: Long = 0L,
    val itemizedExpenses: List<ItemizedMemberExpense>
)

data class MemberShareHistoryStatus(
    val memberName: String,
    val originalSharePaise: Long,
    val allocatedPaise: Long,
    val remainingSharePaise: Long,
    val isFullySettled: Boolean
)

data class SplitHistoryItem(
    val splitId: String,
    val transactionId: String,
    val transactionTitle: String,
    val transactionDate: String,
    val transactionAmountPaise: Long,
    val groupName: String,
    val memberShares: List<MemberShareHistoryStatus>
)

data class RepaymentHistoryItem(
    val repaymentId: String,
    val memberId: String,
    val memberName: String,
    val groupId: String,
    val groupName: String,
    val amountPaise: Long,
    val unallocatedAmountPaise: Long,
    val paymentDate: String,
    val timestamp: Long,
    val linkedTransactionTitle: String?,
    val note: String?,
    val itemizedAllocations: List<Pair<String, Long>>
)

data class SettleUpLedger(
    val totalOutstandingPaise: Long,
    val membersWhoOweCount: Int,
    val groupsWithDebtCount: Int,
    val memberBalances: List<MemberDebtSummary>,
    val splitHistory: List<SplitHistoryItem>,
    val repaymentHistory: List<RepaymentHistoryItem>
)
