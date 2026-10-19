package com.example.expensetracker.model

import java.util.UUID

sealed class SplitGroupValidationError {
    object BlankGroupName : SplitGroupValidationError()
    object InsufficientMembers : SplitGroupValidationError()
    object MissingMeMember : SplitGroupValidationError()
    object MultipleMeMembers : SplitGroupValidationError()
    object BlankMemberName : SplitGroupValidationError()
    object DuplicateMemberName : SplitGroupValidationError()

    fun getErrorMessage(): String {
        return when (this) {
            is BlankGroupName -> "Group name cannot be blank."
            is InsufficientMembers -> "A split group must have at least 2 members."
            is MissingMeMember -> "Exactly one member must be designated as 'Me'."
            is MultipleMeMembers -> "Only one member can be designated as 'Me'."
            is BlankMemberName -> "Member names cannot be blank."
            is DuplicateMemberName -> "Member names must be unique within the group."
        }
    }
}

data class DraftMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isMe: Boolean = false,
    val openingDebtPaise: Long = 0L
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<SplitGroupValidationError> = emptyList()
) {
    fun getFirstErrorMessage(): String? = errors.firstOrNull()?.getErrorMessage()
}

object SplitGroupValidator {

    fun validate(groupName: String, members: List<DraftMember>): ValidationResult {
        val errors = mutableListOf<SplitGroupValidationError>()

        if (groupName.trim().isBlank()) {
            errors.add(SplitGroupValidationError.BlankGroupName)
        }

        if (members.size < 2) {
            errors.add(SplitGroupValidationError.InsufficientMembers)
        }

        val meCount = members.count { it.isMe }
        if (meCount == 0) {
            errors.add(SplitGroupValidationError.MissingMeMember)
        } else if (meCount > 1) {
            errors.add(SplitGroupValidationError.MultipleMeMembers)
        }

        if (members.any { it.name.trim().isBlank() }) {
            errors.add(SplitGroupValidationError.BlankMemberName)
        }

        val trimmedNames = members.map { it.name.trim().lowercase() }
        if (trimmedNames.size != trimmedNames.distinct().size) {
            errors.add(SplitGroupValidationError.DuplicateMemberName)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun sanitizeGroupName(name: String): String = name.trim()

    fun sanitizeMemberName(name: String): String = name.trim()
}
