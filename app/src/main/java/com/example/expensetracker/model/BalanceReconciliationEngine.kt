package com.example.expensetracker.model

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.data.local.TransactionEntity

data class ReconciliationResult(
    val account: AccountEntity,
    val latestObservation: BalanceObservationEntity?,
    val expectedBalancePaise: Long,
    val observedBalancePaise: Long?,
    val discrepancyPaise: Long?,
    val isReconciled: Boolean,
    val statusMessage: String
) {
    val discrepancyDouble: Double? get() = discrepancyPaise?.let { it / 100.0 }
    val expectedBalanceDouble: Double get() = expectedBalancePaise / 100.0
    val observedBalanceDouble: Double? get() = observedBalancePaise?.let { it / 100.0 }
}

object BalanceReconciliationEngine {

    /**
     * Performs safe balance reconciliation for an account against its recorded balance observations.
     *
     * Rules:
     * 1. Compares only observations for the exact account, matching currency, and compatible balance type.
     * 2. Resolves out-of-order/delayed messages by using the latest observation strictly by observation timestamp.
     * 3. Calculates expected balance from transactions occurring on or before the observation timestamp.
     * 4. NEVER creates synthetic/phantom transactions based on a discrepancy.
     */
    fun reconcileAccount(
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        observations: List<BalanceObservationEntity>,
        targetCurrency: String = "INR",
        compatibleBalanceType: String = "AVAILABLE"
    ): ReconciliationResult {
        // Filter strictly by account, resolved status, currency, and balance type
        val eligibleObservations = observations.filter { obs ->
            obs.accountId == account.id &&
                    obs.isResolved &&
                    obs.currency.equals(targetCurrency, ignoreCase = true) &&
                    obs.balanceType.equals(compatibleBalanceType, ignoreCase = true)
        }

        if (eligibleObservations.isEmpty()) {
            val currentCalculated = AccountBalanceCalculator.calculateAccountBalancePaise(account, transactions)
            return ReconciliationResult(
                account = account,
                latestObservation = null,
                expectedBalancePaise = currentCalculated,
                observedBalancePaise = null,
                discrepancyPaise = null,
                isReconciled = true,
                statusMessage = "No balance observations recorded for this account"
            )
        }

        // Out-of-order handling: the latest observation by bank timestamp always wins
        val latestObs = eligibleObservations.maxByOrNull { it.timestamp }!!

        // Expected balance up to the observation timestamp
        val transactionsUpToObs = transactions.filter { tx ->
            tx.accountId == account.id && tx.timestamp <= latestObs.timestamp
        }
        val expectedBalancePaise = AccountBalanceCalculator.calculateAccountBalancePaise(account, transactionsUpToObs)
        val discrepancyPaise = latestObs.balancePaise - expectedBalancePaise
        val isReconciled = discrepancyPaise == 0L

        val statusMessage = if (isReconciled) {
            "Reconciled: Observed balance matches calculated balance"
        } else {
            val diffFormatted = String.format("%.2f", Math.abs(discrepancyPaise) / 100.0)
            if (discrepancyPaise > 0) {
                "Discrepancy: Observed balance is ₹$diffFormatted higher than recorded transactions"
            } else {
                "Discrepancy: Observed balance is ₹$diffFormatted lower than recorded transactions"
            }
        }

        return ReconciliationResult(
            account = account,
            latestObservation = latestObs,
            expectedBalancePaise = expectedBalancePaise,
            observedBalancePaise = latestObs.balancePaise,
            discrepancyPaise = discrepancyPaise,
            isReconciled = isReconciled,
            statusMessage = statusMessage
        )
    }
}
