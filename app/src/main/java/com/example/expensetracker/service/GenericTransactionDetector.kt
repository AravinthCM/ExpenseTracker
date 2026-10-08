package com.example.expensetracker.service

data class GenericDetectionResult(
    val detected: Boolean,
    val confidence: String, // "GENERIC_HIGH_CONFIDENCE", "GENERIC_LOW_CONFIDENCE", "NOT_TRANSACTION"
    val parsedTransaction: ParsedTransaction? = null,
    val failureReason: String? = null
)

object GenericTransactionDetector {

    fun detect(
        packageName: String,
        title: String,
        text: String,
        postTime: Long
    ): GenericDetectionResult {
        val trimmedTitle = title.trim()
        val trimmedText = text.trim()

        val isSupportedPackage = packageName == "com.google.android.gm" ||
                packageName == "com.google.android.apps.nbu.paisa.user" ||
                packageName == "com.google.android.apps.messaging" ||
                packageName.contains("gmail", ignoreCase = true)

        if (!isSupportedPackage) {
            return GenericDetectionResult(
                detected = false,
                confidence = "NOT_TRANSACTION",
                failureReason = "Package '$packageName' is not supported for payment extraction"
            )
        }

        if (trimmedTitle.isBlank() && trimmedText.isBlank()) {
            return GenericDetectionResult(
                detected = false,
                confidence = "NOT_TRANSACTION",
                failureReason = "Notification text and title are empty"
            )
        }

        val parsed = NotificationParser.parse(
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime
        )

        return if (parsed != null && parsed.amount > 0) {
            GenericDetectionResult(
                detected = true,
                confidence = "GENERIC_HIGH_CONFIDENCE",
                parsedTransaction = parsed
            )
        } else {
            GenericDetectionResult(
                detected = false,
                confidence = "NOT_TRANSACTION",
                failureReason = "Parser returned null (no matching payment pattern)"
            )
        }
    }
}
