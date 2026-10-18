package com.example.expensetracker.service

import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.model.TransactionType

data class DetectionResult(
    val detectionMethod: String, // "TEMPLATE", "GENERIC", "NOT_DETECTED"
    val confidence: String,       // "TEMPLATE_MATCH", "GENERIC_HIGH_CONFIDENCE", "GENERIC_LOW_CONFIDENCE", "NOT_TRANSACTION"
    val detectedPayment: Boolean,
    val parsedTransaction: ParsedTransaction? = null,
    val matchedTemplateId: String? = null,
    val matchedTemplateName: String? = null,
    val amount: Double? = null,
    val merchant: String? = null,
    val accountNumber: String? = null,
    val referenceNumber: String? = null,
    val balance: Double? = null,
    val transactionType: String? = null,
    val failureReason: String? = null
)

object TransactionDetectionCoordinator {

    fun detectTransaction(
        packageName: String,
        title: String,
        text: String,
        postTime: Long,
        enabledTemplates: List<PaymentTemplateEntity>
    ): DetectionResult {
        val fullContent = when {
            text.isNotBlank() && title.isNotBlank() -> "$title\n$text"
            text.isNotBlank() -> text
            else -> title
        }

        // Strategy 1: Try Enabled Payment Templates
        if (enabledTemplates.isNotEmpty()) {
            val templateResult = PaymentTemplateMatcher.match(fullContent, enabledTemplates)
            if (templateResult.matched && templateResult.amount != null && templateResult.amount > 0) {
                val txType = templateResult.transactionType ?: TransactionType.EXPENSE
                val merchantName = templateResult.merchant ?: templateResult.templateName ?: "Payment"

                val parsedTx = ParsedTransaction(
                    amount = templateResult.amount,
                    name = merchantName,
                    type = txType,
                    source = "TEMPLATE:${templateResult.templateName ?: "Custom"}",
                    timestamp = postTime,
                    referenceNumber = templateResult.reference
                )

                return DetectionResult(
                    detectionMethod = "TEMPLATE",
                    confidence = "TEMPLATE_MATCH",
                    detectedPayment = true,
                    parsedTransaction = parsedTx,
                    matchedTemplateId = templateResult.templateId,
                    matchedTemplateName = templateResult.templateName,
                    amount = templateResult.amount,
                    merchant = merchantName,
                    accountNumber = templateResult.account,
                    referenceNumber = templateResult.reference,
                    balance = templateResult.balance,
                    transactionType = txType.name
                )
            }
        }

        // Strategy 2: Fall back to Generic Transaction Detector
        val genericResult = GenericTransactionDetector.detect(
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime
        )

        return if (genericResult.detected && genericResult.parsedTransaction != null) {
            DetectionResult(
                detectionMethod = "GENERIC",
                confidence = genericResult.confidence,
                detectedPayment = true,
                parsedTransaction = genericResult.parsedTransaction,
                amount = genericResult.parsedTransaction.amount,
                merchant = genericResult.parsedTransaction.name,
                accountNumber = genericResult.parsedTransaction.accountSuffix,
                referenceNumber = genericResult.parsedTransaction.referenceNumber,
                balance = genericResult.parsedTransaction.balance,
                transactionType = genericResult.parsedTransaction.type.name
            )
        } else {
            DetectionResult(
                detectionMethod = "NOT_DETECTED",
                confidence = "NOT_TRANSACTION",
                detectedPayment = false,
                failureReason = genericResult.failureReason
            )
        }
    }
}
