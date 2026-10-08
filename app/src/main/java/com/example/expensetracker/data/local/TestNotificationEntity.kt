package com.example.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_notifications")
data class TestNotificationEntity(
    @PrimaryKey
    val id: String,
    val receivedAt: Long,
    val packageName: String,
    val notificationId: Int,
    val notificationTag: String? = null,
    val notificationKey: String? = null,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val textLines: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val titleBig: String? = null,
    val rawExtras: String? = null,
    val status: String, // "SUCCESS" or "FAIL"
    val detectedPayment: Boolean,
    val amount: Double? = null,
    val transactionType: String? = null,
    val merchant: String? = null,
    val source: String? = null,
    val failureReason: String? = null,
    val detectionMethod: String? = null, // "TEMPLATE", "GENERIC", "NOT_DETECTED"
    val matchedTemplateName: String? = null,
    val accountNumber: String? = null,
    val referenceNumber: String? = null,
    val confidence: String? = null // "TEMPLATE_MATCH", "GENERIC_HIGH_CONFIDENCE", "GENERIC_LOW_CONFIDENCE", "NOT_TRANSACTION"
)
