package com.example.expensetracker.service

import com.example.expensetracker.model.GPayRequestMatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses Google Pay "payment request" notifications (someone asking the user to pay them),
 * as distinct from completed payments / outgoing payments / ordinary GPay notifications.
 *
 * Supported formats (observed from real device notifications):
 *  - "Pay abdul ajeez ₹31.25 for '☕'"              -> requester, amount, description
 *  - "New split request in 'Room expense'"          -> group name (paired with the line above)
 */
object GPayRequestParser {

    const val GPAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"

    data class ParsedGPayRequest(
        val requesterRawName: String,
        val amountPaise: Long,
        val description: String,
        val groupNameRaw: String?,
        val postTime: Long,
        val requestKey: String
    )

    // "New split request in 'Room expense'" / supports straight and curly quotes
    private val groupRequestRegex = Regex(
        pattern = """New\s+split\s+request\s+in\s+['"‘’](.+?)['"‘’]""",
        option = RegexOption.IGNORE_CASE
    )

    // "Pay abdul ajeez ₹31.25 for '☕'" — must be a whole word "Pay" (not "Paid"/"Payment")
    private val payRequestRegex = Regex(
        pattern = """(?:^|\n)\s*\bPay\b\s+(.+?)\s+₹\s*([\d,]+(?:\.\d{1,2})?)\s+for\s+['"‘’](.+?)['"‘’]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    private val dayBucketFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Returns a parsed request only when the notification unambiguously represents a
     * payment *request* (requester + amount + description all present). Notifications with
     * only a group-name line, or any other GPay wording (paid you / paid / sent), are ignored here.
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        textLines: String?,
        postTime: Long
    ): ParsedGPayRequest? {
        if (packageName != GPAY_PACKAGE) return null

        val combined = listOfNotNull(title, text, bigText, textLines)
            .joinToString("\n")
            .trim()
        if (combined.isBlank()) return null

        val payMatch = payRequestRegex.find(combined) ?: return null

        val requesterName = payMatch.groupValues[1].trim()
        val rawAmount = payMatch.groupValues[2].replace(",", "")
        val amount = rawAmount.toDoubleOrNull() ?: return null
        if (amount <= 0 || requesterName.isBlank()) return null

        val description = payMatch.groupValues[3].trim()
        val groupName = groupRequestRegex.find(combined)?.groupValues?.get(1)?.trim()
        val amountPaise = Math.round(amount * 100.0)

        val requestKey = buildRequestKey(requesterName, amountPaise, description, groupName, postTime)

        return ParsedGPayRequest(
            requesterRawName = requesterName,
            amountPaise = amountPaise,
            description = description,
            groupNameRaw = groupName,
            postTime = postTime,
            requestKey = requestKey
        )
    }

    /**
     * Content-based fingerprint, stable across notification *updates* for the same request
     * (so re-delivery / in-place updates don't create duplicates), but bucketed by day so two
     * genuinely separate requests with coincidentally identical requester/amount/description
     * (e.g. a recurring weekly coffee request) are still treated as distinct.
     */
    private fun buildRequestKey(
        requesterRawName: String,
        amountPaise: Long,
        description: String,
        groupName: String?,
        postTime: Long
    ): String {
        val normalizedName = GPayRequestMatcher.normalizeName(requesterRawName)
        val normalizedDesc = GPayRequestMatcher.normalizeName(description)
        val normalizedGroup = groupName?.let { GPayRequestMatcher.normalizeName(it) } ?: ""
        val dayBucket = dayBucketFormat.format(Date(postTime))
        return listOf(normalizedName, amountPaise.toString(), normalizedDesc, normalizedGroup, dayBucket)
            .joinToString("|")
    }
}
