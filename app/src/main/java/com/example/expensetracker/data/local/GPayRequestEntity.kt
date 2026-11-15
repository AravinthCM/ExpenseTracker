package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.expensetracker.model.GPayProcessingStatus
import java.util.UUID

/**
 * A captured Google Pay "payment request" notification (someone asking the user to pay them,
 * e.g. for a shared/group expense), preserved for review before it affects any ledger.
 */
@Entity(
    tableName = "gpay_requests",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchedGroupId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SplitGroupMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchedMemberId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedTransactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["requestKey"], unique = true),
        Index(value = ["matchedGroupId"]),
        Index(value = ["matchedMemberId"]),
        Index(value = ["linkedTransactionId"])
    ]
)
data class GPayRequestEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    /** Stable dedup fingerprint: normalized requester + amount + description + group + day bucket. */
    val requestKey: String,
    val packageName: String,
    val notificationKey: String? = null,
    val requesterRawName: String,
    val requesterNormalizedName: String,
    val amountPaise: Long,
    val description: String,
    val groupNameRaw: String? = null,
    val originalText: String,
    val postTime: Long,
    val capturedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val matchedGroupId: String? = null,
    val matchedMemberId: String? = null,
    val matchStatus: String,
    val processingStatus: String = GPayProcessingStatus.PENDING,
    /** The group expense transaction created from this request, if any. */
    val linkedTransactionId: String? = null,
    val linkedSplitId: String? = null,
    /** A subsequently captured GPay transaction that looks like the real repayment for this request. */
    val paymentConfirmationTransactionId: String? = null,
    /** The repayment recorded once the user confirms the payment confirmation above. */
    val repaymentId: String? = null
)
