package com.example.expensetracker.model

import com.example.expensetracker.data.local.GPayMemberMappingEntity
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GPayRequestMatcherTest {

    private fun group(
        id: String = "g1",
        name: String = "Room expense",
        members: List<SplitGroupMemberEntity>
    ) = SplitGroupWithMembers(SplitGroupEntity(id = id, name = name), members)

    @Test
    fun normalizeNameCollapsesCaseAndPunctuation() {
        assertEquals("abdul ajeez", GPayRequestMatcher.normalizeName("  Abdul  Ajeez! "))
    }

    @Test
    fun matchesMemberByNormalizedDisplayName() {
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val abdul = SplitGroupMemberEntity(id = "abdul", groupId = "g1", name = "Abdul Ajeez")
        val g = group(members = listOf(me, abdul))

        val result = GPayRequestMatcher.matchMember("abdul ajeez", g, emptyList())
        assertTrue(result is GPayRequestMatcher.MemberMatchResult.Matched)
        assertEquals("abdul", (result as GPayRequestMatcher.MemberMatchResult.Matched).member.id)
    }

    @Test
    fun ambiguousWhenMultipleMembersShareNormalizedName() {
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val a1 = SplitGroupMemberEntity(id = "a1", groupId = "g1", name = "Arun")
        val a2 = SplitGroupMemberEntity(id = "a2", groupId = "g1", name = "arun")
        val g = group(members = listOf(me, a1, a2))

        val result = GPayRequestMatcher.matchMember("Arun", g, emptyList())
        assertTrue(result is GPayRequestMatcher.MemberMatchResult.Ambiguous)
    }

    @Test
    fun notFoundWhenNoMemberMatches() {
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val g = group(members = listOf(me))
        val result = GPayRequestMatcher.matchMember("Stranger", g, emptyList())
        assertTrue(result is GPayRequestMatcher.MemberMatchResult.NotFound)
    }

    @Test
    fun confirmedMappingTakesPriorityOverNameMatch() {
        val me = SplitGroupMemberEntity(id = "me", groupId = "g1", name = "Me", isMe = true)
        val abdul = SplitGroupMemberEntity(id = "abdul", groupId = "g1", name = "A. Ajeez")
        val g = group(members = listOf(me, abdul))
        val mapping = GPayMemberMappingEntity(groupId = "g1", memberId = "abdul", normalizedRequesterName = "abdul ajeez")

        val result = GPayRequestMatcher.matchMember("Abdul Ajeez", g, listOf(mapping))
        assertTrue(result is GPayRequestMatcher.MemberMatchResult.Matched)
        assertEquals("abdul", (result as GPayRequestMatcher.MemberMatchResult.Matched).member.id)
    }

    @Test
    fun matchesGroupByNormalizedName() {
        val groups = listOf(
            group(id = "g1", name = "Room Expense", members = emptyList()),
            group(id = "g2", name = "Trip", members = emptyList())
        )
        val result = GPayRequestMatcher.matchGroup("room expense", groups)
        assertTrue(result is GPayRequestMatcher.GroupMatchResult.Matched)
        assertEquals("g1", (result as GPayRequestMatcher.GroupMatchResult.Matched).group.group.id)
    }

    @Test
    fun ambiguousWhenMultipleGroupsShareNormalizedName() {
        val groups = listOf(
            group(id = "g1", name = "Trip", members = emptyList()),
            group(id = "g2", name = "trip", members = emptyList())
        )
        val result = GPayRequestMatcher.matchGroup("Trip", groups)
        assertTrue(result is GPayRequestMatcher.GroupMatchResult.Ambiguous)
    }

    @Test
    fun groupNotFoundWhenNameMissingOrUnmatched() {
        val groups = listOf(group(id = "g1", name = "Trip", members = emptyList()))
        assertTrue(GPayRequestMatcher.matchGroup(null, groups) is GPayRequestMatcher.GroupMatchResult.NotFound)
        assertTrue(GPayRequestMatcher.matchGroup("Nonexistent", groups) is GPayRequestMatcher.GroupMatchResult.NotFound)
    }
}
