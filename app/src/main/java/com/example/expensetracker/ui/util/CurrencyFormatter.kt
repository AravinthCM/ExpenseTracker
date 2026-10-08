package com.example.expensetracker.ui.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
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

    fun formatWithSign(amount: Double, isIncome: Boolean): String {
        val format = NumberFormat.getNumberInstance(localeIN)
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val formattedNumber = format.format(Math.abs(amount))
        return if (isIncome) "+₹$formattedNumber" else "-₹$formattedNumber"
    }
}
