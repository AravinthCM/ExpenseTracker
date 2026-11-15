package com.example.expensetracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GPayRequestParserTest {

    private val gpayPackage = "com.google.android.apps.nbu.paisa.user"

    @Test
    fun parsesScreenshotStyleSplitRequest() {
        val result = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Pay abdul ajeez ₹31.25 for '☕'",
            text = "New split request in 'Room expense'",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )

        requireNotNull(result)
        assertEquals("abdul ajeez", result.requesterRawName)
        assertEquals(3125L, result.amountPaise)
        assertEquals("☕", result.description)
        assertEquals("Room expense", result.groupNameRaw)
    }

    @Test
    fun parsesDecimalAmountPrecisely() {
        val result = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Pay Ramesh ₹99.99 for 'Snacks'",
            text = null,
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        requireNotNull(result)
        assertEquals(9999L, result.amountPaise)
    }

    @Test
    fun ignoresCompletedPaymentNotifications() {
        val paidYou = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "LOGESHWARAN S paid you ₹1.00",
            text = "Tap to view.",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        assertNull(paidYou)

        val paidTo = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Paid ₹149 to KFC",
            text = "Tap to view.",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        assertNull(paidTo)
    }

    @Test
    fun ignoresGroupOnlyNotificationWithoutAmountDetail() {
        val result = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "New split request in 'Room expense'",
            text = null,
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        assertNull(result)
    }

    @Test
    fun ignoresNonGPayPackages() {
        val result = GPayRequestParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Pay abdul ajeez ₹31.25 for 'Coffee'",
            text = null,
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        assertNull(result)
    }

    @Test
    fun sameRequestSameDayProducesStableRequestKey() {
        val first = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Pay abdul ajeez ₹31.25 for '☕'",
            text = "New split request in 'Room expense'",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        val second = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Pay abdul ajeez ₹31.25 for '☕'",
            text = "New split request in 'Room expense'",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_050_000L // a few seconds later, same notification update
        )
        requireNotNull(first)
        requireNotNull(second)
        assertEquals(first.requestKey, second.requestKey)
    }

    @Test
    fun sameContentDifferentDayProducesDifferentRequestKey() {
        val first = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Pay abdul ajeez ₹31.25 for '☕'",
            text = "New split request in 'Room expense'",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L
        )
        val secondDay = GPayRequestParser.parse(
            packageName = gpayPackage,
            title = "Pay abdul ajeez ₹31.25 for '☕'",
            text = "New split request in 'Room expense'",
            bigText = null,
            textLines = null,
            postTime = 1_700_000_000_000L + 24L * 60 * 60 * 1000
        )
        requireNotNull(first)
        requireNotNull(secondDay)
        assertNotEquals(first.requestKey, secondDay.requestKey)
    }
}
