package com.example.expensetracker

import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.TransactionNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitOpeningDebtAndNotificationTest {
    @Test
    fun openingDebtAppearsInMemberBalanceWithoutCreatingSplitExpense() {
        val me = SplitGroupMemberEntity("me", "g", "Me", isMe = true)
        val member = SplitGroupMemberEntity("member", "g", "Arun", openingDebtPaise = 12500L)
        val group = SplitGroupEntity("g", "Trip")

        val balances = ExpenseSplitCalculator.calculateOutstandingBalances(
            splitsWithShares = emptyList<ExpenseSplitWithShares>(),
            members = listOf(me, member)
        )

        assertEquals(12500L, balances.single().amountOwedPaise)
    }

    @Test
    fun notificationSeparatesTransactionAmountAndAvailableBalance() {
        val message = TransactionNotificationHelper.buildNotificationMessage(
            type = TransactionType.EXPENSE,
            amount = 500.0,
            name = "Hari Agency",
            source = "BANK_EMAIL",
            balance = 21500.0
        )
        assertTrue(message.contains("₹500"))
        assertTrue(message.contains("Available balance: ₹21,500"))
        assertTrue(
            TransactionNotificationHelper.buildNotificationMessage(
                TransactionType.EXPENSE, 500.0, "Hari Agency", "BANK_EMAIL", null
            ).contains("Available balance").not()
        )
    }
}