package com.example.expensetracker.model

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.TransactionEntity
import kotlin.math.roundToLong

data class AccountWithBalance(
    val account: AccountEntity,
    val currentBalancePaise: Long
) {
    val currentBalanceDouble: Double get() = currentBalancePaise / 100.0
}

object AccountBalanceCalculator {

    fun calculateAccountBalancePaise(
        account: AccountEntity,
        transactions: List<TransactionEntity>
    ): Long {
        var balance = account.balanceBaselinePaise ?: account.openingBalancePaise
        val baselineTimestamp = account.balanceBaselineTimestamp
        val assigned = transactions.filter {
            it.accountId == account.id && (baselineTimestamp == null || it.timestamp > baselineTimestamp)
        }
        for (tx in assigned) {
            val amountPaise = (tx.amount * 100.0).roundToLong()
            when (tx.type) {
                "INCOME" -> balance += amountPaise
                "EXPENSE" -> if (tx.balanceIncludedInAccount != true) balance -= amountPaise
                else -> { /* TRANSFER or unknown: ignored to prevent double counting */ }
            }
        }
        return balance
    }

    fun calculateTotalActiveBalancePaise(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>
    ): Long {
        return accounts
            .filter { !it.isArchived }
            .sumOf { calculateAccountBalancePaise(it, transactions) }
    }

    /**
     * The value the Edit Account dialog's balance field should be prefilled with: the account's
     * actual displayed Current balance (baseline + ledger since baseline), never the legacy
     * opening balance, so editing never silently appears to reset the account to ₹0.
     */
    fun resolveEditPrefillBalancePaise(
        account: AccountEntity?,
        knownCurrentBalancePaise: Long?
    ): Long {
        if (account == null) return 0L
        return knownCurrentBalancePaise ?: account.balanceBaselinePaise ?: account.openingBalancePaise
    }
}
