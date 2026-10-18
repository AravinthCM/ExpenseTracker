package com.example.expensetracker.model

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.SourceAccountMappingEntity

sealed class AccountMatchResult {
    data class MatchedBySuffix(val account: AccountEntity, val suffix: String) : AccountMatchResult()
    data class MatchedByMapping(val account: AccountEntity, val mapping: SourceAccountMappingEntity) : AccountMatchResult()
    data class Ambiguous(val candidateAccounts: List<AccountEntity>, val reason: String) : AccountMatchResult()
    data class Unresolved(val reason: String) : AccountMatchResult()

    val accountId: String?
        get() = when (this) {
            is MatchedBySuffix -> account.id
            is MatchedByMapping -> account.id
            is Ambiguous, is Unresolved -> null
        }
}

object AccountIdentifier {

    /**
     * Cleans an extracted account number or suffix by removing masking characters (X, *, etc.)
     * and non-alphanumeric noise, returning the trailing digits/identifying suffix.
     */
    fun cleanSuffix(rawSuffix: String?): String? {
        if (rawSuffix.isNullOrBlank()) return null
        val cleaned = rawSuffix.trim()
            .replace(Regex("^[Xx*.]+"), "")
            .trim()
        val digitsOnly = cleaned.filter { it.isLetterOrDigit() }
        return if (digitsOnly.length >= 2) digitsOnly else null
    }

    /**
     * Identifies the target account for a transaction or balance observation based on strict priority:
     * 1. Extracted account suffix matching saved account metadata (accountNumber or name). Must be unique.
     * 2. Confirmed persistent source-to-account mapping (isConfirmed = true).
     * 3. If neither matches uniquely, returns Ambiguous or Unresolved (never defaults to primary account or bank name alone).
     */
    fun identifyAccount(
        extractedSuffix: String?,
        source: String,
        senderOrPackage: String?,
        rawContent: String,
        accounts: List<AccountEntity>,
        confirmedMappings: List<SourceAccountMappingEntity>
    ): AccountMatchResult {
        val activeAccounts = accounts.filter { !it.isArchived }
        val cleanedSuffix = cleanSuffix(extractedSuffix)

        // Step 1: Match by extracted suffix
        if (!cleanedSuffix.isNullOrEmpty()) {
            val suffixMatches = activeAccounts.filter { account ->
                matchesAccountSuffix(account, cleanedSuffix)
            }

            if (suffixMatches.size == 1) {
                return AccountMatchResult.MatchedBySuffix(suffixMatches.first(), cleanedSuffix)
            } else if (suffixMatches.size > 1) {
                return AccountMatchResult.Ambiguous(
                    candidateAccounts = suffixMatches,
                    reason = "Multiple accounts match suffix '$cleanedSuffix': ${suffixMatches.map { it.name }}"
                )
            }
            // If suffix was present but matched 0 accounts, we do NOT automatically fall back to mappings
            // that might misattribute a different account at the same bank!
        }

        // Step 2: If no suffix match, check explicitly confirmed source mappings
        val validMappings = confirmedMappings.filter { mapping ->
            mapping.isConfirmed && activeAccounts.any { it.id == mapping.accountId }
        }

        val matchingMappings = validMappings.filter { mapping ->
            matchesSourceMapping(mapping, source, senderOrPackage, rawContent)
        }

        val mappedAccountIds = matchingMappings.map { it.accountId }.distinct()
        if (mappedAccountIds.size == 1) {
            val matchedAccount = activeAccounts.first { it.id == mappedAccountIds.first() }
            val matchedMapping = matchingMappings.first { it.accountId == matchedAccount.id }
            return AccountMatchResult.MatchedByMapping(matchedAccount, matchedMapping)
        } else if (mappedAccountIds.size > 1) {
            val candidates = activeAccounts.filter { it.id in mappedAccountIds }
            return AccountMatchResult.Ambiguous(
                candidateAccounts = candidates,
                reason = "Multiple confirmed source mappings match this source: ${candidates.map { it.name }}"
            )
        }

        // Step 3: Could not reliably identify account -> Unresolved
        val reason = if (!cleanedSuffix.isNullOrEmpty()) {
            "Account suffix '$cleanedSuffix' did not match any active account"
        } else {
            "No account suffix found and no confirmed source mapping matched"
        }
        return AccountMatchResult.Unresolved(reason)
    }

    private fun matchesAccountSuffix(account: AccountEntity, suffix: String): Boolean {
        // 1. Check account.accountNumber if present
        if (!account.accountNumber.isNullOrBlank()) {
            val cleanAccNum = account.accountNumber.trim().filter { it.isLetterOrDigit() }
            if (cleanAccNum.equals(suffix, ignoreCase = true) ||
                cleanAccNum.endsWith(suffix, ignoreCase = true) ||
                suffix.endsWith(cleanAccNum, ignoreCase = true)
            ) {
                return true
            }
        }

        // 2. Check account.name for suffix digits
        val nameWords = account.name.split(Regex("[\\s-_/.,()]+")).map { it.filter { char -> char.isLetterOrDigit() } }
        for (word in nameWords) {
            if (word.equals(suffix, ignoreCase = true) ||
                (word.length >= suffix.length && word.endsWith(suffix, ignoreCase = true))
            ) {
                return true
            }
        }

        return false
    }

    private fun matchesSourceMapping(
        mapping: SourceAccountMappingEntity,
        source: String,
        senderOrPackage: String?,
        rawContent: String
    ): Boolean {
        // Check sourcePattern
        val patternMatch = mapping.sourcePattern.equals(source, ignoreCase = true) ||
                mapping.sourcePattern.equals("*") ||
                (senderOrPackage != null && mapping.sourcePattern.contains(senderOrPackage, ignoreCase = true))

        if (!patternMatch) return false

        // Check senderOrPackage if specified in mapping
        if (!mapping.senderOrPackage.isNullOrBlank()) {
            if (senderOrPackage == null || !senderOrPackage.contains(mapping.senderOrPackage, ignoreCase = true)) {
                return false
            }
        }

        // Check matchingContext if specified in mapping
        if (!mapping.matchingContext.isNullOrBlank()) {
            if (!rawContent.contains(mapping.matchingContext, ignoreCase = true)) {
                return false
            }
        }

        return true
    }
}
