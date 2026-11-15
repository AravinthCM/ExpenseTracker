package com.example.expensetracker.model

import com.example.expensetracker.data.local.GPayMemberMappingEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers

/** Matches a GPay request's requester/group text against existing Split Groups and Members. */
object GPayRequestMatcher {

    fun normalizeName(name: String): String =
        name.trim().lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    sealed class GroupMatchResult {
        data class Matched(val group: SplitGroupWithMembers) : GroupMatchResult()
        object Ambiguous : GroupMatchResult()
        object NotFound : GroupMatchResult()
    }

    /** Prefers the group explicitly named in the notification; never invents/creates one. */
    fun matchGroup(groupNameRaw: String?, groups: List<SplitGroupWithMembers>): GroupMatchResult {
        if (groupNameRaw.isNullOrBlank()) return GroupMatchResult.NotFound
        val normalizedTarget = normalizeName(groupNameRaw)
        if (normalizedTarget.isBlank()) return GroupMatchResult.NotFound
        val candidates = groups.filter { normalizeName(it.group.name) == normalizedTarget }
        return when (candidates.size) {
            0 -> GroupMatchResult.NotFound
            1 -> GroupMatchResult.Matched(candidates[0])
            else -> GroupMatchResult.Ambiguous
        }
    }

    sealed class MemberMatchResult {
        data class Matched(val member: SplitGroupMemberEntity) : MemberMatchResult()
        object Ambiguous : MemberMatchResult()
        object NotFound : MemberMatchResult()
    }

    /**
     * Priority 1: an existing confirmed requester->member mapping for this group.
     * Priority 2: a reliable normalized display-name match within the group (excluding "Me").
     * Never guesses on partial/ambiguous matches.
     */
    fun matchMember(
        requesterRawName: String,
        group: SplitGroupWithMembers,
        confirmedMappings: List<GPayMemberMappingEntity>
    ): MemberMatchResult {
        val normalizedRequester = normalizeName(requesterRawName)

        val mapping = confirmedMappings.firstOrNull {
            it.groupId == group.group.id && it.normalizedRequesterName == normalizedRequester
        }
        if (mapping != null) {
            val mappedMember = group.members.firstOrNull { it.id == mapping.memberId }
            if (mappedMember != null) return MemberMatchResult.Matched(mappedMember)
        }

        val candidates = group.members.filter { !it.isMe && normalizeName(it.name) == normalizedRequester }
        return when (candidates.size) {
            0 -> MemberMatchResult.NotFound
            1 -> MemberMatchResult.Matched(candidates[0])
            else -> MemberMatchResult.Ambiguous
        }
    }
}
