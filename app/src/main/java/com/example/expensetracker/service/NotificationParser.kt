package com.example.expensetracker.service

import android.util.Log
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType

object NotificationParser {

    private const val GPAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
    private const val MESSAGING_PACKAGE = "com.google.android.apps.messaging"
    private const val GMAIL_PACKAGE = "com.google.android.gm"

    // Canara Bank Email Debit: "An amount of INR 1.00 has been DEBITED on 20/09/26 from your account XXX528 to KRISHNA KUMA with UPI Ref No.:6263222146179."
    private val canaraDebitRegex = Regex(
        pattern = """An\s+amount\s+of\s+(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+has\s+been\s+DEBITED\s+on\s+\S+\s+from\s+your\s+account\s+\S+(?:\s+to\s+(.+?))?(?:\s+with\s+UPI\s+Ref|\s+with\s+Ref|\s+Total|\.|\n|$)""",
        option = RegexOption.IGNORE_CASE
    )

    private val genericDebitRegex = Regex(
        pattern = """(?:amount\s+of\s+)?(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+has\s+been\s+DEBITED""",
        option = RegexOption.IGNORE_CASE
    )

    // Canara Bank Email Credit: "An amount of INR 500.00 has been CREDITED on 20/09/26 to your account XXX528 from RAMESH with UPI Ref No.:6263222146180."
    private val canaraCreditRegex = Regex(
        pattern = """An\s+amount\s+of\s+(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+has\s+been\s+CREDITED\s+on\s+\S+\s+to\s+your\s+account\s+\S+(?:\s+(?:from|by)\s+(.+?))?(?:\s+with\s+UPI\s+Ref|\s+with\s+Ref|\s+Total|\.|\n|$)""",
        option = RegexOption.IGNORE_CASE
    )

    private val genericCreditRegex = Regex(
        pattern = """(?:amount\s+of\s+)?(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+has\s+been\s+CREDITED""",
        option = RegexOption.IGNORE_CASE
    )

    // GPay Income pattern: "LOGESHWARAN S paid you ₹1.00"
    private val gpayIncomeRegex = Regex(
        pattern = """^(.+?)\s+paid\s+you\s+₹?\s*([\d,]+(?:\.\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // GPay Expense pattern: "Paid ₹149 to KFC" or "Sent ₹500 to Swiggy"
    private val gpayExpenseRegex = Regex(
        pattern = """^(?:paid|sent)\s+₹?\s*([\d,]+(?:\.\d{1,2})?)\s+to\s+(.+)""",
        option = RegexOption.IGNORE_CASE
    )

    // Bank SMS Debit pattern: "IDBI Bank Acct XX617 debited for Rs 1.00" or "Acct debited for Rs 1,250.00"
    private val bankDebitRegex = Regex(
        pattern = """debited\s+(?:for\s+)?(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // Recipient / Credited name extraction: "Bal Rs 16599.27 ARAVINTH C M credited" or "; ARAVINTH C M credited"
    private val bankCreditedNameRegex = Regex(
        pattern = """;\s*(?:Bal\s+(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{1,2})?\s+)?(.+?)\s+credited""",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(
        packageName: String,
        title: String,
        text: String,
        postTime: Long
    ): ParsedTransaction? {
        val trimmedTitle = title.trim()
        val trimmedText = text.trim()

        return when (packageName) {
            GMAIL_PACKAGE -> parseGmail(trimmedTitle, trimmedText, postTime)
            GPAY_PACKAGE -> parseGPay(trimmedTitle, trimmedText, postTime)
            MESSAGING_PACKAGE -> parseBankSms(trimmedTitle, trimmedText, postTime)
            else -> null
        }
    }

    private fun parseGmail(title: String, text: String, postTime: Long): ParsedTransaction? {
        val fullContent = if (text.isNotBlank()) text else title
        if (fullContent.isBlank()) return null

        // 1. Check for Canara / Bank Debit
        val debitMatch = canaraDebitRegex.find(fullContent)
        if (debitMatch != null) {
            val rawAmount = debitMatch.groupValues[1].replace(",", "")
            val amount = rawAmount.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val recipientName = debitMatch.groupValues.getOrNull(2)?.trim() ?: ""
                val finalName = when {
                    recipientName.isNotBlank() -> recipientName
                    else -> extractRecipientFromText(fullContent) ?: "Canara Bank"
                }

                return ParsedTransaction(
                    amount = amount,
                    name = finalName,
                    type = TransactionType.EXPENSE,
                    source = TransactionSource.BANK_EMAIL.name,
                    timestamp = postTime
                )
            }
        }

        val genDebitMatch = genericDebitRegex.find(fullContent)
        if (genDebitMatch != null) {
            val rawAmount = genDebitMatch.groupValues[1].replace(",", "")
            val amount = rawAmount.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val recipientName = extractRecipientFromText(fullContent)
                val finalName = recipientName ?: "Canara Bank"

                return ParsedTransaction(
                    amount = amount,
                    name = finalName,
                    type = TransactionType.EXPENSE,
                    source = TransactionSource.BANK_EMAIL.name,
                    timestamp = postTime
                )
            }
        }

        // 2. Check for Canara / Bank Credit
        val creditMatch = canaraCreditRegex.find(fullContent)
        if (creditMatch != null) {
            val rawAmount = creditMatch.groupValues[1].replace(",", "")
            val amount = rawAmount.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val senderName = creditMatch.groupValues.getOrNull(2)?.trim() ?: ""
                val finalName = when {
                    senderName.isNotBlank() -> senderName
                    else -> extractSenderFromText(fullContent) ?: "Canara Bank"
                }

                return ParsedTransaction(
                    amount = amount,
                    name = finalName,
                    type = TransactionType.INCOME,
                    source = TransactionSource.BANK_EMAIL.name,
                    timestamp = postTime
                )
            }
        }

        val genCreditMatch = genericCreditRegex.find(fullContent)
        if (genCreditMatch != null) {
            val rawAmount = genCreditMatch.groupValues[1].replace(",", "")
            val amount = rawAmount.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val senderName = extractSenderFromText(fullContent)
                val finalName = senderName ?: "Canara Bank"

                return ParsedTransaction(
                    amount = amount,
                    name = finalName,
                    type = TransactionType.INCOME,
                    source = TransactionSource.BANK_EMAIL.name,
                    timestamp = postTime
                )
            }
        }

        return null
    }

    private fun extractRecipientFromText(text: String): String? {
        val match = Regex("""\bto\s+([A-Za-z0-9\s]{2,30}?)(?:\s+with|\s+Ref|\s+Total|\.|\n|$)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractSenderFromText(text: String): String? {
        val match = Regex("""\b(?:from|by)\s+([A-Za-z0-9\s]{2,30}?)(?:\s+with|\s+Ref|\s+Total|\.|\n|$)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseGPay(title: String, text: String, postTime: Long): ParsedTransaction? {
        val candidates = listOf(title, text, "$title $text")

        for (candidate in candidates) {
            if (candidate.isBlank()) continue

            // 1. Try GPay Income
            val incomeMatch = gpayIncomeRegex.find(candidate)
            if (incomeMatch != null) {
                val name = incomeMatch.groupValues[1].trim()
                val rawAmount = incomeMatch.groupValues[2].replace(",", "")
                val amount = rawAmount.toDoubleOrNull()
                if (amount != null && amount > 0 && name.isNotBlank()) {
                    return ParsedTransaction(
                        amount = amount,
                        name = name,
                        type = TransactionType.INCOME,
                        source = TransactionSource.GPAY.name,
                        timestamp = postTime
                    )
                }
            }

            // 2. Try GPay Expense
            val expenseMatch = gpayExpenseRegex.find(candidate)
            if (expenseMatch != null) {
                val rawAmount = expenseMatch.groupValues[1].replace(",", "")
                val name = expenseMatch.groupValues[2].trim()
                val amount = rawAmount.toDoubleOrNull()
                if (amount != null && amount > 0 && name.isNotBlank()) {
                    return ParsedTransaction(
                        amount = amount,
                        name = name,
                        type = TransactionType.EXPENSE,
                        source = TransactionSource.GPAY.name,
                        timestamp = postTime
                    )
                }
            }
        }
        return null
    }

    // Bank SMS Transfer pattern: "Transfer of Rs 1,000.00 to Acct XX999 on 20-Sep-26"
    private val bankTransferRegex = Regex(
        pattern = """transfer\s+of\s+(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)(?:\s+to\s+(.+?))?(?:\s+on|\.|$|;)""",
        option = RegexOption.IGNORE_CASE
    )

    private fun parseBankSms(title: String, text: String, postTime: Long): ParsedTransaction? {
        val fullContent = "$title $text".trim()
        if (fullContent.isBlank()) return null

        // 1. Check for Bank Debit match
        val debitMatch = bankDebitRegex.find(fullContent)
        if (debitMatch != null) {
            val rawAmount = debitMatch.groupValues[1].replace(",", "")
            val amount = rawAmount.toDoubleOrNull()

            if (amount != null && amount > 0) {
                // Extract credited recipient name if present (e.g., "ARAVINTH C M credited")
                val nameMatch = bankCreditedNameRegex.find(fullContent)
                val recipientName = nameMatch?.groupValues?.get(1)?.trim()

                val finalName = when {
                    !recipientName.isNullOrBlank() -> recipientName
                    title.contains("IDBI", ignoreCase = true) || fullContent.contains("IDBI", ignoreCase = true) -> "IDBI Bank"
                    title.isNotBlank() && title != "Messages" -> title
                    else -> "Bank SMS"
                }

                return ParsedTransaction(
                    amount = amount,
                    name = finalName,
                    type = TransactionType.EXPENSE,
                    source = TransactionSource.BANK_SMS.name,
                    timestamp = postTime
                )
            }
        }

        // 2. Check for Bank Transfer match
        val transferMatch = bankTransferRegex.find(fullContent)
        if (transferMatch != null) {
            val rawAmount = transferMatch.groupValues[1].replace(",", "")
            val amount = rawAmount.toDoubleOrNull()
            val recipientName = transferMatch.groupValues.getOrNull(2)?.trim()

            if (amount != null && amount > 0) {
                val finalName = when {
                    !recipientName.isNullOrBlank() -> recipientName
                    title.contains("IDBI", ignoreCase = true) || fullContent.contains("IDBI", ignoreCase = true) -> "IDBI Bank"
                    title.isNotBlank() && title != "Messages" -> title
                    else -> "Bank SMS"
                }

                return ParsedTransaction(
                    amount = amount,
                    name = finalName,
                    type = TransactionType.TRANSFER,
                    source = TransactionSource.BANK_SMS.name,
                    timestamp = postTime
                )
            }
        }

        return null
    }

    /*
    // Temporary test method for NotificationParser
    fun runTests() {
        val tag = "ExpenseTrackerNotification"
        Log.d(tag, "========================================")
        Log.d(tag, "🧪 STARTING BANK SMS & GPAY PARSER TESTS")
        Log.d(tag, "========================================")

        val testCases = listOf(
            TestCase(
                description = "Test 1: Real IDBI debit SMS",
                packageName = MESSAGING_PACKAGE,
                title = "JD-IDBIBK-S",
                text = "IDBI Bank Acct XX617 debited for Rs 1.00 on 20-Sep-26; Bal Rs 16599.27 ARAVINTH C M credited. UPI:662948348715. To Block UPI send SMS ..."
            ),
            TestCase(
                description = "Test 2: Unrelated Google Messages notification",
                packageName = MESSAGING_PACKAGE,
                title = "Messages is doing work in the background",
                text = "Messages is working in the background..."
            ),
            TestCase(
                description = "Test 3: Different amount formatting (Rs 1,250.00)",
                packageName = MESSAGING_PACKAGE,
                title = "JD-IDBIBK-S",
                text = "IDBI Bank Acct XX617 debited for Rs 1,250.00 on 20-Sep-26; Bal Rs 16599.27 ARAVINTH C M credited."
            ),
            TestCase(
                description = "Test 4: GPay Income",
                packageName = GPAY_PACKAGE,
                title = "LOGESHWARAN S paid you ₹1.00",
                text = "Tap to view."
            ),
            TestCase(
                description = "Test 5: GPay Expense",
                packageName = GPAY_PACKAGE,
                title = "Paid ₹149 to KFC",
                text = "Tap to view."
            )
        )

        testCases.forEachIndexed { index, testCase ->
            val result = parse(
                packageName = testCase.packageName,
                title = testCase.title,
                text = testCase.text,
                postTime = System.currentTimeMillis()
            )

            Log.d(tag, "TEST #${index + 1}: ${testCase.description}")
            Log.d(tag, "Package   : ${testCase.packageName}")
            Log.d(tag, "Input Title: \"${testCase.title}\"")
            Log.d(tag, "Input Text : \"${testCase.text}\"")

            if (result != null) {
                Log.d(
                    tag,
                    """
                    Result: PARSED SUCCESS
                    Source: ${result.source}
                    Type  : ${result.type}
                    Amount: ₹${result.amount}
                    Name  : ${result.name}
                    """.trimIndent()
                )
            } else {
                Log.d(tag, "Result: NULL (No transaction detected)")
            }
            Log.d(tag, "----------------------------------------")
        }
        Log.d(tag, "🧪 PARSER TESTS COMPLETED")
        Log.d(tag, "========================================")
    }

    private data class TestCase(
        val description: String,
        val packageName: String,
        val title: String,
        val text: String
    )
    */
}
