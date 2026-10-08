package com.example.expensetracker.model

import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
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
}
