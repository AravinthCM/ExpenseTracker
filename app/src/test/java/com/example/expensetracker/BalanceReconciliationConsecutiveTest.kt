package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.BalanceReconciliationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceReconciliationConsecutiveTest {
    private val account = AccountEntity("acc", "Checking", 0L)

    private fun observation(id: String, balance: Long, timestamp: Long) = BalanceObservationEntity(
        id = id,
        accountId = account.id,
        balancePaise = balance,
        timestamp = timestamp,
        source = "BANK",
        isResolved = true
    )

    @Test
    fun laterObservationUsesOnlyTransactionsAfterEarlierObservation() {
        val result = BalanceReconciliationEngine.reconcileAccount(
            account,
            transactions = listOf(
                TransactionEntity("before", "Before", 999.0, "EXPENSE", "Other", "2026-01-01", "MANUAL", 500L, accountId = account.id),
                TransactionEntity("between", "Coffee", 3.0, "EXPENSE", "Food", "2026-01-01", "MANUAL", 1500L, accountId = account.id),
                TransactionEntity("after", "After", 50.0, "EXPENSE", "Other", "2026-01-01", "MANUAL", 2500L, accountId = account.id)
            ),
            observations = listOf(observation("early", 10000L, 1000L), observation("late", 9700L, 2000L))
        )

        assertEquals(9700L, result.expectedBalancePaise)
        assertEquals(0L, result.discrepancyPaise)
        assertTrue(result.isReconciled)
    }
}