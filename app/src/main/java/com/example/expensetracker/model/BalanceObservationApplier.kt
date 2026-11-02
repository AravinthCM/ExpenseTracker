package com.example.expensetracker.model

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity

/**
 * Applies a trustworthy captured bank balance observation to an account's displayed
 * Current balance (the balance baseline). This is the only place that is allowed to
 * move an account's baseline from a bank observation; it never touches transactions.
 */
object BalanceObservationApplier {

    /**
     * Returns an updated [AccountEntity] with its balance baseline moved to [observation]'s
     * balance, or null if the observation must NOT be applied (stale, unresolved, mismatched
     * account/currency/type, or not newer than what's already captured).
     */
    fun applyIfNewer(
        account: AccountEntity,
        observation: BalanceObservationEntity,
        targetCurrency: String = "INR",
        compatibleBalanceType: String = "AVAILABLE"
    ): AccountEntity? {
        if (!observation.isResolved) return null
        if (observation.accountId != account.id) return null
        if (!observation.currency.equals(targetCurrency, ignoreCase = true)) return null
        if (!observation.balanceType.equals(compatibleBalanceType, ignoreCase = true)) return null

        val existingBaselineTimestamp = account.balanceBaselineTimestamp
        if (existingBaselineTimestamp != null && observation.timestamp <= existingBaselineTimestamp) {
            // A delayed/out-of-order observation can never overwrite a newer captured balance.
            return null
        }

        return account.copy(
            balanceBaselinePaise = observation.balancePaise,
            balanceBaselineTimestamp = observation.timestamp,
            updatedAt = System.currentTimeMillis()
        )
    }
}
