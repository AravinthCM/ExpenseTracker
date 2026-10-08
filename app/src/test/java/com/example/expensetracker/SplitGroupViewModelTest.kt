package com.example.expensetracker

import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.model.DraftMember
import com.example.expensetracker.model.SplitGroupValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SplitGroupViewModelTest {

    @Test
    fun testGroupEntityCreationAndDefaults() {
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val group = SplitGroupEntity(
            id = groupId,
            name = "Flatmates",
            createdAt = now,
            updatedAt = now
        )

        assertEquals(groupId, group.id)
        assertEquals("Flatmates", group.name)
        assertEquals(now, group.createdAt)
        assertEquals(now, group.updatedAt)
    }

    @Test
    fun testGroupMemberEntityCascadeReference() {
        val groupId = UUID.randomUUID().toString()
        val member1 = SplitGroupMemberEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = "Me",
            isMe = true
        )
        val member2 = SplitGroupMemberEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = "Bob",
            isMe = false
        )

        val groupWithMembers = SplitGroupWithMembers(
            group = SplitGroupEntity(id = groupId, name = "Trip"),
            members = listOf(member1, member2)
        )

        assertEquals(groupId, groupWithMembers.group.id)
        assertEquals(2, groupWithMembers.members.size)
        assertTrue(groupWithMembers.members.any { it.isMe })
        assertEquals(1, groupWithMembers.members.count { it.isMe })
    }

    @Test
    fun testDraftMemberConversionAndTrimming() {
        val draftMembers = listOf(
            DraftMember(name = "   Alice   ", isMe = true),
            DraftMember(name = "   Bob   ", isMe = false)
        )

        val groupName = "   Goa Trip   "
        val validation = SplitGroupValidator.validate(groupName, draftMembers)
        assertTrue(validation.isValid)

        val sanitizedGroupName = SplitGroupValidator.sanitizeGroupName(groupName)
        val sanitizedMembers = draftMembers.map {
            it.copy(name = SplitGroupValidator.sanitizeMemberName(it.name))
        }

        assertEquals("Goa Trip", sanitizedGroupName)
        assertEquals("Alice", sanitizedMembers[0].name)
        assertEquals("Bob", sanitizedMembers[1].name)
    }

    @Test
    fun testEditingExistingGroupPreservesMemberIds() {
        val existingMemberId1 = UUID.randomUUID().toString()
        val existingMemberId2 = UUID.randomUUID().toString()

        val updatedDrafts = listOf(
            DraftMember(id = existingMemberId1, name = "Alice (Updated)", isMe = true),
            DraftMember(id = existingMemberId2, name = "Bob", isMe = false),
            DraftMember(name = "Charlie (New)", isMe = false)
        )

        val validation = SplitGroupValidator.validate("Updated Group", updatedDrafts)
        assertTrue(validation.isValid)
        assertEquals(3, updatedDrafts.size)
        assertEquals(existingMemberId1, updatedDrafts[0].id)
        assertEquals(existingMemberId2, updatedDrafts[1].id)
        assertTrue(updatedDrafts[2].id.isNotBlank())
    }
}
