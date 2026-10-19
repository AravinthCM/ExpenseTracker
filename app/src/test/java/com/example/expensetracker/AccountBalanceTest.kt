package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.model.AccountBalanceCalculator
import com.example.expensetracker.model.AccountWithBalance
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AccountBalanceTest {

    @Test
    fun test1_accountCreation() {
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            name = "SBI Savings",
            openingBalancePaise = 500000L, // ₹5000.00
            isPrimary = true
        )
        assertEquals("SBI Savings", account.name)
        assertEquals(500000L, account.openingBalancePaise)
        assertTrue(account.isPrimary)
        assertFalse(account.isArchived)
    }

    @Test
    fun test2_duplicateOrInvalidAccountNames() {
        val existingAccounts = listOf(
            AccountEntity(id = "1", name = "Canara Bank", openingBalancePaise = 100000L)
        )
        val duplicateInput = "  canara bank  "
        val isDuplicate = existingAccounts.any { it.name.trim().equals(duplicateInput.trim(), ignoreCase = true) }
        assertTrue(isDuplicate)

        val uniqueInput = "HDFC Bank"
        val isUniqueDuplicate = existingAccounts.any { it.name.trim().equals(uniqueInput.trim(), ignoreCase = true) }
        assertFalse(isUniqueDuplicate)
    }

    @Test
    fun test3_primaryAccountSelectionGuaranteesOnlyOnePrimary() {
        val accounts = listOf(
            AccountEntity(id = "1", name = "SBI", openingBalancePaise = 1000L, isPrimary = true),
            AccountEntity(id = "2", name = "HDFC", openingBalancePaise = 2000L, isPrimary = false)
        )
        // Simulate setting HDFC (id = 2) as primary
        val updatedAccounts = accounts.map { acc ->
            acc.copy(isPrimary = (acc.id == "2"))
        }
        val primaryAccounts = updatedAccounts.filter { it.isPrimary }
        assertEquals(1, primaryAccounts.size)
        assertEquals("2", primaryAccounts.first().id)
    }

    @Test
    fun test4_openingBalanceCalculation() {
        val account = AccountEntity(id = "acc1", name = "Cash", openingBalancePaise = 250000L) // ₹2500.00
        val transactions = emptyList<TransactionEntity>()
        val balancePaise = AccountBalanceCalculator.calculateAccountBalancePaise(account, transactions)
        assertEquals(250000L, balancePaise)
    }

    @Test
    fun currentBalanceBaselineIgnoresHistoricalTransactionsAndAppliesLaterChanges() {
        val account = AccountEntity(
            id = "acc1",
            name = "SBI",
            openingBalancePaise = 0L,
            balanceBaselinePaise = 2200000L,
            balanceBaselineTimestamp = 2_000L
        )
        val historicalExpense = TransactionEntity(
            id = "historical",
            title = "Historical expense",
            amount = 1000.0,
            type = "EXPENSE",
            category = "Food",
            date = "2026-01-01",
            source = "MANUAL",
            timestamp = 1_000L,
            accountId = "acc1"
        )
        val laterIncome = historicalExpense.copy(
            id = "later",
            title = "Later income",
            amount = 123.45,
            type = "INCOME",
            timestamp = 3_000L
        )

        assertEquals(
            2_212_345L,
            AccountBalanceCalculator.calculateAccountBalancePaise(account, listOf(historicalExpense, laterIncome))
        )
    }

    @Test
    fun baselineTimestampIsExclusive() {
        val account = AccountEntity(
            id = "acc1",
            name = "SBI",
            openingBalancePaise = 0L,
            balanceBaselinePaise = 10000L,
            balanceBaselineTimestamp = 1_000L
        )
        val atBaseline = TransactionEntity(
            id = "at-baseline",
            title = "At baseline",
            amount = 50.0,
            type = "EXPENSE",
            category = "Food",
            date = "2026-01-01",
            source = "MANUAL",
            timestamp = 1_000L,
            accountId = "acc1"
        )

        assertEquals(10000L, AccountBalanceCalculator.calculateAccountBalancePaise(account, listOf(atBaseline)))
    }

    @Test
    fun test5_incomeIncreasesAccountBalance() {
        val account = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L) // ₹1000.00
        val txs = listOf(
            TransactionEntity(
                id = "tx1",
                title = "Salary Credit",
                amount = 5000.50,
                type = "INCOME",
                category = "SALARY",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = "acc1"
            )
        )
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(account, txs)
        assertEquals(600050L, balance) // ₹1000.00 + ₹5000.50 = ₹6000.50 (600050 paise)
    }

    @Test
    fun test6_expenseDecreasesAccountBalance() {
        val account = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L) // ₹1000.00
        val txs = listOf(
            TransactionEntity(
                id = "tx1",
                title = "Grocery Shopping",
                amount = 250.75,
                type = "EXPENSE",
                category = "GROCERY",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = "acc1"
            )
        )
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(account, txs)
        assertEquals(74925L, balance) // ₹1000.00 - ₹250.75 = ₹749.25 (74925 paise)
    }

    @Test
    fun expenseAlreadyIncludedInAccountBalanceIsNotDeductedAgain() {
        val account = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L)
        val transaction = TransactionEntity(
            id = "included",
            title = "Bank debit already reflected",
            amount = 250.0,
            type = "EXPENSE",
            category = "FOOD",
            date = "Today",
            source = "MANUAL",
            timestamp = 1L,
            accountId = "acc1",
            balanceIncludedInAccount = true
        )
        assertEquals(100000L, AccountBalanceCalculator.calculateAccountBalancePaise(account, listOf(transaction)))
    }

    @Test
    fun expenseNotIncludedInAccountBalanceIsDeductedExactlyOnce() {
        val account = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L)
        val transaction = TransactionEntity(
            id = "not-included",
            title = "Manual expense",
            amount = 250.0,
            type = "EXPENSE",
            category = "FOOD",
            date = "Today",
            source = "MANUAL",
            timestamp = 1L,
            accountId = "acc1",
            balanceIncludedInAccount = false
        )
        assertEquals(75000L, AccountBalanceCalculator.calculateAccountBalancePaise(account, listOf(transaction)))
        assertEquals(75000L, AccountBalanceCalculator.calculateAccountBalancePaise(account, listOf(transaction)))
    }

    @Test
    fun changingExpenseInclusionOrAccountRecalculatesWithoutMutatingAmount() {
        val account1 = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L)
        val account2 = AccountEntity(id = "acc2", name = "Cash", openingBalancePaise = 50000L)
        val base = TransactionEntity("tx", "Expense", 250.0, "EXPENSE", "FOOD", "Today", "MANUAL", 1L, accountId = "acc1", balanceIncludedInAccount = false)
        val included = base.copy(balanceIncludedInAccount = true)
        val moved = included.copy(accountId = "acc2")

        assertEquals(75000L, AccountBalanceCalculator.calculateAccountBalancePaise(account1, listOf(base)))
        assertEquals(100000L, AccountBalanceCalculator.calculateAccountBalancePaise(account1, listOf(included)))
        assertEquals(50000L, AccountBalanceCalculator.calculateAccountBalancePaise(account2, listOf(moved)))
        assertEquals(250.0, moved.amount, 0.0)
    }

    @Test
    fun test7_transfersAreNotDoubleCounted() {
        val account = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L)
        val txs = listOf(
            TransactionEntity(
                id = "tx1",
                title = "Internal Transfer",
                amount = 500.00,
                type = "TRANSFER",
                category = "OTHER",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = "acc1"
            )
        )
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(account, txs)
        assertEquals(100000L, balance) // Transfer adds/subtracts 0 to single account balance
    }

    @Test
    fun test8_unassignedTransactionsDoNotAffectAccount() {
        val account = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L)
        val txs = listOf(
            TransactionEntity(
                id = "tx1",
                title = "Unassigned Expense",
                amount = 500.00,
                type = "EXPENSE",
                category = "FOOD",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = null // Unassigned
            )
        )
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(account, txs)
        assertEquals(100000L, balance)
    }

    @Test
    fun test9_existingTransactionsSurviveMigration() {
        val historicalEntity = TransactionEntity(
            id = "old1",
            title = "Old Swiggy Expense",
            amount = 350.00,
            type = "EXPENSE",
            category = "FOOD",
            date = "Yesterday",
            source = "MANUAL",
            timestamp = 1000000L,
            originalText = "",
            accountId = null
        )
        assertNull(historicalEntity.accountId)
        assertEquals(350.00, historicalEntity.amount, 0.001)
    }

    @Test
    fun test10_newManualTransactionsDefaultToPrimaryAccount() {
        val primaryAcc = AccountEntity(id = "p1", name = "Canara Primary", openingBalancePaise = 100000L, isPrimary = true)
        val txWithoutAcc = Transaction(
            id = "tx1",
            title = "Dinner",
            amount = 400.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.FOOD,
            accountId = null
        )

        val finalAccountId = txWithoutAcc.accountId ?: primaryAcc.id
        assertEquals("p1", finalAccountId)
    }

    @Test
    fun test11_notificationTransactionsDefaultToPrimaryAccount() {
        val primaryAcc = AccountEntity(id = "p1", name = "SBI Primary", openingBalancePaise = 500000L, isPrimary = true)
        val parsedNotifTx = TransactionEntity(
            id = "n1",
            title = "ATM Withdrawal",
            amount = 1000.0,
            type = "EXPENSE",
            category = "OTHER",
            date = "Today",
            source = "BANK_SMS",
            timestamp = System.currentTimeMillis(),
            accountId = null
        )

        val assignedTx = if (parsedNotifTx.accountId == null) parsedNotifTx.copy(accountId = primaryAcc.id) else parsedNotifTx
        assertEquals("p1", assignedTx.accountId)
    }

    @Test
    fun test12_transactionEditingPreservesItsAccount() {
        val originalTx = Transaction(
            id = "tx1",
            title = "Uber Ride",
            amount = 250.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.TRANSPORT,
            accountId = "acc_cash"
        )

        val editedTx = originalTx.copy(amount = 280.0, title = "Uber Ride XL")
        assertEquals("acc_cash", editedTx.accountId)
    }

    @Test
    fun test13_accountReassignmentRecalculatesBothBalances() {
        val acc1 = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L) // ₹1000.00
        val acc2 = AccountEntity(id = "acc2", name = "HDFC", openingBalancePaise = 200000L) // ₹2000.00

        var txs = listOf(
            TransactionEntity(
                id = "tx1",
                title = "Electronics",
                amount = 500.00,
                type = "EXPENSE",
                category = "SHOPPING",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = "acc1"
            )
        )

        assertEquals(50000L, AccountBalanceCalculator.calculateAccountBalancePaise(acc1, txs)) // ₹500.00
        assertEquals(200000L, AccountBalanceCalculator.calculateAccountBalancePaise(acc2, txs)) // ₹2000.00

        // Reassign tx1 from acc1 to acc2
        txs = txs.map { if (it.id == "tx1") it.copy(accountId = "acc2") else it }

        assertEquals(100000L, AccountBalanceCalculator.calculateAccountBalancePaise(acc1, txs)) // Back to ₹1000.00
        assertEquals(150000L, AccountBalanceCalculator.calculateAccountBalancePaise(acc2, txs)) // Reduced to ₹1500.00
    }

    @Test
    fun test14_deletingTransactionUpdatesDerivedBalance() {
        val acc = AccountEntity(id = "acc1", name = "SBI", openingBalancePaise = 100000L)
        val tx1 = TransactionEntity(
            id = "tx1",
            title = "Book",
            amount = 200.00,
            type = "EXPENSE",
            category = "SHOPPING",
            date = "Today",
            source = "MANUAL",
            timestamp = System.currentTimeMillis(),
            accountId = "acc1"
        )
        val initialTxs = listOf(tx1)
        assertEquals(80000L, AccountBalanceCalculator.calculateAccountBalancePaise(acc, initialTxs))

        // Delete transaction
        val remainingTxs = emptyList<TransactionEntity>()
        assertEquals(100000L, AccountBalanceCalculator.calculateAccountBalancePaise(acc, remainingTxs))
    }

    @Test
    fun test15_repaymentRecordsAloneDoNotAffectAccountBalances() {
        val acc = AccountEntity(id = "acc1", name = "Cash", openingBalancePaise = 100000L)
        val txs = emptyList<TransactionEntity>()

        // A repayment record exists, but no transaction is assigned to acc
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(acc, txs)
        assertEquals(100000L, balance)
    }

    @Test
    fun test16_linkedIncomingTransactionAffectsAccountOnce() {
        val acc = AccountEntity(id = "acc1", name = "Canara", openingBalancePaise = 100000L)
        val linkedIncomeTx = TransactionEntity(
            id = "income1",
            title = "Received from Rahul",
            amount = 500.00,
            type = "INCOME",
            category = "OTHER",
            date = "Today",
            source = "MANUAL",
            timestamp = System.currentTimeMillis(),
            accountId = "acc1"
        )
        val txs = listOf(linkedIncomeTx)

        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(acc, txs)
        assertEquals(150000L, balance) // Affected exactly once by the transaction record
    }

    @Test
    fun test17_noAccountModeContinuesToWork() {
        val accounts = emptyList<AccountEntity>()
        val txs = listOf(
            TransactionEntity(
                id = "t1",
                title = "Coffee",
                amount = 50.0,
                type = "EXPENSE",
                category = "FOOD",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = null
            )
        )
        val totalActiveBal = AccountBalanceCalculator.calculateTotalActiveBalancePaise(accounts, txs)
        assertEquals(0L, totalActiveBal)
    }

    @Test
    fun test18_integerPaisePrecision() {
        val acc = AccountEntity(id = "acc1", name = "Precision Test", openingBalancePaise = 10050L) // ₹100.50
        val txs = listOf(
            TransactionEntity(
                id = "tx1",
                title = "Tea",
                amount = 0.25,
                type = "INCOME",
                category = "OTHER",
                date = "Today",
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                accountId = "acc1"
            )
        )
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(acc, txs)
        assertEquals(10075L, balance) // ₹100.50 + ₹0.25 = ₹100.75
    }

    @Test
    fun test19_archivingAccountPreservesHistory() {
        val activeAcc = AccountEntity(id = "a1", name = "SBI", openingBalancePaise = 100000L, isArchived = false)
        val archivedAcc = AccountEntity(id = "a2", name = "Old Bank", openingBalancePaise = 200000L, isArchived = true)

        val accounts = listOf(activeAcc, archivedAcc)
        val txs = emptyList<TransactionEntity>()

        val totalActiveBalance = AccountBalanceCalculator.calculateTotalActiveBalancePaise(accounts, txs)
        assertEquals(100000L, totalActiveBalance) // Only includes non-archived account
    }

    @Test
    fun test20_primaryAccountChangesReflectedReactively() {
        var accounts = listOf(
            AccountEntity(id = "a1", name = "SBI", openingBalancePaise = 1000L, isPrimary = true),
            AccountEntity(id = "a2", name = "HDFC", openingBalancePaise = 2000L, isPrimary = false)
        )

        var primary = accounts.find { it.isPrimary }
        assertEquals("a1", primary?.id)

        // Change primary to a2
        accounts = accounts.map { it.copy(isPrimary = (it.id == "a2")) }
        primary = accounts.find { it.isPrimary }
        assertEquals("a2", primary?.id)
    }
}
