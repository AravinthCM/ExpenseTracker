package com.example.expensetracker

import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.service.TransactionDetectionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDetectionCoordinatorTest {

    private val sample1 = "An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08."

    private val templatePattern = "An amount of INR {AMOUNT} has been DEBITED on {DATE} from your account {ACCOUNT} to {MERCHANT} with UPI Ref No.:{REFERENCE}. Total Available Balance INR {BALANCE}."

    private val canaraTemplate = PaymentTemplateEntity(
        id = "canara_1",
        name = "Canara Debit",
        transactionType = "EXPENSE",
        sampleText = sample1,
        templatePattern = templatePattern,
        enabled = true
    )

    @Test
    fun testCoordinator_UsesTemplateWhenMatched() {
        val result = TransactionDetectionCoordinator.detectTransaction(
            packageName = "com.google.android.gm",
            title = "Canara Bank Alert",
            text = sample1,
            postTime = System.currentTimeMillis(),
            enabledTemplates = listOf(canaraTemplate)
        )

        assertTrue(result.detectedPayment)
        assertEquals("TEMPLATE", result.detectionMethod)
        assertEquals("TEMPLATE_MATCH", result.confidence)
        assertEquals("Canara Debit", result.matchedTemplateName)
        assertEquals(104.0, result.amount!!, 0.001)
        assertEquals("THIRUCHENDUR", result.merchant)
        assertEquals("XXX528", result.accountNumber)
        assertEquals("663206421839", result.referenceNumber)
        assertNotNull(result.parsedTransaction)
    }

    @Test
    fun testCoordinator_FallsBackToGenericWhenNoTemplateMatches() {
        val genericSms = "IDBI Bank Acct XX617 debited for Rs 1.00 on 20-Sep-26; Bal Rs 16599.27 ARAVINTH C M credited."

        val result = TransactionDetectionCoordinator.detectTransaction(
            packageName = "com.google.android.apps.messaging",
            title = "IDBIBK",
            text = genericSms,
            postTime = System.currentTimeMillis(),
            enabledTemplates = listOf(canaraTemplate) // Template won't match IDBI SMS
        )

        assertTrue("Generic parser should catch IDBI SMS", result.detectedPayment)
        assertEquals("GENERIC", result.detectionMethod)
        assertEquals("GENERIC_HIGH_CONFIDENCE", result.confidence)
        assertEquals(1.0, result.amount!!, 0.001)
        assertNotNull(result.parsedTransaction)
    }

    @Test
    fun testCoordinator_ReturnsNotDetectedForNonTransaction() {
        val spam = "Welcome to Swiggy! Enjoy free delivery on your first 3 orders."

        val result = TransactionDetectionCoordinator.detectTransaction(
            packageName = "com.google.android.gm",
            title = "Swiggy Offer",
            text = spam,
            postTime = System.currentTimeMillis(),
            enabledTemplates = listOf(canaraTemplate)
        )

        assertFalse(result.detectedPayment)
        assertEquals("NOT_DETECTED", result.detectionMethod)
        assertEquals("NOT_TRANSACTION", result.confidence)
    }
}
