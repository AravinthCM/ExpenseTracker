package com.example.expensetracker.service

object PaymentTemplateHelper {

    data class DetectedVariables(
        val amount: String? = null,
        val date: String? = null,
        val account: String? = null,
        val merchant: String? = null,
        val reference: String? = null,
        val balance: String? = null,
        val suggestedPattern: String
    )

    fun autoDetectTemplate(sampleText: String): DetectedVariables {
        val trimmed = sampleText.trim()
        if (trimmed.isBlank()) {
            return DetectedVariables(suggestedPattern = "")
        }

        var amountVal: String? = null
        var dateVal: String? = null
        var accountVal: String? = null
        var merchantVal: String? = null
        var refVal: String? = null
        var balVal: String? = null

        var workingPattern = trimmed

        // 1. Balance detection: extract value after balance keyword
        val balRegex = Regex("""(?i)(?:Total\s+Available\s+Balance|Available\s+Balance|Bal|Balance)\s+(?:INR|Rs\.?|₹)?\s*([\d,]+(?:\.\d{1,2})?)""")
        val balMatch = balRegex.find(workingPattern)
        if (balMatch != null && balMatch.groupValues.size > 1) {
            balVal = balMatch.groupValues[1]
        }

        // 2. Reference / UPI Ref detection: extract value after UPI/Ref keyword
        val refRegex = Regex("""(?i)(?:UPI\s+Ref\s+(?:No\.?:?)?|Ref\s+(?:No\.?:?)?|UPI:?)\s*([A-Za-z0-9]{6,16})""")
        val refMatch = refRegex.find(workingPattern)
        if (refMatch != null && refMatch.groupValues.size > 1) {
            refVal = refMatch.groupValues[1]
        }

        // 3. Merchant detection: extract name between 'to'/'from'/'by' and 'with'/'Ref'/'Total'/period/end
        val merchantRegex = Regex("""(?i)\bto\s+([A-Za-z0-9\s]{2,30}?)(?=\s+(?:with|Ref|Bal|Total|\.|\n|$))""")
        val merchMatch = merchantRegex.find(workingPattern)
        if (merchMatch != null && merchMatch.groupValues.size > 1) {
            merchantVal = merchMatch.groupValues[1].trim()
        } else {
            val altMerchRegex = Regex("""(?i)\b(?:from|by)\s+([A-Za-z0-9\s]{2,30}?)(?=\s+(?:with|Ref|Bal|Total|\.|\n|$))""")
            val altMatch = altMerchRegex.find(workingPattern)
            if (altMatch != null && altMatch.groupValues.size > 1) {
                // Verify it's not "your account XXX"
                val matchedStr = altMatch.groupValues[1].trim()
                if (!matchedStr.contains("account", ignoreCase = true) && !matchedStr.contains("acct", ignoreCase = true)) {
                    merchantVal = matchedStr
                }
            }
        }

        // 4. Account detection: extract account suffix/number after account keyword
        val accountRegex = Regex("""(?i)(?:account|acct|acc)\s+([X\*\d]{3,16})""")
        val acctMatch = accountRegex.find(workingPattern)
        if (acctMatch != null && acctMatch.groupValues.size > 1) {
            accountVal = acctMatch.groupValues[1]
        }

        // 5. Date detection: extract date string
        val dateRegex = Regex("""\b(\d{1,4}[/\.-](?:\d{1,2}|[A-Za-z]{3})[/\.-]\d{1,4})\b""")
        val dateMatch = dateRegex.find(workingPattern)
        if (dateMatch != null) {
            dateVal = dateMatch.groupValues[1]
        }

        // 6. Amount detection: extract transaction amount (excluding balance value)
        val amountRegex = Regex("""(?i)(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""")
        val allAmounts = amountRegex.findAll(workingPattern).toList()
        for (m in allAmounts) {
            val candidate = m.groupValues[1]
            if (candidate != balVal) {
                amountVal = candidate
                break
            }
        }

        // --- PERFORM VALUE-LEVEL REPLACEMENTS SAFELY ---
        if (!balVal.isNullOrBlank()) {
            workingPattern = replaceExactValue(workingPattern, balVal, "{BALANCE}")
        }
        if (!refVal.isNullOrBlank()) {
            workingPattern = replaceExactValue(workingPattern, refVal, "{REFERENCE}")
        }
        if (!merchantVal.isNullOrBlank()) {
            workingPattern = replaceExactValue(workingPattern, merchantVal, "{MERCHANT}")
        }
        if (!accountVal.isNullOrBlank()) {
            workingPattern = replaceExactValue(workingPattern, accountVal, "{ACCOUNT}")
        }
        if (!dateVal.isNullOrBlank()) {
            workingPattern = replaceExactValue(workingPattern, dateVal, "{DATE}")
        }
        if (!amountVal.isNullOrBlank()) {
            workingPattern = replaceExactValue(workingPattern, amountVal, "{AMOUNT}")
        }

        return DetectedVariables(
            amount = amountVal,
            date = dateVal,
            account = accountVal,
            merchant = merchantVal,
            reference = refVal,
            balance = balVal,
            suggestedPattern = workingPattern
        )
    }

    private fun replaceExactValue(target: String, valueToReplace: String, tag: String): String {
        val index = target.indexOf(valueToReplace)
        if (index < 0) return target
        return target.substring(0, index) + tag + target.substring(index + valueToReplace.length)
    }
}
