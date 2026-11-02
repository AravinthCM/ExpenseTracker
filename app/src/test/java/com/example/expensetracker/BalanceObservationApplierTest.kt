package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.model.BalanceObservationApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceObservationApplierTest {

    private val account = AccountEntity(
        id = "acc1",
        name = "Canara Bank",
        openingBalancePaise = 0L,
        balanceBaselinePaise = 100000L,
        balanceBaselineTimestamp = 5_000L
    )

    private fun observation(
        accountId: String? = "acc1",
        balancePaise: Long,
        timestamp: Long,
        isResolved: Boolean = true,
        currency: String = "INR",
        balanceType: String = "AVAILABLE"
    ) = BalanceObservationEntity(
        id = "obs-$timestamp",
        accountId = accountId,
        balancePaise = balancePaise,
        timestamp = timestamp,
        source = "BANK_SMS",
        isResolved = isResolved,
        currency = currency,
        balanceType = balanceType
    )

    @Test
    fun newerValidObservation_updatesBaseline() {
        val obs = observation(balancePaise = 265461L, timestamp = 10_000L)
        val result = BalanceObservationApplier.applyIfNewer(account, obs)
        assertEquals(265461L, result?.balanceBaselinePaise)
        assertEquals(10_000L, result?.balanceBaselineTimestamp)
    }

    @Test
    fun olderDelayedObservation_cannotReplaceNewerBalance() {
        val obs = observation(balancePaise = 500L, timestamp = 1_000L) // older than account's 5_000L baseline
        val result = BalanceObservationApplier.applyIfNewer(account, obs)
        assertNull(result)
    }

    @Test
    fun unresolvedObservation_cannotUpdateAnyAccount() {
        val obs = observation(balancePaise = 999999L, timestamp = 10_000L, isResolved = false)
        val result = BalanceObservationApplier.applyIfNewer(account, obs)
        assertNull(result)
    }

    @Test
    fun observationForDifferentAccount_isIsolated() {
        val obs = observation(accountId = "acc2", balancePaise = 999999L, timestamp = 10_000L)
        val result = BalanceObservationApplier.applyIfNewer(account, obs)
        assertNull(result)
    }

    @Test
    fun mismatchedCurrency_isRejected() {
        val obs = observation(balancePaise = 999999L, timestamp = 10_000L, currency = "USD")
        val result = BalanceObservationApplier.applyIfNewer(account, obs)
        assertNull(result)
    }

    @Test
    fun mismatchedBalanceType_isRejected() {
        val obs = observation(balancePaise = 999999L, timestamp = 10_000L, balanceType = "LEDGER")
        val result = BalanceObservationApplier.applyIfNewer(account, obs)
        assertNull(result)
    }

    @Test
    fun accountWithNoExistingBaseline_acceptsFirstResolvedObservation() {
        val freshAccount = AccountEntity(id = "acc3", name = "Fresh", openingBalancePaise = 0L)
        val obs = observation(accountId = "acc3", balancePaise = 123456L, timestamp = 1L)
        val result = BalanceObservationApplier.applyIfNewer(freshAccount, obs)
        assertEquals(123456L, result?.balanceBaselinePaise)
    }
}
