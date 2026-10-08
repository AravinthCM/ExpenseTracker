package com.example.expensetracker.service

import android.util.Log
import com.example.expensetracker.model.TransactionType

object TransactionNormalizer {

    private const val TAG = "ExpenseTrackerNotification"
    private const val GPAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
    private const val MESSAGING_PACKAGE = "com.google.android.apps.messaging"

    private val transferKeywordsRegex = Regex(
        pattern = """\b(?:transfer\s+of|transferred|transfer)\b""",
        option = RegexOption.IGNORE_CASE
    )

    fun normalize(parsed: ParsedTransaction, rawText: String): NormalizedTransaction {
        val isTransfer = parsed.type == TransactionType.TRANSFER || transferKeywordsRegex.containsMatchIn(rawText)

        val finalType = when {
            isTransfer -> TransactionType.TRANSFER
            parsed.type == TransactionType.INCOME -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        return NormalizedTransaction(
            amount = parsed.amount,
            name = parsed.name,
            type = finalType,
            source = parsed.source,
            timestamp = parsed.timestamp,
            originalText = rawText
        )
    }

    /*
    // Temporary test method for TransactionNormalizer
    fun runTests() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🧪 STARTING NORMALIZER TESTS")
        Log.d(TAG, "========================================")

        val testCases = listOf(
            TestCase(
                description = "Test 1: GPay Income",
                packageName = GPAY_PACKAGE,
                title = "LOGESHWARAN S paid you ₹1.00",
                text = "Tap to view."
            ),
            TestCase(
                description = "Test 2: GPay Expense (Paid ₹149 to KFC)",
                packageName = GPAY_PACKAGE,
                title = "Paid ₹149 to KFC",
                text = "Tap to view."
            ),
            TestCase(
                description = "Test 3: GPay Expense (Sent ₹500 to Swiggy)",
                packageName = GPAY_PACKAGE,
                title = "Sent ₹500 to Swiggy",
                text = "Tap to view."
            ),
            TestCase(
                description = "Test 4: Bank SMS Debit (IDBI Bank Acct debited)",
                packageName = MESSAGING_PACKAGE,
                title = "JD-IDBIBK-S",
                text = "IDBI Bank Acct XX617 debited for Rs 1.00 on 20-Sep-26; Bal Rs 16599.27 ARAVINTH C M credited. UPI:662948348715. To Block UPI send SMS ..."
            ),
            TestCase(
                description = "Test 5: Explicit Transfer (Transfer of Rs 1,000.00 to Acct XX999)",
                packageName = MESSAGING_PACKAGE,
                title = "JD-IDBIBK-S",
                text = "Transfer of Rs 1,000.00 to Acct XX999 on 20-Sep-26"
            )
        )

        testCases.forEachIndexed { index, tc ->
            val rawText = "${tc.title} ${tc.text}".trim()
            val parsed = NotificationParser.parse(
                packageName = tc.packageName,
                title = tc.title,
                text = tc.text,
                postTime = System.currentTimeMillis()
            )

            Log.d(TAG, "TEST #${index + 1}: ${tc.description}")
            Log.d(TAG, "Package   : ${tc.packageName}")
            Log.d(TAG, "Raw Text  : \"$rawText\"")

            if (parsed != null) {
                val normalized = normalize(parsed, rawText)
                Log.d(
                    TAG,
                    """
                    Result: NORMALIZED SUCCESS
                    Type        : ${normalized.type}
                    Amount      : ₹${normalized.amount}
                    Name        : ${normalized.name}
                    Source      : ${normalized.source}
                    Timestamp   : ${normalized.timestamp}
                    OriginalText: "${normalized.originalText}"
                    """.trimIndent()
                )
            } else {
                Log.d(TAG, "Result: NULL (No transaction detected)")
            }
            Log.d(TAG, "----------------------------------------")
        }

        Log.d(TAG, "🧪 NORMALIZER TESTS COMPLETED")
        Log.d(TAG, "========================================")
    }

    private data class TestCase(
        val description: String,
        val packageName: String,
        val title: String,
        val text: String
    )
    */
}
