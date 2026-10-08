package com.example.expensetracker.service

import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.model.TransactionType

object PaymentTemplateMatcher {

    private val PLACEHOLDER_REGEX = Regex("""\{([A-Z_]+)\}""")

    fun normalizeText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n+"""), "\n")
            .trim()
    }

    fun match(rawNotificationText: String, enabledTemplates: List<PaymentTemplateEntity>): TemplateMatchResult {
        if (rawNotificationText.isBlank() || enabledTemplates.isEmpty()) {
            return TemplateMatchResult(matched = false)
        }

        val normalizedInput = normalizeText(rawNotificationText)

        for (template in enabledTemplates) {
            if (!template.enabled) continue

            val matchResult = matchTemplate(normalizedInput, template)
            if (matchResult.matched) {
                return matchResult
            }
        }

        return TemplateMatchResult(matched = false)
    }

    fun matchTemplate(rawNotificationText: String, template: PaymentTemplateEntity): TemplateMatchResult {
        val normalizedInput = normalizeText(rawNotificationText)
        val normalizedPattern = normalizeText(template.templatePattern)

        if (normalizedPattern.isBlank() || normalizedInput.isBlank()) {
            return TemplateMatchResult(matched = false)
        }

        // 1. Identify order of placeholders in template pattern
        val placeholders = PLACEHOLDER_REGEX.findAll(normalizedPattern).map { it.groupValues[1] }.toList()

        // 2. Build regex pattern from template string
        val regexPatternBuilder = StringBuilder()
        var lastIndex = 0

        for (match in PLACEHOLDER_REGEX.findAll(normalizedPattern)) {
            val literalText = normalizedPattern.substring(lastIndex, match.range.first)
            regexPatternBuilder.append(escapeLiteral(literalText))

            val placeholderName = match.groupValues[1]
            when (placeholderName) {
                "AMOUNT" -> regexPatternBuilder.append("""([\d,]+(?:\.\d{1,2})?)""")
                "DATE" -> regexPatternBuilder.append("""([A-Za-z0-9/\.-]+)""")
                "ACCOUNT" -> regexPatternBuilder.append("""([X\*\d\w]+)""")
                "MERCHANT" -> regexPatternBuilder.append("""(.+?)""")
                "REFERENCE" -> regexPatternBuilder.append("""([A-Za-z0-9]+)""")
                "BALANCE" -> regexPatternBuilder.append("""([\d,]+(?:\.\d{1,2})?)""")
                else -> regexPatternBuilder.append("""([^\s,;\.]+?)""")
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < normalizedPattern.length) {
            val tailLiteral = normalizedPattern.substring(lastIndex)
            regexPatternBuilder.append(escapeLiteral(tailLiteral))
        }

        val compiledRegex = try {
            Regex(regexPatternBuilder.toString(), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        } catch (e: Exception) {
            return TemplateMatchResult(matched = false)
        }

        val match = compiledRegex.find(normalizedInput) ?: return TemplateMatchResult(matched = false)

        // Verify group count matches total placeholders
        if (match.groupValues.size - 1 < placeholders.size) {
            return TemplateMatchResult(matched = false)
        }

        var amount: Double? = null
        var merchant: String? = null
        var account: String? = null
        var reference: String? = null
        var balance: Double? = null
        var dateStr: String? = null

        for (i in placeholders.indices) {
            val name = placeholders[i]
            val capturedValue = match.groupValues[i + 1].trim()

            when (name) {
                "AMOUNT" -> {
                    val cleanAmount = capturedValue.replace(",", "")
                    amount = cleanAmount.toDoubleOrNull()
                }
                "MERCHANT" -> merchant = capturedValue.takeIf { it.isNotBlank() }
                "ACCOUNT" -> account = capturedValue.takeIf { it.isNotBlank() }
                "REFERENCE" -> reference = capturedValue.takeIf { it.isNotBlank() }
                "BALANCE" -> {
                    val cleanBal = capturedValue.replace(",", "")
                    balance = cleanBal.toDoubleOrNull()
                }
                "DATE" -> dateStr = capturedValue.takeIf { it.isNotBlank() }
            }
        }

        // Amount must be valid and positive to qualify as a valid transaction match
        if (amount == null || amount <= 0) {
            return TemplateMatchResult(matched = false)
        }

        val type = try {
            TransactionType.valueOf(template.transactionType.uppercase())
        } catch (e: Exception) {
            if (template.transactionType.equals("DEBIT", ignoreCase = true)) TransactionType.EXPENSE
            else if (template.transactionType.equals("CREDIT", ignoreCase = true)) TransactionType.INCOME
            else TransactionType.EXPENSE
        }

        return TemplateMatchResult(
            matched = true,
            templateId = template.id,
            templateName = template.name,
            amount = amount,
            merchant = merchant,
            account = account,
            reference = reference,
            balance = balance,
            dateStr = dateStr,
            transactionType = type
        )
    }

    private fun escapeLiteral(literal: String): String {
        if (literal.isEmpty()) return ""
        // Escape standard regex tokens, turn literal spaces / whitespace into \s+
        val escaped = Regex.escape(literal)
        return escaped.replace("""\ """, """\s+""").replace("""\n""", """\s+""")
    }
}
