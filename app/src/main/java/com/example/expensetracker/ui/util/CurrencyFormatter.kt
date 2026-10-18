package com.example.expensetracker.ui.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    const val HIDDEN_AMOUNT = "••••"
    private val localeIN: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun format(amount: Double): String {
        val format = NumberFormat.getNumberInstance(localeIN)
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val isNegative = amount < 0
        val absAmount = Math.abs(amount)
        val formattedNumber = format.format(absAmount)
        return if (isNegative) "-₹$formattedNumber" else "₹$formattedNumber"
    }

    fun format(amount: Double, isVisible: Boolean): String {
        return if (isVisible) format(amount) else HIDDEN_AMOUNT
    }

    fun formatWithSign(amount: Double, isIncome: Boolean): String {
        val format = NumberFormat.getNumberInstance(localeIN)
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val formattedNumber = format.format(Math.abs(amount))
        return if (isIncome) "+₹$formattedNumber" else "-₹$formattedNumber"
    }

    fun formatPaise(paise: Long): String {
        return format(paise / 100.0)
    }

    fun formatPaise(paise: Long, isVisible: Boolean): String {
        return format(paise / 100.0, isVisible)
    }
}
