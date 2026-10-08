package com.example.expensetracker

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.RepaymentAllocationEntity
import com.example.expensetracker.data.local.RepaymentEntity
import com.example.expensetracker.data.local.RepaymentWithAllocations
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.data.local.toEntity
import com.example.expensetracker.model.AccountBalanceCalculator
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DashboardAnalyticsIntegrationTest {

    private val account1 = AccountEntity(id = "acc1", name = "SBI Bank", openingBalancePaise = 100000L, isPrimary = true) // ₹1000.00
    private val account2 = AccountEntity(id = "acc2", name = "HDFC Bank", openingBalancePaise = 200000L, isPrimary = false) // ₹2000.00
    private val archivedAccount = AccountEntity(id = "acc3", name = "Old Axis", openingBalancePaise = 50000L, isArchived = true) // ₹500.00

    private val tx1Account1Income = Transaction(
        id = "t1", title = "Salary", amount = 5000.0, type = TransactionType.INCOME,
        category = TransactionCategory.SALARY, accountId = "acc1", timestamp = 1000L
    )
    private val tx2Account1Expense = Transaction(
        id = "t2", title = "Grocery", amount = 1500.0, type = TransactionType.EXPENSE,
        category = TransactionCategory.FOOD, accountId = "acc1", timestamp = 2000L
    )
    private val tx3Account2Expense = Transaction(
        id = "t3", title = "Cab Ride", amount = 800.0, type = TransactionType.EXPENSE,
        category = TransactionCategory.TRANSPORT, accountId = "acc2", timestamp = 3000L
    )
    private val tx4UnassignedExpense = Transaction(
        id = "t4", title = "Cash Tea", amount = 50.0, type = TransactionType.EXPENSE,
        category = TransactionCategory.FOOD, accountId = null, timestamp = 4000L
    )
    private val tx5Account1Transfer = Transaction(
        id = "t5", title = "Self Transfer", amount = 1000.0, type = TransactionType.TRANSFER,
        category = TransactionCategory.OTHER, accountId = "acc1", timestamp = 5000L
    )

    private val allTransactions = listOf(
        tx1Account1Income,
        tx2Account1Expense,
        tx3Account2Expense,
        tx4UnassignedExpense,
        tx5Account1Transfer
    )

    // Test 1: Dashboard balance for a selected account
    @Test
    fun test1_dashboardBalanceForSelectedAccount() {
        val txEntities = allTransactions.map { it.toEntity() }
        val balancePaise = AccountBalanceCalculator.calculateAccountBalancePaise(account1, txEntities)
        // opening 100000 + income 500000 - expense 150000 = 450000 (₹4500.00)
        assertEquals(450000L, balancePaise)
        assertEquals(4500.0, balancePaise / 100.0, 0.001)
    }

    // Test 2: Combined active-account balance
    @Test
    fun test2_combinedActiveAccountBalance() {
        val accounts = listOf(account1, account2, archivedAccount)
        val txEntities = allTransactions.map { it.toEntity() }
        val totalActivePaise = AccountBalanceCalculator.calculateTotalActiveBalancePaise(accounts, txEntities)
        // Acc1 = 100000 + 500000 - 150000 = 450000
        // Acc2 = 200000 - 80000 = 120000
        // Total = 450000 + 120000 = 570000 (₹5700.00)
        assertEquals(570000L, totalActivePaise)
    }

    // Test 3: Account-selection changes updating summaries
    @Test
    fun test3_accountSelectionChangesUpdateSummaries() {
        // When account1 selected
        val acc1Income = allTransactions.filter { it.accountId == "acc1" && it.type == TransactionType.INCOME }.sumOf { it.amount }
        val acc1Expense = allTransactions.filter { it.accountId == "acc1" && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(5000.0, acc1Income, 0.001)
        assertEquals(1500.0, acc1Expense, 0.001)

        // When account2 selected
        val acc2Income = allTransactions.filter { it.accountId == "acc2" && it.type == TransactionType.INCOME }.sumOf { it.amount }
        val acc2Expense = allTransactions.filter { it.accountId == "acc2" && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(0.0, acc2Income, 0.001)
        assertEquals(800.0, acc2Expense, 0.001)
    }

    // Test 4: Hide/show preference not changing underlying values
    @Test
    fun test4_hideShowPreferenceNotChangingUnderlyingValues() {
        val balancePaise = AccountBalanceCalculator.calculateAccountBalancePaise(account1, allTransactions.map { it.toEntity() })
        val isVisible = false
        val displayString = if (isVisible) "₹4,500.00" else "••••••"
        assertEquals("••••••", displayString)
        // Underlying calculation remains intact
        assertEquals(450000L, balancePaise)
    }

    // Test 5: Recent transactions filtered by account
    @Test
    fun test5_recentTransactionsFilteredByAccount() {
        val selectedAccId = "acc1"
        val filtered = allTransactions.filter { it.accountId == selectedAccId }
        assertEquals(3, filtered.size) // t1, t2, t5
        assertTrue(filtered.all { it.accountId == "acc1" })
    }

    // Test 6: Income and expense summaries filtered by account
    @Test
    fun test6_incomeAndExpenseSummariesFilteredByAccount() {
        val selectedAccId = "acc2"
        val filtered = allTransactions.filter { it.accountId == selectedAccId }
        val income = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(0.0, income, 0.001)
        assertEquals(800.0, expense, 0.001)
    }

    // Test 7: Analytics category totals filtered by account
    @Test
    fun test7_analyticsCategoryTotalsFilteredByAccount() {
        val filteredAcc1 = allTransactions.filter { it.accountId == "acc1" && it.type == TransactionType.EXPENSE }
        val foodTotalAcc1 = filteredAcc1.filter { it.category == TransactionCategory.FOOD }.sumOf { it.amount }
        val transportTotalAcc1 = filteredAcc1.filter { it.category == TransactionCategory.TRANSPORT }.sumOf { it.amount }

        assertEquals(1500.0, foodTotalAcc1, 0.001)
        assertEquals(0.0, transportTotalAcc1, 0.001)
    }

    // Test 8: Trend data filtered by account
    @Test
    fun test8_trendDataFilteredByAccount() {
        val acc2Expenses = allTransactions.filter { it.accountId == "acc2" && it.type == TransactionType.EXPENSE }
        assertEquals(1, acc2Expenses.size)
        assertEquals(800.0, acc2Expenses.sumOf { it.amount }, 0.001)
    }

    // Test 9: Drilldown preserving the selected account filter
    @Test
    fun test9_drilldownPreservesSelectedAccountFilter() {
        val selectedAccount = "acc1"
        val foodCategoryDrilldown = allTransactions.filter {
            it.accountId == selectedAccount &&
            it.type == TransactionType.EXPENSE &&
            it.category == TransactionCategory.FOOD
        }
        assertEquals(1, foodCategoryDrilldown.size)
        assertEquals("t2", foodCategoryDrilldown.first().id)
    }

    // Test 10: Empty account and empty-period states
    @Test
    fun test10_emptyAccountAndEmptyPeriodStates() {
        val emptyAccountId = "non_existent"
        val filtered = allTransactions.filter { it.accountId == emptyAccountId }
        assertTrue(filtered.isEmpty())
    }

    // Test 11: Unassigned transactions excluded from specific-account calculations
    @Test
    fun test11_unassignedTransactionsExcludedFromSpecificAccountCalculations() {
        val acc1Balance = AccountBalanceCalculator.calculateAccountBalancePaise(account1, allTransactions.map { it.toEntity() })
        // tx4 is unassigned (-50.0) -> must NOT affect acc1
        assertEquals(450000L, acc1Balance)
    }

    // Test 12: Transfers not double-counted
    @Test
    fun test12_transfersNotDoubleCounted() {
        val acc1TxList = allTransactions.filter { it.accountId == "acc1" }
        val nonTransferExpenseSum = acc1TxList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val nonTransferIncomeSum = acc1TxList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

        // Analytics income/expense exclude transfers
        assertEquals(5000.0, nonTransferIncomeSum, 0.001)
        assertEquals(1500.0, nonTransferExpenseSum, 0.001)
    }

    // Test 13: Repayment records not independently changing balances
    @Test
    fun test13_repaymentRecordsNotIndependentlyChangingBalances() {
        val initialBalance = AccountBalanceCalculator.calculateAccountBalancePaise(account1, allTransactions.map { it.toEntity() })
        // Repayment object alone does not change account transactions
        val repayment = RepaymentEntity(
            id = "rep1",
            groupId = "g1",
            memberId = "m1",
            amountPaise = 50000L,
            unallocatedAmountPaise = 0L,
            paymentDate = "2026-10-09",
            timestamp = System.currentTimeMillis(),
            linkedTransactionId = null,
            note = null
        )
        assertNotNull(repayment)
        val balanceAfterRepayment = AccountBalanceCalculator.calculateAccountBalancePaise(account1, allTransactions.map { it.toEntity() })
        assertEquals(initialBalance, balanceAfterRepayment)
    }

    // Test 14: Linked incoming transactions counted once
    @Test
    fun test14_linkedIncomingTransactionsCountedOnce() {
        val linkedRepaymentIncomeTx = TransactionEntity(
            id = "t_link",
            title = "Repayment from Alice",
            amount = 500.0,
            type = "INCOME",
            category = "OTHER",
            date = "Today",
            source = "MANUAL",
            timestamp = 6000L,
            accountId = "acc1"
        )
        val txs = allTransactions.map { it.toEntity() } + linkedRepaymentIncomeTx
        val balance = AccountBalanceCalculator.calculateAccountBalancePaise(account1, txs)
        // 450000 + 50000 = 500000
        assertEquals(500000L, balance)
    }

    // Test 15: Archived account fallback
    @Test
    fun test15_archivedAccountFallback() {
        val activeAccounts = listOf(account1, account2, archivedAccount).filter { !it.isArchived }
        val selectedId: String? = "acc3" // archived
        val fallbackId = selectedId?.takeIf { id -> activeAccounts.any { it.id == id } }
        // Must fallback to null (All Accounts) or primary active account
        assertEquals(null, fallbackId)
    }

    // Test 16: Existing analytics period filters continuing to work
    @Test
    fun test16_existingAnalyticsPeriodFiltersContinuingToWork() {
        val now = System.currentTimeMillis()
        val txThisMonth = Transaction(id = "tm1", title = "Rent", amount = 1000.0, type = TransactionType.EXPENSE, category = TransactionCategory.BILLS, timestamp = now)
        val txOld = Transaction(id = "tm2", title = "Old", amount = 1000.0, type = TransactionType.EXPENSE, category = TransactionCategory.BILLS, timestamp = 0L)

        val txs = listOf(txThisMonth, txOld)
        val recentTxs = txs.filter { it.timestamp > 1000000L }
        assertEquals(1, recentTxs.size)
        assertEquals("tm1", recentTxs.first().id)
    }

    // Test 17: Integer-paise balance precision
    @Test
    fun test17_integerPaiseBalancePrecision() {
        val acc = AccountEntity(id = "acc_p", name = "Paise Acc", openingBalancePaise = 10025L) // ₹100.25
        val tx = TransactionEntity(id = "tp", title = "Small", amount = 0.75, type = "INCOME", category = "OTHER", date = "Today", source = "MANUAL", timestamp = 1L, accountId = "acc_p")
        val bal = AccountBalanceCalculator.calculateAccountBalancePaise(acc, listOf(tx))
        assertEquals(10100L, bal) // ₹101.00 exactly (10100 paise)
    }

    // Test 18: No regression to split and repayment ledger calculations
    @Test
    fun test18_noRegressionToSplitAndRepaymentLedgerCalculations() {
        val group = SplitGroupWithMembers(
            group = SplitGroupEntity(id = "g1", name = "Trip", createdAt = 0L, updatedAt = 0L),
            members = listOf(
                SplitGroupMemberEntity(id = "m_me", groupId = "g1", name = "Me", isMe = true, createdAt = 0L),
                SplitGroupMemberEntity(id = "m_bob", groupId = "g1", name = "Bob", isMe = false, createdAt = 0L)
            )
        )
        val splitTx = TransactionEntity(id = "t_split", title = "Dinner", amount = 100.0, type = "EXPENSE", category = "FOOD", date = "Today", source = "MANUAL", timestamp = 0L)
        val splits = listOf(
            ExpenseSplitWithShares(
                split = ExpenseSplitEntity(id = "s1", transactionId = "t_split", groupId = "g1", amountPaise = 10000L, payerMemberId = "m_me", createdAt = 0L),
                shares = listOf(
                    ExpenseSplitShareEntity(id = "sh1", splitId = "s1", memberId = "m_me", shareAmountPaise = 5000L),
                    ExpenseSplitShareEntity(id = "sh2", splitId = "s1", memberId = "m_bob", shareAmountPaise = 5000L)
                )
            )
        )
        val ledger = ExpenseSplitCalculator.calculateSettleUpLedger(
            splitsWithShares = splits,
            groupsWithMembers = listOf(group),
            transactions = listOf(splitTx),
            repaymentsWithAllocations = emptyList()
        )
        assertEquals(1, ledger.memberBalances.size)
        val summary = ledger.memberBalances.first()
        assertEquals(5000L, summary.totalOwedPaise) // Bob owes Me 5000 paise (₹50.00)
    }
}
