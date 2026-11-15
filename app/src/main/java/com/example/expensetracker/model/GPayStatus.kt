package com.example.expensetracker.model

/** Match confidence for a captured GPay request against existing Split Groups/Members. */
object GPayMatchStatus {
    const val AUTO_MATCHED = "AUTO_MATCHED"
    const val NEEDS_GROUP = "NEEDS_GROUP"
    const val NEEDS_MEMBER = "NEEDS_MEMBER"
    const val AMBIGUOUS_GROUP = "AMBIGUOUS_GROUP"
    const val AMBIGUOUS_MEMBER = "AMBIGUOUS_MEMBER"
}

/** Lifecycle state of a captured GPay request in the review workflow. */
object GPayProcessingStatus {
    const val PENDING = "PENDING"
    const val ADDED_AS_EXPENSE = "ADDED_AS_EXPENSE"
    const val DISMISSED = "DISMISSED"
    const val DUPLICATE = "DUPLICATE"
}
