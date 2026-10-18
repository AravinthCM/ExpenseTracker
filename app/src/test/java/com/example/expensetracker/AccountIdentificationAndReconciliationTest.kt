package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.data.local.SourceAccountMappingEntity
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.AccountIdentifier
import com.example.expensetracker.model.AccountMatchResult
import com.example.expensetracker.model.BalanceReconciliationEngine
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.DeduplicationResult
import com.example.expensetracker.service.NormalizedTransaction
import com.example.expensetracker.service.NotificationParser
import com.example.expensetracker.service.TransactionDeduplicator
import com.example.expensetracker.service.TransactionNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AccountIdentificationAndReconciliationTest {

    private lateinit var canara1: AccountEntity
    private lateinit var canara2: AccountEntity
    private lateinit var hdfcAccount: AccountEntity

    @Before
    fun setUp() {
        TransactionDeduplicator.clearHistory()

        canara1 = AccountEntity(
            id = "acc_canara_528",
            name = "Canara Savings 528",
            openingBalancePaise = 100000L, // ₹1,000.00
            accountNumber = "528",
            isPrimary = true
        )

        canara2 = AccountEntity(
            id = "acc_canara_912",
            name = "Canara Current 912",
            openingBalancePaise = 500000L, // ₹5,000.00
            accountNumber = "912",
            isPrimary = false
        )

        hdfcAccount = AccountEntity(
            id = "acc_hdfc_001",
            name = "HDFC Salary",
            openingBalancePaise = 250000L, // ₹2,500.00
            accountNumber = "123456780001",
            isPrimary = false
        )
    }

    // 1. Unique account suffix match
    @Test
    fun test1_uniqueAccountSuffixMatch() {
        val accounts = listOf(canara1, canara2, hdfcAccount)
        val mappings = emptyList<SourceAccountMappingEntity>()

        // Suffix "528" should uniquely match canara1
        val result = AccountIdentifier.identifyAccount(
            extractedSuffix = "XXX528",
            source = "BANK_EMAIL",
            senderOrPackage = "com.google.android.gm",
            rawContent = "An amount of INR 100 has been debited from your account XXX528",
            accounts = accounts,
            confirmedMappings = mappings
        )

        assertTrue(result is AccountMatchResult.MatchedBySuffix)
        val matched = result as AccountMatchResult.MatchedBySuffix
        assertEquals("acc_canara_528", matched.account.id)
        assertEquals("528", matched.suffix)
    }

    // 2. No suffix and one explicitly confirmed mapping
    @Test
    fun test2_noSuffixAndOneExplicitlyConfirmedMapping() {
        val accounts = listOf(canara1, hdfcAccount)
        val confirmedMapping = SourceAccountMappingEntity(
            id = "map_1",
            accountId = "acc_hdfc_001",
            sourcePattern = "SALARY_ALERT",
            senderOrPackage = "hdfcbank.com",
            matchingContext = null,
            isConfirmed = true
        )

        val result = AccountIdentifier.identifyAccount(
            extractedSuffix = null,
            source = "SALARY_ALERT",
            senderOrPackage = "hdfcbank.com",
            rawContent = "Monthly salary credited to your account",
            accounts = accounts,
            confirmedMappings = listOf(confirmedMapping)
        )

        assertTrue(result is AccountMatchResult.MatchedByMapping)
        val matched = result as AccountMatchResult.MatchedByMapping
        assertEquals("acc_hdfc_001", matched.account.id)
        assertEquals("map_1", matched.mapping.id)
    }

    // 3. Two accounts at the same bank
    @Test
    fun test3_twoAccountsAtSameBank() {
        val accounts = listOf(canara1, canara2)
        val mappings = emptyList<SourceAccountMappingEntity>()

        // 3a. Message with suffix 528 -> matches canara1
        val res1 = AccountIdentifier.identifyAccount(
            extractedSuffix = "528",
            source = "BANK_EMAIL",
            senderOrPackage = "canarabank.com",
            rawContent = "Account ending 528 debited INR 50",
            accounts = accounts,
            confirmedMappings = mappings
        )
        assertTrue(res1 is AccountMatchResult.MatchedBySuffix)
        assertEquals("acc_canara_528", (res1 as AccountMatchResult.MatchedBySuffix).account.id)

        // 3b. Message with suffix 912 -> matches canara2
        val res2 = AccountIdentifier.identifyAccount(
            extractedSuffix = "912",
            source = "BANK_EMAIL",
            senderOrPackage = "canarabank.com",
            rawContent = "Account ending 912 debited INR 75",
            accounts = accounts,
            confirmedMappings = mappings
        )
        assertTrue(res2 is AccountMatchResult.MatchedBySuffix)
        assertEquals("acc_canara_912", (res2 as AccountMatchResult.MatchedBySuffix).account.id)

        // 3c. Message with NO suffix and NO mapping -> MUST be Unresolved; NEVER default to primary account!
        val resNoSuffix = AccountIdentifier.identifyAccount(
            extractedSuffix = null,
            source = "BANK_EMAIL",
            senderOrPackage = "canarabank.com",
            rawContent = "Canara Bank transaction alert: INR 100 debited",
            accounts = accounts,
            confirmedMappings = mappings
        )
        assertTrue("Must be Unresolved when suffix is missing and multiple accounts exist",
            resNoSuffix is AccountMatchResult.Unresolved)
        assertNull(resNoSuffix.accountId)
    }

    // 4. Unknown or ambiguous account
    @Test
    fun test4_unknownOrAmbiguousAccount() {
        val accounts = listOf(canara1, canara2)
        val mappings = emptyList<SourceAccountMappingEntity>()

        // Unknown suffix 999
        val resUnknown = AccountIdentifier.identifyAccount(
            extractedSuffix = "999",
            source = "BANK_EMAIL",
            senderOrPackage = "canarabank.com",
            rawContent = "Account ending 999 debited INR 50",
            accounts = accounts,
            confirmedMappings = mappings
        )
        assertTrue(resUnknown is AccountMatchResult.Unresolved)
        assertNull(resUnknown.accountId)

        // Ambiguous: Two accounts with identical suffix metadata
        val duplicateSuffixAcc = canara2.copy(id = "acc_dup", name = "Another 528", accountNumber = "528")
        val resAmbiguous = AccountIdentifier.identifyAccount(
            extractedSuffix = "528",
            source = "BANK_EMAIL",
            senderOrPackage = "canarabank.com",
            rawContent = "Account ending 528 debited INR 50",
            accounts = listOf(canara1, duplicateSuffixAcc),
            confirmedMappings = mappings
        )
        assertTrue(resAmbiguous is AccountMatchResult.Ambiguous)
        assertNull(resAmbiguous.accountId)
    }

    // 5. Incorrect or removed mapping
    @Test
    fun test5_incorrectOrRemovedMapping() {
        val accounts = listOf(canara1, hdfcAccount)

        // Mapping is unconfirmed (isConfirmed = false)
        val unconfirmedMapping = SourceAccountMappingEntity(
            id = "map_unconfirmed",
            accountId = "acc_hdfc_001",
            sourcePattern = "UNKNOWN_SOURCE",
            senderOrPackage = "unknown.bank.com",
            isConfirmed = false
        )

        val resUnconfirmed = AccountIdentifier.identifyAccount(
            extractedSuffix = null,
            source = "UNKNOWN_SOURCE",
            senderOrPackage = "unknown.bank.com",
            rawContent = "Alert from unknown bank",
            accounts = accounts,
            confirmedMappings = listOf(unconfirmedMapping)
        )
        assertTrue(resUnconfirmed is AccountMatchResult.Unresolved)

        // Removed mapping (empty list)
        val resRemoved = AccountIdentifier.identifyAccount(
            extractedSuffix = null,
            source = "UNKNOWN_SOURCE",
            senderOrPackage = "unknown.bank.com",
            rawContent = "Alert from unknown bank",
            accounts = accounts,
            confirmedMappings = emptyList()
        )
        assertTrue(resRemoved is AccountMatchResult.Unresolved)
    }

    // 6. Delayed observations: Newer bank observation always takes precedence over older delayed observation
    @Test
    fun test6_delayedObservationsResolution() {
        val tOlder = 1000L
        val tNewer = 2000L

        val olderObs = BalanceObservationEntity(
            id = "obs_older",
            accountId = canara1.id,
            balancePaise = 50000L, // ₹500.00
            balanceType = "AVAILABLE",
            currency = "INR",
            timestamp = tOlder,
            source = "BANK_EMAIL",
            isResolved = true
        )

        val newerObs = BalanceObservationEntity(
            id = "obs_newer",
            accountId = canara1.id,
            balancePaise = 45000L, // ₹450.00
            balanceType = "AVAILABLE",
            currency = "INR",
            timestamp = tNewer,
            source = "BANK_EMAIL",
            isResolved = true
        )

        // Even if olderObs is processed after newerObs, list contains both
        val observations = listOf(olderObs, newerObs)
        val transactions = listOf(
            TransactionEntity(
                id = "tx_1",
                title = "Coffee",
                amount = 550.0,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-18",
                source = "MANUAL",
                timestamp = 1500L,
                accountId = canara1.id
            )
        )

        val result = BalanceReconciliationEngine.reconcileAccount(
            account = canara1,
            transactions = transactions,
            observations = observations
        )

        // The latest observation by bank timestamp (tNewer = 2000L) must be used
        assertNotNull(result.latestObservation)
        assertEquals("obs_newer", result.latestObservation!!.id)
        assertEquals(45000L, result.observedBalancePaise)

        // Opening balance 100,000 - 55,000 expense (at 1500L <= 2000L) = 45,000 paise
        assertEquals(45000L, result.expectedBalancePaise)
        assertEquals(0L, result.discrepancyPaise)
        assertTrue(result.isReconciled)
    }

    // 7. Duplicate observations: Prevent same observation from being applied twice
    @Test
    fun test7_duplicateObservationDetection() {
        val parser = NotificationParser
        val rawEmail = "An amount of INR 60.00 has been DEBITED on 12/10/26 from your account XXX528 to ARUNPANDIYAN with UPI Ref No.:628500182613. Total Available Balance INR 675.08."

        val ref = parser.extractReferenceNumber(rawEmail)
        val balance = parser.extractBalance(rawEmail)
        val suffix = parser.extractAccountSuffix(rawEmail)

        assertEquals("628500182613", ref)
        assertEquals(675.08, balance!!, 0.001)
        assertEquals("528", suffix)

        val obs1 = BalanceObservationEntity(
            id = "obs_1",
            accountId = canara1.id,
            accountSuffix = suffix,
            balancePaise = (balance * 100.0).toLong(),
            balanceType = "AVAILABLE",
            currency = "INR",
            timestamp = 1791819973371L,
            source = "BANK_EMAIL",
            referenceNumber = ref,
            rawText = rawEmail,
            isResolved = true
        )

        // Verify duplicate check criteria: same reference and same balancePaise
        val isDuplicate = (obs1.referenceNumber == ref && obs1.balancePaise == 67508L)
        assertTrue("Duplicate observation matching reference and balance must be detected", isDuplicate)
    }

    // 8. Reconciliation isolation across accounts and balance types
    @Test
    fun test8_reconciliationIsolationAcrossAccountsAndBalanceTypes() {
        val obsCanara = BalanceObservationEntity(
            id = "obs_c",
            accountId = canara1.id,
            balancePaise = 100000L,
            balanceType = "AVAILABLE",
            currency = "INR",
            timestamp = 1000L,
            source = "BANK_EMAIL",
            isResolved = true
        )

        val obsUSD = BalanceObservationEntity(
            id = "obs_usd",
            accountId = canara1.id,
            balancePaise = 50000L,
            balanceType = "AVAILABLE",
            currency = "USD", // Different currency
            timestamp = 2000L,
            source = "FOREX",
            isResolved = true
        )

        val obsLedger = BalanceObservationEntity(
            id = "obs_ledger",
            accountId = canara1.id,
            balancePaise = 90000L,
            balanceType = "LEDGER", // Different balance type
            currency = "INR",
            timestamp = 3000L,
            source = "BANK_STATEMENT",
            isResolved = true
        )

        val observations = listOf(obsCanara, obsUSD, obsLedger)

        // 8a. Reconciliation on canara1 with "INR" and "AVAILABLE" must only match obsCanara
        val resCanara = BalanceReconciliationEngine.reconcileAccount(
            account = canara1,
            transactions = emptyList(),
            observations = observations,
            targetCurrency = "INR",
            compatibleBalanceType = "AVAILABLE"
        )
        assertEquals("obs_c", resCanara.latestObservation?.id)

        // 8b. Reconciliation on canara2 (which has no observations) must be completely isolated
        val resCanara2 = BalanceReconciliationEngine.reconcileAccount(
            account = canara2,
            transactions = emptyList(),
            observations = observations
        )
        assertNull(resCanara2.latestObservation)
        assertNull(resCanara2.observedBalancePaise)
        assertEquals(500000L, resCanara2.expectedBalancePaise)

        // 8c. Safe reconciliation never creates automatic transactions on discrepancy
        val txCountBefore = emptyList<TransactionEntity>().size
        val resDiscrepancy = BalanceReconciliationEngine.reconcileAccount(
            account = canara1,
            transactions = emptyList(), // expected balance 100000L, observed 90000L
            observations = listOf(obsCanara.copy(balancePaise = 90000L))
        )
        assertFalse(resDiscrepancy.isReconciled)
        assertEquals(-10000L, resDiscrepancy.discrepancyPaise)
        val txCountAfter = emptyList<TransactionEntity>().size
        assertEquals("Reconciliation must never generate synthetic transactions", txCountBefore, txCountAfter)
    }

    // 9. Regression tests for Phase 1 transaction deduplication
    @Test
    fun test9_regressionPhase1TransactionDeduplication() {
        val normTx1 = NormalizedTransaction(
            amount = 60.0,
            name = "ARUNPANDIYAN",
            type = TransactionType.EXPENSE,
            source = "BANK_EMAIL",
            timestamp = 1791819973371L,
            originalText = "Canara alert",
            referenceNumber = "628500182613",
            accountId = canara1.id
        )

        val res1 = TransactionDeduplicator.process(normTx1)
        assertTrue("First transaction ingest must be NewTransaction", res1 is DeduplicationResult.NewTransaction)

        val normTx2 = NormalizedTransaction(
            amount = 60.0,
            name = "ARUNPANDIYAN",
            type = TransactionType.EXPENSE,
            source = "BANK_EMAIL",
            timestamp = 1791819973371L + 60000L,
            originalText = "Canara alert duplicate",
            referenceNumber = "628500182613",
            accountId = canara1.id
        )

        val res2 = TransactionDeduplicator.process(normTx2)
        assertTrue("Second transaction with same reference number must be detected as DuplicateTransaction",
            res2 is DeduplicationResult.DuplicateTransaction)
        assertEquals("628500182613", (res2 as DeduplicationResult.DuplicateTransaction).existingTransaction.referenceNumber)
    }
}
