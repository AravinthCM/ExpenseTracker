package com.example.expensetracker

import com.example.expensetracker.ui.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterPrivacyTest {

    @Test
    fun hiddenAmountsUseOneConsistentMask() {
        assertEquals(CurrencyFormatter.HIDDEN_AMOUNT, CurrencyFormatter.format(123.45, false))
        assertEquals(CurrencyFormatter.HIDDEN_AMOUNT, CurrencyFormatter.format(-123.45, false))
        assertEquals(CurrencyFormatter.HIDDEN_AMOUNT, CurrencyFormatter.formatPaise(12345L, false))
    }

    @Test
    fun nonDashboardFormattingRemainsVisible() {
        assertEquals("₹123.45", CurrencyFormatter.format(123.45))
        assertEquals("₹123.45", CurrencyFormatter.format(123.45, true))
        assertEquals("-₹123.45", CurrencyFormatter.format(-123.45, true))
        assertEquals("₹123.45", CurrencyFormatter.formatPaise(12345L, true))
    }

    @Test
    fun maskingDoesNotChangeTheInputValue() {
        val amount = -0.5
        CurrencyFormatter.format(amount, false)
        assertEquals(-0.5, amount, 0.0)
    }
}