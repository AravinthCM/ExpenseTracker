package com.example.expensetracker

import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.PaymentTemplateHelper
import com.example.expensetracker.service.PaymentTemplateMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTemplateMatcherTest {

    private val sample1 = "An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08."
    private val sample2 = "An amount of INR 56.00 has been DEBITED on 23/09/26 from your account XXX528 to ROWTHRI CHIN with UPI Ref No.:663248208866. Total Available Balance INR 308.08."

    private val templatePattern = "An amount of INR {AMOUNT} has been DEBITED on {DATE} from your account {ACCOUNT} to {MERCHANT} with UPI Ref No.:{REFERENCE}. Total Available Balance INR {BALANCE}."

    private val canaraTemplate = PaymentTemplateEntity(
        id = "canara_debit_1",
        name = "Canara Bank Debit Email",
        transactionType = "EXPENSE",
        sampleText = sample1,
        templatePattern = templatePattern,
        enabled = true
    )

    @Test
    fun testOriginal104CanaraMessage_MATCH() {
        val result = PaymentTemplateMatcher.matchTemplate(sample1, canaraTemplate)

        assertTrue("Original ₹104 Canara message should MATCH", result.matched)
        assertEquals(104.0, result.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals("THIRUCHENDUR", result.merchant)
        assertEquals("XXX528", result.account)
        assertEquals("663206421839", result.reference)
        assertEquals(364.08, result.balance!!, 0.001)
        assertEquals("23/09/26", result.dateStr)
    }

    @Test
    fun test56CanaraMessage_MATCH() {
        val result = PaymentTemplateMatcher.matchTemplate(sample2, canaraTemplate)

        assertTrue("₹56 Canara message should MATCH", result.matched)
        assertEquals(56.0, result.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals("ROWTHRI CHIN", result.merchant)
        assertEquals("XXX528", result.account)
        assertEquals("663248208866", result.reference)
        assertEquals(308.08, result.balance!!, 0.001)
        assertEquals("23/09/26", result.dateStr)
    }

    @Test
    fun testDifferentMerchant_MATCH() {
        val input = "An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to SWIGGY BANGALORE with UPI Ref No.:663206421839. Total Available Balance INR 364.08."
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertTrue("Different merchant should MATCH", result.matched)
        assertEquals("SWIGGY BANGALORE", result.merchant)
        assertEquals(104.0, result.amount!!, 0.001)
    }

    @Test
    fun testDifferentAmount_MATCH() {
        val input = "An amount of INR 1,250.75 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08."
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertTrue("Different amount should MATCH", result.matched)
        assertEquals(1250.75, result.amount!!, 0.001)
    }

    @Test
    fun testDifferentReference_MATCH() {
        val input = "An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:999888777666. Total Available Balance INR 364.08."
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertTrue("Different reference should MATCH", result.matched)
        assertEquals("999888777666", result.reference)
    }

    @Test
    fun testExtraTextBeforeTransaction_MATCH() {
        val input = "ALERT: Dear Customer, An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08."
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertTrue("Extra text before transaction should MATCH", result.matched)
        assertEquals(104.0, result.amount!!, 0.001)
        assertEquals("THIRUCHENDUR", result.merchant)
    }

    @Test
    fun testExtraTextAfterTransaction_MATCH() {
        val input = "An amount of INR 104.00 has been DEBITED on 23/09/26 from your account XXX528 to THIRUCHENDUR with UPI Ref No.:663206421839. Total Available Balance INR 364.08. Thank you for banking with Canara Bank."
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertTrue("Extra text after transaction should MATCH", result.matched)
        assertEquals(104.0, result.amount!!, 0.001)
        assertEquals("THIRUCHENDUR", result.merchant)
    }

    @Test
    fun testUnrelatedCanaraEmail_NO_MATCH() {
        val input = "Dear Customer, get 10% instant discount using your Canara Bank debit card on Flipkart Sale!"
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertFalse("Unrelated Canara email should NO MATCH", result.matched)
    }

    @Test
    fun testThankingYouAlone_NO_MATCH() {
        val input = "Thanking you for banking with Canara Bank."
        val result = PaymentTemplateMatcher.matchTemplate(input, canaraTemplate)

        assertFalse("'Thanking you for banking with Canara Bank' alone should NO MATCH", result.matched)
    }

    @Test
    fun testAutoDetectTemplateFromSample() {
        val detected = PaymentTemplateHelper.autoDetectTemplate(sample1)

        assertEquals("104.00", detected.amount)
        assertEquals("23/09/26", detected.date)
        assertEquals("XXX528", detected.account)
        assertEquals("THIRUCHENDUR", detected.merchant)
        assertEquals("663206421839", detected.reference)
        assertEquals("364.08", detected.balance)
        assertNotNull(detected.suggestedPattern)

        // Validate auto-detected pattern matches sample 1 and sample 2
        val autoTemplate1 = canaraTemplate.copy(templatePattern = detected.suggestedPattern)
        val matchResult1 = PaymentTemplateMatcher.matchTemplate(sample1, autoTemplate1)
        assertTrue("Auto-detected template pattern should match sample 1", matchResult1.matched)

        val matchResult2 = PaymentTemplateMatcher.matchTemplate(sample2, autoTemplate1)
        assertTrue("Auto-detected template pattern should match sample 2", matchResult2.matched)
        assertEquals(56.0, matchResult2.amount!!, 0.001)
        assertEquals("ROWTHRI CHIN", matchResult2.merchant)
    }
}
