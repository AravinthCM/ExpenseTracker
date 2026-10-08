package com.example.expensetracker

import com.example.expensetracker.model.DraftMember
import com.example.expensetracker.model.SplitGroupValidationError
import com.example.expensetracker.model.SplitGroupValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitGroupValidatorTest {

    @Test
    fun testValidGroupWithTwoMembersAndOneMe() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = true),
            DraftMember(name = "Bob", isMe = false)
        )
        val result = SplitGroupValidator.validate("Trip to Goa", members)

        assertTrue("Group with 2 members and exactly one 'Me' should be valid", result.isValid)
        assertTrue("Errors list should be empty", result.errors.isEmpty())
    }

    @Test
    fun testValidGroupWithMultipleMembers() {
        val members = listOf(
            DraftMember(name = "  Alice  ", isMe = false),
            DraftMember(name = "Bob", isMe = true),
            DraftMember(name = "Charlie", isMe = false)
        )
        val result = SplitGroupValidator.validate("Weekend Party", members)

        assertTrue("Group with 3 members should be valid", result.isValid)
    }

    @Test
    fun testBlankGroupNameFails() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = true),
            DraftMember(name = "Bob", isMe = false)
        )
        val result = SplitGroupValidator.validate("   ", members)

        assertFalse("Blank group name should fail validation", result.isValid)
        assertTrue(result.errors.contains(SplitGroupValidationError.BlankGroupName))
        assertEquals("Group name cannot be blank.", result.getFirstErrorMessage())
    }

    @Test
    fun testInsufficientMembersFails() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = true)
        )
        val result = SplitGroupValidator.validate("Solo Group", members)

        assertFalse("Group with fewer than 2 members should fail validation", result.isValid)
        assertTrue(result.errors.contains(SplitGroupValidationError.InsufficientMembers))
        assertEquals("A split group must have at least 2 members.", result.getFirstErrorMessage())
    }

    @Test
    fun testMissingMeMemberFails() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = false),
            DraftMember(name = "Bob", isMe = false)
        )
        val result = SplitGroupValidator.validate("No Me Group", members)

        assertFalse("Group without a 'Me' member should fail validation", result.isValid)
        assertTrue(result.errors.contains(SplitGroupValidationError.MissingMeMember))
        assertEquals("Exactly one member must be designated as 'Me'.", result.getFirstErrorMessage())
    }

    @Test
    fun testMultipleMeMembersFails() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = true),
            DraftMember(name = "Bob", isMe = true)
        )
        val result = SplitGroupValidator.validate("Two Mes Group", members)

        assertFalse("Group with multiple 'Me' members should fail validation", result.isValid)
        assertTrue(result.errors.contains(SplitGroupValidationError.MultipleMeMembers))
        assertEquals("Only one member can be designated as 'Me'.", result.getFirstErrorMessage())
    }

    @Test
    fun testBlankMemberNameFails() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = true),
            DraftMember(name = "   ", isMe = false)
        )
        val result = SplitGroupValidator.validate("Blank Member Group", members)

        assertFalse("Blank member name should fail validation", result.isValid)
        assertTrue(result.errors.contains(SplitGroupValidationError.BlankMemberName))
        assertEquals("Member names cannot be blank.", result.getFirstErrorMessage())
    }

    @Test
    fun testDuplicateMemberNamesCaseInsensitiveFails() {
        val members = listOf(
            DraftMember(name = "Alice", isMe = true),
            DraftMember(name = "  alice  ", isMe = false)
        )
        val result = SplitGroupValidator.validate("Duplicate Group", members)

        assertFalse("Duplicate member names (ignoring case and whitespace) should fail validation", result.isValid)
        assertTrue(result.errors.contains(SplitGroupValidationError.DuplicateMemberName))
        assertEquals("Member names must be unique within the group.", result.getFirstErrorMessage())
    }

    @Test
    fun testSanitizeTrimsLeadingAndTrailingWhitespace() {
        val sanitizedGroup = SplitGroupValidator.sanitizeGroupName("   Dinner Group   ")
        val sanitizedMember = SplitGroupValidator.sanitizeMemberName("   Charlie   ")

        assertEquals("Dinner Group", sanitizedGroup)
        assertEquals("Charlie", sanitizedMember)
    }
}
