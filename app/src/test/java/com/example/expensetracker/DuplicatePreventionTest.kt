package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.data.local.toDomainModel
import com.example.expensetracker.data.local.toEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.DeduplicationResult
import com.example.expensetracker.service.DuplicateConfidence
import com.example.expensetracker.service.DuplicateReviewHelper
import com.example.expensetracker.service.NormalizedTransaction
import com.example.expensetracker.service.NotificationParser
import com.example.expensetracker.service.ParsedTransaction
import com.example.expensetracker.service.TransactionDeduplicator
import com.example.expensetracker.service.TransactionDetectionCoordinator
import com.example.expensetracker.service.TransactionNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DuplicatePreventionTest {

    @Before
    fun setUp() {
        TransactionDeduplicator.clearHistory()
    }

    @Test
    fun test1_sameBankReferenceProcessedTwiceProducesDuplicateResult() {
        val emailBody = """
            An amount of INR 60.00 has been DEBITED on 12/10/26 from your account XXX528 to ARUNPANDIYAN with UPI Ref No.:628500182613. Total Available Balance INR 675.08.
        """.trimIndent()

        val parsed1 = NotificationParser.parse(
            packageName = "com.google.android.gm",
            title = "UPI/IMPS/MB Transaction Alert",
            text = emailBody,
            postTime = 1791819973371L
        )
        assertNotNull(parsed1)
        assertEquals("628500182613", parsed1!!.referenceNumber)
        assertEquals(60.0, parsed1.amount, 0.001)

        val norm1 = TransactionNormalizer.normalize(parsed1, emailBody)
        assertEquals("628500182613", norm1.referenceNumber)

        val result1 = TransactionDeduplicator.process(norm1)
        assertTrue("First ingest should be NewTransaction", result1 is DeduplicationResult.NewTransaction)

        // Second ingest with same reference (even if timestamp is 15 minutes later!)
        val parsed2 = NotificationParser.parse(
            packageName = "com.google.android.gm",
            title = "UPI/IMPS/MB Transaction Alert",
            text = emailBody,
            postTime = 1791819973371L + (15 * 60 * 1000L)
        )
        assertNotNull(parsed2)
        val norm2 = TransactionNormalizer.normalize(parsed2!!, emailBody)

        val result2 = TransactionDeduplicator.process(norm2)
        assertTrue("Second ingest with identical bank reference must be detected as DuplicateTransaction",
            result2 is DeduplicationResult.DuplicateTransaction)
        val dup = result2 as DeduplicationResult.DuplicateTransaction
        assertEquals("628500182613", dup.existingTransaction.referenceNumber)
    }

    @Test
    fun test2_concurrentAttemptsToProcessSameIdentifiableTransactionCannotCreateDuplicates() {
        val emailBody = """
            An amount of INR 60.00 has been DEBITED on 12/10/26 from your account XXX528 to ARUNPANDIYAN with UPI Ref No.:628500182613. Total Available Balance INR 675.08.
        """.trimIndent()

        val parsed = NotificationParser.parse(
            packageName = "com.google.android.gm",
            title = "UPI/IMPS/MB Transaction Alert",
            text = emailBody,
            postTime = 1791819973371L
        )!!
        val norm = TransactionNormalizer.normalize(parsed, emailBody)

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val newCount = AtomicInteger(0)
        val dupCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val res = TransactionDeduplicator.process(norm)
                    if (res is DeduplicationResult.NewTransaction) {
                        newCount.incrementAndGet()
                    } else if (res is DeduplicationResult.DuplicateTransaction) {
                        dupCount.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await()
        executor.shutdown()

        assertEquals("Exactly one concurrent thread should produce NewTransaction", 1, newCount.get())
        assertEquals("All other 9 concurrent threads must be detected as duplicates", 9, dupCount.get())
    }

    @Test
    fun test3_twoLegitimatePurchasesWithSameAmountAndMerchantRemainSeparateWhenReferencesDiffer() {
        val emailBody1 = """
            An amount of INR 60.00 has been DEBITED on 12/10/26 from your account XXX528 to ARUNPANDIYAN with UPI Ref No.:628500182613. Total Available Balance INR 675.08.
        """.trimIndent()
        val emailBody2 = """
            An amount of INR 60.00 has been DEBITED on 12/10/26 from your account XXX528 to ARUNPANDIYAN with UPI Ref No.:999888777666. Total Available Balance INR 615.08.
        """.trimIndent()

        val parsed1 = NotificationParser.parse("com.google.android.gm", "Alert", emailBody1, 1000L)!!
        val parsed2 = NotificationParser.parse("com.google.android.gm", "Alert", emailBody2, 1000L + 5000L)!! // 5s later

        val norm1 = TransactionNormalizer.normalize(parsed1, emailBody1)
        val norm2 = TransactionNormalizer.normalize(parsed2, emailBody2)

        val result1 = TransactionDeduplicator.process(norm1)
        val result2 = TransactionDeduplicator.process(norm2)

        assertTrue("First purchase is new", result1 is DeduplicationResult.NewTransaction)
        assertTrue("Second legitimate purchase with distinct UPI reference must be NewTransaction",
            result2 is DeduplicationResult.NewTransaction)
    }

    @Test
    fun test4_transactionsWithoutReferenceNumbersFollowSafeTimeWindowPolicy() {
        // Without reference numbers (e.g. cash or manual notification)
        val tx1 = NormalizedTransaction(
            amount = 50.0,
            name = "Coffee Shop",
            type = TransactionType.EXPENSE,
            source = "MANUAL",
            timestamp = 100000L,
            originalText = "",
            referenceNumber = null
        )

        val result1 = TransactionDeduplicator.process(tx1)
        assertTrue(result1 is DeduplicationResult.NewTransaction)

        // Same amount and merchant within 2 minutes without ref -> detected as duplicate
        val txDuplicate = tx1.copy(timestamp = 100000L + 30000L) // 30s later
        val resultDup = TransactionDeduplicator.process(txDuplicate)
        assertTrue("Same amount and merchant within 2 minutes is detected as duplicate",
            resultDup is DeduplicationResult.DuplicateTransaction)

        // Same amount and merchant after 5 minutes without ref -> allowed as separate purchase
        val txLater = tx1.copy(timestamp = 100000L + (5 * 60 * 1000L)) // 5 minutes later
        val resultLater = TransactionDeduplicator.process(txLater)
        assertTrue("Legitimate recurring purchase 5 minutes later without reference must not be suppressed",
            resultLater is DeduplicationResult.NewTransaction)
    }

    @Test
    fun test5_editingTransactionDoesNotCreateDuplicate() {
        val original = Transaction(
            id = "tx-123",
            title = "ARUNPANDIYAN",
            amount = 60.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.OTHER,
            date = "Today",
            source = TransactionSource.BANK_EMAIL,
            timestamp = 1791819973371L,
            originalText = "Ref No.:628500182613",
            accountId = "acc-canara",
            referenceNumber = "628500182613"
        )

        val entity = original.toEntity()
        assertEquals("tx-123", entity.id)
        assertEquals("628500182613", entity.referenceNumber)

        // Edit category to FOOD
        val edited = original.copy(category = TransactionCategory.FOOD)
        val editedEntity = edited.toEntity()

        assertEquals("tx-123", editedEntity.id)
        assertEquals("FOOD", editedEntity.category)
        assertEquals("628500182613", editedEntity.referenceNumber)
        // Entity preserves same ID, so @Update performs in-place update without creating second transaction
    }

    @Test
    fun test6_bankSmsWithUpiReferenceIsCorrectlyExtracted() {
        val sms = "AD-IDBIBK-S IDBI Bank Acct XX617 debited for Rs 399.00 on 16-Oct-26; Bal Rs 21949.92 MYJIO credited. UPI:131396891040. To Block UPI send SMS..."
        val parsed = NotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "IDBIBK",
            text = sms,
            postTime = 1792158688968L
        )

        assertNotNull(parsed)
        assertEquals(399.0, parsed!!.amount, 0.001)
        assertEquals("MYJIO", parsed.name)
        assertEquals("131396891040", parsed.referenceNumber)
        assertEquals(TransactionSource.BANK_SMS.name, parsed.source)
    }

    @Test
    fun test7_duplicateReviewHelperIdentifiesHighConfidenceAndUncertainDuplicates() {
        val tx1 = TransactionEntity(
            id = "1",
            title = "ARUNPANDIYAN",
            amount = 60.0,
            type = "EXPENSE",
            category = "Other",
            date = "Today",
            source = "BANK_EMAIL",
            timestamp = 1000L,
            originalText = "with UPI Ref No.:628500182613",
            referenceNumber = "628500182613"
        )
        val tx2 = TransactionEntity(
            id = "2",
            title = "ARUNPANDIYAN",
            amount = 60.0,
            type = "EXPENSE",
            category = "Other",
            date = "Today",
            source = "BANK_EMAIL",
            timestamp = 1020L, // 20ms later
            originalText = "with UPI Ref No.:628500182613",
            referenceNumber = "628500182613"
        )
        val tx3 = TransactionEntity(
            id = "3",
            title = "Tea Stall",
            amount = 15.0,
            type = "EXPENSE",
            category = "Food",
            date = "2026-10-12",
            source = "MANUAL",
            timestamp = 200000L,
            originalText = "",
            referenceNumber = null
        )
        val tx4 = TransactionEntity(
            id = "4",
            title = "Tea Stall",
            amount = 15.0,
            type = "EXPENSE",
            category = "Food",
            date = "2026-10-12",
            source = "MANUAL",
            timestamp = 500000L, // Hours later on same day
            originalText = "",
            referenceNumber = null
        )

        val duplicates = DuplicateReviewHelper.findDuplicates(listOf(tx1, tx2, tx3, tx4))
        assertEquals(2, duplicates.size)

        val highConfidence = duplicates.first { it.confidence == DuplicateConfidence.HIGH_CONFIDENCE }
        assertEquals(2, highConfidence.transactions.size)
        assertTrue(highConfidence.reason.contains("628500182613"))

        val uncertain = duplicates.first { it.confidence == DuplicateConfidence.UNCERTAIN }
        assertEquals(2, uncertain.transactions.size)
        assertTrue(uncertain.reason.contains("2026-10-12"))
    }
}
