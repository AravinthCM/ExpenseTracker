package com.example.expensetracker.model

import com.example.expensetracker.data.local.GPayMemberMappingEntity
import com.example.expensetracker.data.local.GPayRequestEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.service.GPayRequestParser

/** Orchestrates capturing/updating GPay requests and matching them to groups/members, without ever writing to the ledger directly. */
object GPayRequestProcessor {

    data class MatchOutcome(
        val matchedGroupId: String?,
        val matchedMemberId: String?,
        val matchStatus: String
    )

    data class CaptureResult(
        val entity: GPayRequestEntity,
        val isNew: Boolean,
        /** True when an already-processed (non-pending) request with the same content was received again. */
        val isRepeatOfProcessed: Boolean
    )

    fun resolveMatch(
        parsed: GPayRequestParser.ParsedGPayRequest,
        groups: List<SplitGroupWithMembers>,
        memberMappings: List<GPayMemberMappingEntity>
    ): MatchOutcome = resolveMatch(parsed.groupNameRaw, parsed.requesterRawName, groups, memberMappings)

    fun resolveMatch(
        groupNameRaw: String?,
        requesterRawName: String,
        groups: List<SplitGroupWithMembers>,
        memberMappings: List<GPayMemberMappingEntity>
    ): MatchOutcome {
        val groupResult = GPayRequestMatcher.matchGroup(groupNameRaw, groups)
        val group = when (groupResult) {
            is GPayRequestMatcher.GroupMatchResult.Matched -> groupResult.group
            GPayRequestMatcher.GroupMatchResult.Ambiguous ->
                return MatchOutcome(null, null, GPayMatchStatus.AMBIGUOUS_GROUP)
            GPayRequestMatcher.GroupMatchResult.NotFound ->
                return MatchOutcome(null, null, GPayMatchStatus.NEEDS_GROUP)
        }

        return when (val memberResult = GPayRequestMatcher.matchMember(requesterRawName, group, memberMappings)) {
            is GPayRequestMatcher.MemberMatchResult.Matched ->
                MatchOutcome(group.group.id, memberResult.member.id, GPayMatchStatus.AUTO_MATCHED)
            GPayRequestMatcher.MemberMatchResult.Ambiguous ->
                MatchOutcome(group.group.id, null, GPayMatchStatus.AMBIGUOUS_MEMBER)
            GPayRequestMatcher.MemberMatchResult.NotFound ->
                MatchOutcome(group.group.id, null, GPayMatchStatus.NEEDS_MEMBER)
        }
    }

    /**
     * Upserts by [GPayRequestParser.ParsedGPayRequest.requestKey]: a repeated notification for the
     * same request (onNotificationPosted fired again, notification updated, or dismissed+reposted
     * with identical content same day) updates the existing row in place instead of creating a new one.
     */
    fun captureOrUpdate(
        parsed: GPayRequestParser.ParsedGPayRequest,
        packageName: String,
        notificationKey: String?,
        originalText: String,
        existingRequests: List<GPayRequestEntity>,
        groups: List<SplitGroupWithMembers>,
        memberMappings: List<GPayMemberMappingEntity>
    ): CaptureResult {
        val existing = existingRequests.firstOrNull { it.requestKey == parsed.requestKey }
        if (existing != null) {
            val updated = existing.copy(
                notificationKey = notificationKey ?: existing.notificationKey,
                originalText = originalText,
                updatedAt = System.currentTimeMillis()
            )
            return CaptureResult(
                entity = updated,
                isNew = false,
                isRepeatOfProcessed = existing.processingStatus != GPayProcessingStatus.PENDING
            )
        }

        val match = resolveMatch(parsed, groups, memberMappings)
        val entity = GPayRequestEntity(
            requestKey = parsed.requestKey,
            packageName = packageName,
            notificationKey = notificationKey,
            requesterRawName = parsed.requesterRawName,
            requesterNormalizedName = GPayRequestMatcher.normalizeName(parsed.requesterRawName),
            amountPaise = parsed.amountPaise,
            description = parsed.description,
            groupNameRaw = parsed.groupNameRaw,
            originalText = originalText,
            postTime = parsed.postTime,
            matchedGroupId = match.matchedGroupId,
            matchedMemberId = match.matchedMemberId,
            matchStatus = match.matchStatus
        )
        return CaptureResult(entity, isNew = true, isRepeatOfProcessed = false)
    }

    /**
     * Finds a pending/added GPay request that a freshly captured GPay payment transaction likely
     * settles (same requester + same amount), so the user can be offered to confirm a repayment.
     * This never records a repayment by itself.
     */
    fun findMatchingRequestForPaymentConfirmation(
        requests: List<GPayRequestEntity>,
        counterpartyName: String,
        amountPaise: Long
    ): GPayRequestEntity? {
        val normalizedName = GPayRequestMatcher.normalizeName(counterpartyName)
        val candidates = requests.filter {
            it.requesterNormalizedName == normalizedName &&
                it.amountPaise == amountPaise &&
                it.paymentConfirmationTransactionId == null &&
                it.repaymentId == null &&
                it.processingStatus != GPayProcessingStatus.DISMISSED
        }
        return candidates.singleOrNull()
    }
}

sealed class GPayExpenseCreationResult {
    data class Success(
        val transaction: TransactionEntity,
        val split: com.example.expensetracker.data.local.ExpenseSplitEntity,
        val shares: List<com.example.expensetracker.data.local.ExpenseSplitShareEntity>
    ) : GPayExpenseCreationResult()

    data class Error(val message: String) : GPayExpenseCreationResult()
}

/** Builds the group expense (transaction + split + shares) implied by a confidently-matched GPay request. */
object GPayExpenseCreator {

    fun createFromRequest(
        request: GPayRequestEntity,
        group: SplitGroupWithMembers
    ): GPayExpenseCreationResult {
        if (request.matchStatus != GPayMatchStatus.AUTO_MATCHED || request.matchedMemberId == null) {
            return GPayExpenseCreationResult.Error("Request is not confidently matched to a group member yet.")
        }
        if (request.processingStatus != GPayProcessingStatus.PENDING) {
            return GPayExpenseCreationResult.Error("Request has already been processed.")
        }
        if (group.group.id != request.matchedGroupId) {
            return GPayExpenseCreationResult.Error("Group does not match the request's resolved group.")
        }

        val amountRupees = request.amountPaise / 100.0
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        val transaction = TransactionEntity(
            id = java.util.UUID.randomUUID().toString(),
            title = request.description.ifBlank { "GPay request from ${request.requesterRawName}" },
            amount = amountRupees,
            type = "EXPENSE",
            category = "OTHER",
            date = dateFormat.format(java.util.Date(request.postTime)),
            source = "GPAY",
            timestamp = request.postTime,
            originalText = request.originalText,
            accountId = null,
            // The requester paid for this out of their own money, not from the user's bank account.
            balanceIncludedInAccount = true
        )

        return when (val result = ExpenseSplitCalculator.calculateEqualSplitForPayer(transaction, group, request.matchedMemberId)) {
            is SplitCalculationResult.Success -> GPayExpenseCreationResult.Success(transaction, result.split, result.shares)
            is SplitCalculationResult.Error -> GPayExpenseCreationResult.Error(result.message)
        }
    }
}
