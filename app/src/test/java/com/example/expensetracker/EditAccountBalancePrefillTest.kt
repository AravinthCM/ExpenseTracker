package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.AccountBalanceCalculator
import com.example.expensetracker.model.BalanceObservationApplier
import org.junit.Assert.assertEquals
import org.junit.Test

class EditAccountBalancePrefillTest {

    @Test
    fun newAccount_prefillsZero() {
        assertEquals(0L, AccountBalanceCalculator.resolveEditPrefillBalancePaise(null, null))
    }

    @Test
    fun accountWithNoBaseline_prefillsFromOpeningBalance() {
        val account = AccountEntity(id = "a1", name = "Cash", openingBalancePaise = 250000L)
        assertEquals(250000L, AccountBalanceCalculator.resolveEditPrefillBalancePaise(account, null))
    }

    @Test
    fun accountWithCapturedBaseline_prefillsFromKnownCurrentBalance_notOpeningBalance() {
        // Reproduces the reported bug: card shows -₹2,654.61 while opening balance is ₹0.
        val account = AccountEntity(
            id = "a1",
            name = "Canara Bank",
            openingBalancePaise = 0L,
            balanceBaselinePaise = -300000L,
            balanceBaselineTimestamp = 5_000L
        )
        val displayedCurrentBalancePaise = -265461L
        val prefill = AccountBalanceCalculator.resolveEditPrefillBalancePaise(account, displayedCurrentBalancePaise)
        assertEquals(-265461L, prefill)
    }

    @Test
    fun capturedBankBalance_flowsThroughToEditPrefill_afterBeingApplied() {
        val account = AccountEntity(id = "a1", name = "Canara Bank", openingBalancePaise = 0L)
        val observation = BalanceObservationEntity(
            id = "obs1",
            accountId = "a1",
            balancePaise = -300000L,
            timestamp = 5_000L,
            source = "BANK_SMS",
            isResolved = true
        )
        val updatedAccount = BalanceObservationApplier.applyIfNewer(account, observation)!!
        val laterTransactions = listOf(
            TransactionEntity("tx1", "Coffee", 345.39, "EXPENSE", "Food", "Today", "MANUAL", 6_000L, accountId = "a1")
        )
        val currentBalancePaise = AccountBalanceCalculator.calculateAccountBalancePaise(updatedAccount, laterTransactions)
        assertEquals(-334539L, currentBalancePaise) // -3000.00 - 345.39 = -3345.39

        val prefill = AccountBalanceCalculator.resolveEditPrefillBalancePaise(updatedAccount, currentBalancePaise)
        assertEquals(currentBalancePaise, prefill)
    }
}
