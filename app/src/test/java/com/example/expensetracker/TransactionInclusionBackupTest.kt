package com.example.expensetracker

import com.example.expensetracker.data.backup.BackupJsonSerializer
import com.example.expensetracker.data.backup.BackupTransaction
import com.example.expensetracker.data.backup.BACKUP_FORMAT_IDENTIFIER
import com.example.expensetracker.data.backup.BackupDataPayload
import com.example.expensetracker.data.backup.BackupMetadata
import com.example.expensetracker.data.backup.ExpenseTrackerBackup
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionInclusionBackupTest {
    @Test
    fun inclusionStateRoundTripsAndMissingFieldRemainsUnknown() {
        val payload = BackupDataPayload(
            accounts = emptyList(), categories = emptyList(), categoryMappings = emptyList(),
            transactions = listOf(
                BackupTransaction("included", "Included", 10.0, 1000L, "EXPENSE", "Food", "2026-01-01", "MANUAL", 1L, balanceIncludedInAccount = true),
                BackupTransaction("unknown", "Legacy", 20.0, 2000L, "EXPENSE", "Food", "2026-01-01", "MANUAL", 2L)
            ),
            splitGroups = emptyList(), splitGroupMembers = emptyList(), expenseSplits = emptyList(),
            expenseSplitShares = emptyList(), repayments = emptyList(), repaymentAllocations = emptyList(),
            recurringExpenses = emptyList(), paymentTemplates = emptyList(), balanceObservations = emptyList(),
            sourceAccountMappings = emptyList()
        )
        val backup = ExpenseTrackerBackup(
            format = BACKUP_FORMAT_IDENTIFIER,
            schemaVersion = 1,
            exportedAt = "2026-01-01T00:00:00Z",
            appVersion = "1.0",
            databaseVersion = 13,
            data = payload,
            metadata = BackupMetadata(2, 0, 0, 0, 0, emptyMap())
        )

        val roundTrip = BackupJsonSerializer.deserialize(BackupJsonSerializer.serialize(backup))
        assertEquals(true, roundTrip.data.transactions[0].balanceIncludedInAccount)
        assertEquals(null, roundTrip.data.transactions[1].balanceIncludedInAccount)
    }
}