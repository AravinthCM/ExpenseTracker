package com.example.expensetracker.data.backup

import com.example.expensetracker.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BackupImportTest {

    private lateinit var db: TestDatabaseHarness
    private lateinit var importer: BackupImporter
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        db = TestDatabaseHarness()
        importer = BackupImporter(db)
        backupManager = BackupManager(db)
    }

    private fun createSamplePayload(): BackupDataPayload {
        val account1 = AccountEntity(id = "acc-1", name = "Canara Bank", openingBalancePaise = 500000L, isPrimary = true, accountNumber = "123456789528")
        val account2 = AccountEntity(id = "acc-2", name = "Cash", openingBalancePaise = 100000L, isPrimary = false)

        val cat1 = CategoryEntity(id = "cat-1", name = "Food")
        val cat2 = CategoryEntity(id = "cat-2", name = "Fuel")
        val catMapping = CategoryMappingEntity(id = "map-1", pattern = "swiggy", categoryName = "Food", priority = 1)

        val tx1 = BackupTransaction(
            id = "tx-1", title = "Lunch Swiggy", amount = 250.50, amountPaise = 25050L,
            type = "EXPENSE", category = "Food", date = "2026-10-18", source = "NOTIFICATION",
            timestamp = 1729240000000L, accountId = "acc-1", referenceNumber = "REF123456"
        )
        val tx2 = BackupTransaction(
            id = "tx-2", title = "Salary", amount = 50000.0, amountPaise = 5000000L,
            type = "INCOME", category = "Income", date = "2026-10-01", source = "MANUAL",
            timestamp = 1729140000000L, accountId = "acc-1"
        )
        val tx3 = BackupTransaction(
            id = "tx-3", title = "ATM Withdrawal", amount = 2000.0, amountPaise = 200000L,
            type = "TRANSFER", category = "Transfer", date = "2026-10-05", source = "MANUAL",
            timestamp = 1729180000000L, accountId = "acc-1"
        )

        val group = SplitGroupEntity(id = "grp-1", name = "Goa Trip", createdAt = 1000L, updatedAt = 1000L)
        val m1 = SplitGroupMemberEntity(id = "mem-1", groupId = "grp-1", name = "Me", isMe = true, createdAt = 1000L)
        val m2 = SplitGroupMemberEntity(id = "mem-2", groupId = "grp-1", name = "Arun", isMe = false, createdAt = 1000L)

        val split = ExpenseSplitEntity(id = "sp-1", transactionId = "tx-1", groupId = "grp-1", amountPaise = 25050L, payerMemberId = "mem-1", createdAt = 1000L)
        val share1 = ExpenseSplitShareEntity(id = "sh-1", splitId = "sp-1", memberId = "mem-1", shareAmountPaise = 12525L)
        val share2 = ExpenseSplitShareEntity(id = "sh-2", splitId = "sp-1", memberId = "mem-2", shareAmountPaise = 12525L)

        val repayment = RepaymentEntity(id = "rep-1", groupId = "grp-1", memberId = "mem-2", amountPaise = 12525L, unallocatedAmountPaise = 0L, paymentDate = "2026-10-18", timestamp = 2000L)
        val alloc = RepaymentAllocationEntity(id = "alloc-1", repaymentId = "rep-1", shareId = "sh-2", allocatedAmountPaise = 12525L)

        val recurring = RecurringExpenseEntity(id = "rec-1", merchant = "Netflix", amount = 199.0, frequency = "MONTHLY", intervalDays = 30, lastTransactionTimestamp = 1000L, confirmedAt = 1000L)
        val template = PaymentTemplateEntity(id = "tpl-1", name = "Canara UPI", transactionType = "EXPENSE", sampleText = "Paid INR 100", templatePattern = "Paid INR (?<amount>\\d+)", enabled = true, createdAt = 1000L, updatedAt = 1000L)
        val obs = BalanceObservationEntity(id = "obs-1", accountId = "acc-1", accountSuffix = "528", balancePaise = 67508L, balanceType = "AVAILABLE", currency = "INR", timestamp = 1000L, source = "GMAIL", rawText = "Balance INR 675.08", isResolved = true, isReconciled = false, createdAt = 1000L)
        val sam = SourceAccountMappingEntity(id = "sam-1", accountId = "acc-1", sourcePattern = "CANARA_ALERT", senderOrPackage = "com.google.android.gm", matchingContext = "528", isConfirmed = true, createdAt = 1000L, updatedAt = 1000L)

        return BackupDataPayload(
            accounts = listOf(account1, account2),
            categories = listOf(cat1, cat2),
            categoryMappings = listOf(catMapping),
            transactions = listOf(tx1, tx2, tx3),
            splitGroups = listOf(group),
            splitGroupMembers = listOf(m1, m2),
            expenseSplits = listOf(split),
            expenseSplitShares = listOf(share1, share2),
            repayments = listOf(repayment),
            repaymentAllocations = listOf(alloc),
            recurringExpenses = listOf(recurring),
            paymentTemplates = listOf(template),
            balanceObservations = listOf(obs),
            sourceAccountMappings = listOf(sam)
        )
    }

    private fun createSampleBackup(): ExpenseTrackerBackup {
        val payload = createSamplePayload()
        return ExpenseTrackerBackup(
            format = BACKUP_FORMAT_IDENTIFIER,
            schemaVersion = 1,
            exportedAt = "2026-10-18T10:00:00Z",
            appVersion = "1.0",
            databaseVersion = 12,
            data = payload,
            metadata = BackupMetadata(
                totalTransactions = payload.transactions.size,
                totalAccounts = payload.accounts.size,
                totalCategories = payload.categories.size,
                totalSplitGroups = payload.splitGroups.size,
                totalRepayments = payload.repayments.size,
                recordCounts = mapOf("transactions" to payload.transactions.size)
            )
        )
    }

    // 1. Importing a valid backup exported by the current application
    @Test
    fun test1_importingValidBackup_restoresAllEntitiesAndRelationships() = runBlocking {
        val backup = createSampleBackup()
        val json = BackupJsonSerializer.serialize(backup)
        val deserialized = BackupJsonSerializer.deserialize(json)

        val result = importer.restoreBackup(deserialized).getOrThrow()
        assertEquals(ImportMode.RESTORE_REPLACE, result.mode)
        assertEquals(2, result.accountsImported)
        assertEquals(3, result.transactionsImported)
        assertEquals(2, result.categoriesImported)
        assertEquals(1, result.splitGroupsImported)
        assertEquals(1, result.repaymentsImported)

        // Verify foreign key linkages in restored DB
        val tx = db.transactionDao().getTransactionByIdSync("tx-1")
        assertNotNull(tx)
        assertEquals("acc-1", tx?.accountId)
        assertEquals("REF123456", tx?.referenceNumber)

        val splitWithShares = db.expenseSplitDao().getAllSplitsWithSharesSync()
        assertEquals(1, splitWithShares.size)
        assertEquals(2, splitWithShares[0].shares.size)
        assertEquals("mem-1", splitWithShares[0].split.payerMemberId)

        val repaymentWithAlloc = db.repaymentDao().getAllRepaymentsWithAllocationsSync()
        assertEquals(1, repaymentWithAlloc.size)
        assertEquals(1, repaymentWithAlloc[0].allocations.size)
        assertEquals(12525L, repaymentWithAlloc[0].allocations[0].allocatedAmountPaise)
    }

    // 2. Empty backups
    @Test
    fun test2_emptyBackups() = runBlocking {
        val emptyPayload = BackupDataPayload(
            accounts = emptyList(), categories = emptyList(), categoryMappings = emptyList(),
            transactions = emptyList(), splitGroups = emptyList(), splitGroupMembers = emptyList(),
            expenseSplits = emptyList(), expenseSplitShares = emptyList(), repayments = emptyList(),
            repaymentAllocations = emptyList(), recurringExpenses = emptyList(),
            paymentTemplates = emptyList(), balanceObservations = emptyList(),
            sourceAccountMappings = emptyList()
        )
        val emptyBackup = ExpenseTrackerBackup(
            exportedAt = "2026-10-18T10:00:00Z", appVersion = "1.0", databaseVersion = 12,
            data = emptyPayload,
            metadata = BackupMetadata(0, 0, 0, 0, 0, emptyMap())
        )

        val json = BackupJsonSerializer.serialize(emptyBackup)
        val deserialized = BackupJsonSerializer.deserialize(json)
        val result = importer.restoreBackup(deserialized).getOrThrow()

        assertEquals(0, result.transactionsImported)
        assertEquals(0, db.transactionDao().getAllTransactionsSync().size)
    }

    // 3. Unsupported schema versions and malformed JSON
    @Test
    fun test3_unsupportedSchemaVersionsAndMalformedJson() {
        // Malformed JSON
        try {
            BackupJsonSerializer.deserialize("{ not valid json: ")
            fail("Should have failed on malformed JSON")
        } catch (e: BackupValidationException) {
            assertTrue(e.message!!.contains("Malformed JSON"))
        }

        // Unsupported schema version
        val futureJson = """
            {
              "format": "$BACKUP_FORMAT_IDENTIFIER",
              "schemaVersion": 999,
              "exportedAt": "2026-10-18T10:00:00Z",
              "appVersion": "9.9",
              "databaseVersion": 99,
              "data": {}
            }
        """.trimIndent()
        try {
            BackupJsonSerializer.deserialize(futureJson)
            fail("Should have failed on schemaVersion 999")
        } catch (e: BackupValidationException) {
            assertTrue(e.message!!.contains("Unsupported schemaVersion 999"))
        }

        // Invalid format
        val invalidFormatJson = """
            {
              "format": "DifferentAppBackup",
              "schemaVersion": 1,
              "exportedAt": "2026-10-18T10:00:00Z",
              "data": {}
            }
        """.trimIndent()
        try {
            BackupJsonSerializer.deserialize(invalidFormatJson)
            fail("Should have failed on invalid format")
        } catch (e: BackupValidationException) {
            assertTrue(e.message!!.contains("Invalid backup format"))
        }
    }

    // 4. Duplicate IDs and missing relationship targets
    @Test
    fun test4_duplicateIdsAndMissingRelationshipTargets() {
        val base = createSampleBackup()
        val data = base.data

        // Duplicate transaction ID within file
        val dupTx = data.transactions[0].copy()
        val payloadWithDup = data.copy(transactions = data.transactions + dupTx)
        val backupWithDup = base.copy(data = payloadWithDup)
        val valResultDup = BackupValidator.validateBackup(backupWithDup)
        assertFalse("Should be invalid due to duplicate transaction ID", valResultDup.isValid)
        assertTrue(valResultDup.errors.any { it.message.contains("Duplicate ID") })

        // Missing relationship target: expense split referencing non-existent group
        val badSplit = data.expenseSplits[0].copy(groupId = "non-existent-group-id")
        val payloadWithBadFk = data.copy(expenseSplits = listOf(badSplit))
        val backupWithBadFk = base.copy(data = payloadWithBadFk)
        val valResultFk = BackupValidator.validateBackup(backupWithBadFk)
        assertFalse("Should be invalid due to missing group FK target", valResultFk.isValid)
        assertTrue(valResultFk.errors.any { it.message.contains("non-existent group ID") })
    }

    // 5. Account, category and transaction references
    @Test
    fun test5_accountCategoryAndTransactionReferences() = runBlocking {
        val backup = createSampleBackup()
        importer.restoreBackup(backup).getOrThrow()

        val tx = db.transactionDao().getTransactionByIdSync("tx-1")!!
        val acc = db.accountDao().getAccountByIdSync(tx.accountId!!)
        assertNotNull("Referenced account must exist", acc)
        assertEquals("Canara Bank", acc?.name)

        val categories = db.categoryDao().getAllCategoriesSync()
        assertTrue("Transaction category must exist in categories", categories.any { it.name == tx.category })
    }

    // 6. Split shares and repayment allocations
    @Test
    fun test6_splitSharesAndRepaymentAllocations() = runBlocking {
        val backup = createSampleBackup()
        importer.restoreBackup(backup).getOrThrow()

        val split = db.expenseSplitDao().getAllSplitsWithSharesSync().first()
        val sumShares = split.shares.sumOf { it.shareAmountPaise }
        assertEquals(split.split.amountPaise, sumShares)
        assertEquals(2, split.shares.size)

        val rep = db.repaymentDao().getAllRepaymentsWithAllocationsSync().first()
        assertEquals(1, rep.allocations.size)
        assertEquals(split.shares[1].id, rep.allocations[0].shareId)
    }

    // 7. Unallocated repayment amounts
    @Test
    fun test7_unallocatedRepaymentAmounts() = runBlocking {
        val base = createSampleBackup()
        // Repayment of ₹500 with ₹350 allocated and ₹150 unallocated (15000 paise)
        val repWithUnallocated = base.data.repayments[0].copy(
            amountPaise = 50000L,
            unallocatedAmountPaise = 15000L
        )
        val alloc = base.data.repaymentAllocations[0].copy(
            allocatedAmountPaise = 35000L
        )
        val payload = base.data.copy(
            repayments = listOf(repWithUnallocated),
            repaymentAllocations = listOf(alloc)
        )
        val backup = base.copy(data = payload)

        importer.restoreBackup(backup).getOrThrow()

        val restored = db.repaymentDao().getAllRepaymentsWithAllocationsSync().first()
        assertEquals(50000L, restored.repayment.amountPaise)
        assertEquals(15000L, restored.repayment.unallocatedAmountPaise)
        assertEquals(35000L, restored.allocations[0].allocatedAmountPaise)
    }

    // 8. Existing database ID conflicts
    @Test
    fun test8_existingDatabaseIdConflicts() = runBlocking {
        // Pre-populate DB with account having ID "acc-1"
        val existingAcc = AccountEntity(id = "acc-1", name = "Existing Canara", openingBalancePaise = 20000L)
        db.accountDao().insertAccount(existingAcc)

        val backup = createSampleBackup()
        val preview = BackupValidator.generatePreviewSummary(backup, db)

        assertTrue("Should detect ID conflict on acc-1", preview.idConflicts.any { it.id == "acc-1" })

        // Merge mode safely handles conflict without crashing or overwriting
        val result = importer.mergeBackup(backup).getOrThrow()
        assertEquals(ImportMode.MERGE, result.mode)
        assertEquals("Existing account remains unchanged", 20000L, db.accountDao().getAccountByIdSync("acc-1")?.openingBalancePaise)
    }

    // 9. Re-importing the same backup in Merge mode produces zero redundant duplicates
    @Test
    fun test9_reimportingSameBackup_producesZeroDuplicates() = runBlocking {
        val backup = createSampleBackup()

        // First import in Merge mode
        val res1 = importer.mergeBackup(backup).getOrThrow()
        assertEquals(3, res1.transactionsImported)
        assertEquals(0, res1.duplicatesSkipped)

        // Second import of the EXACT same backup
        val res2 = importer.mergeBackup(backup).getOrThrow()
        assertEquals(0, res2.transactionsImported)
        assertEquals(3, res2.duplicatesSkipped)
        assertEquals(3, db.transactionDao().getAllTransactionsSync().size)
    }

    // 10. Duplicate candidates that must remain separate until reviewed
    @Test
    fun test10_duplicateCandidates_remainSeparateUntilReviewed() {
        val existingTx = TransactionEntity(
            id = "existing-tx-1", title = "Lunch Swiggy", amount = 250.50, type = "EXPENSE",
            category = "Food", date = "2026-10-18", source = "NOTIFICATION", timestamp = 1729240000000L
        )
        db.transactionDao().insertTransaction(existingTx)

        val incomingTx = BackupTransaction(
            id = "incoming-tx-2", title = "Lunch Swiggy", amount = 250.50, amountPaise = 25050L,
            type = "EXPENSE", category = "Food", date = "2026-10-18", source = "NOTIFICATION",
            timestamp = 1729240000000L, referenceNumber = null // No reference number
        )
        val backup = createSampleBackup().copy(
            data = createSamplePayload().copy(transactions = listOf(incomingTx))
        )

        val preview = BackupValidator.generatePreviewSummary(backup, db)
        assertEquals(1, preview.duplicateCandidates.size)
        assertEquals("Same title, amount, date and type", preview.duplicateCandidates[0].reason)
    }

    // 11. Transactions with identical amounts but different references
    @Test
    fun test11_transactionsWithIdenticalAmountsButDifferentReferences() = runBlocking {
        val tx1 = BackupTransaction(
            id = "tx-ref1", title = "Tea", amount = 60.0, amountPaise = 6000L,
            type = "EXPENSE", category = "Food", date = "2026-10-18", source = "UPI",
            timestamp = 1000L, referenceNumber = "REF001"
        )
        val tx2 = BackupTransaction(
            id = "tx-ref2", title = "Tea", amount = 60.0, amountPaise = 6000L,
            type = "EXPENSE", category = "Food", date = "2026-10-18", source = "UPI",
            timestamp = 2000L, referenceNumber = "REF002"
        )
        val backup = createSampleBackup().copy(
            data = createSamplePayload().copy(transactions = listOf(tx1, tx2))
        )

        val result = importer.mergeBackup(backup).getOrThrow()
        assertEquals(2, result.transactionsImported)
        assertEquals(0, result.duplicatesSkipped)

        val allTx = db.transactionDao().getAllTransactionsSync()
        assertEquals(2, allTx.size)
        assertTrue(allTx.any { it.referenceNumber == "REF001" })
        assertTrue(allTx.any { it.referenceNumber == "REF002" })
    }

    // 12. Currency preservation and exact paise values
    @Test
    fun test12_currencyPreservationAndExactPaiseValues() = runBlocking {
        val txSmall = BackupTransaction(
            id = "tx-small", title = "Tiny", amount = 0.50, amountPaise = 50L,
            type = "EXPENSE", category = "Food", date = "2026-10-18", source = "MANUAL", timestamp = 1000L
        )
        val txLarge = BackupTransaction(
            id = "tx-large", title = "Big", amount = 123456.78, amountPaise = 12345678L,
            type = "EXPENSE", category = "Food", date = "2026-10-18", source = "MANUAL", timestamp = 2000L
        )
        val backup = createSampleBackup().copy(
            data = createSamplePayload().copy(transactions = listOf(txSmall, txLarge))
        )

        val json = BackupJsonSerializer.serialize(backup)
        val restoredBackup = BackupJsonSerializer.deserialize(json)
        assertEquals(50L, restoredBackup.data.transactions[0].amountPaise)
        assertEquals(12345678L, restoredBackup.data.transactions[1].amountPaise)

        importer.restoreBackup(restoredBackup).getOrThrow()
        val all = db.transactionDao().getAllTransactionsSync()
        val foundSmall = all.find { it.id == "tx-small" }!!
        val foundLarge = all.find { it.id == "tx-large" }!!
        assertEquals(50L, Math.round(foundSmall.amount * 100.0))
        assertEquals(12345678L, Math.round(foundLarge.amount * 100.0))
    }

    // 13. Transactional rollback when an import fails
    @Test
    fun test13_transactionalRollback_whenImportFails() = runBlocking {
        // Setup initial database state
        val initialAcc = AccountEntity(id = "init-acc", name = "Initial Account", openingBalancePaise = 10000L)
        db.accountDao().insertAccount(initialAcc)
        assertEquals(1, db.accountDao().getAllAccountsSync().size)

        // Create backup with a broken foreign key that will fail inside transaction
        val base = createSampleBackup()
        val brokenSplit = base.data.expenseSplits[0].copy(payerMemberId = "non-existent-member")
        val brokenBackup = base.copy(data = base.data.copy(expenseSplits = listOf(brokenSplit)))

        // Attempt restore
        val result = importer.restoreBackup(brokenBackup)
        assertTrue("Restore should fail due to foreign key validation", result.isFailure)

        // Verify database rolled back to exact initial state
        val currentAccounts = db.accountDao().getAllAccountsSync()
        assertEquals(1, currentAccounts.size)
        assertEquals("Initial Account", currentAccounts[0].name)
    }

    // 14. Preservation of existing database after a rejected import
    @Test
    fun test14_preservationOfExistingDatabase_afterRejectedImport() = runBlocking {
        val initialAcc = AccountEntity(id = "acc-kept", name = "Keep Me", openingBalancePaise = 42000L)
        val initialTx = TransactionEntity(id = "tx-kept", title = "Keep Tx", amount = 100.0, type = "EXPENSE", category = "Food", date = "2026-10-18", source = "MANUAL", timestamp = 1000L)
        db.accountDao().insertAccount(initialAcc)
        db.transactionDao().insertTransaction(initialTx)

        val invalidJson = "{ invalid json content }"
        try {
            BackupJsonSerializer.deserialize(invalidJson)
            fail("Should fail deserialization")
        } catch (e: BackupValidationException) {
            // Expected
        }

        // Verify existing database is completely untouched
        assertEquals(1, db.accountDao().getAllAccountsSync().size)
        assertEquals("Keep Me", db.accountDao().getAllAccountsSync()[0].name)
        assertEquals(1, db.transactionDao().getAllTransactionsSync().size)
        assertEquals("Keep Tx", db.transactionDao().getAllTransactionsSync()[0].title)
    }

    // 15. Export -> import -> export round-trip equivalence
    @Test
    fun test15_exportImportExportRoundTripEquivalence() = runBlocking {
        val originalBackup = createSampleBackup()

        // 1. Restore originalBackup into DB
        importer.restoreBackup(originalBackup).getOrThrow()

        // 2. Export new snapshot from DB
        val exportedSnapshot = backupManager.createBackupSnapshot(appVersion = "1.0")

        // 3. Serialize and deserialize
        val json = BackupJsonSerializer.serialize(exportedSnapshot)
        val deserialized = BackupJsonSerializer.deserialize(json)

        // 4. Assert round-trip equivalence
        assertEquals(originalBackup.data.accounts.size, deserialized.data.accounts.size)
        assertEquals(originalBackup.data.transactions.size, deserialized.data.transactions.size)
        assertEquals(originalBackup.data.categories.size, deserialized.data.categories.size)
        assertEquals(originalBackup.data.splitGroups.size, deserialized.data.splitGroups.size)
        assertEquals(originalBackup.data.expenseSplits.size, deserialized.data.expenseSplits.size)
        assertEquals(originalBackup.data.repayments.size, deserialized.data.repayments.size)

        // Compare individual entities
        val origTx1 = originalBackup.data.transactions.find { it.id == "tx-1" }!!
        val desTx1 = deserialized.data.transactions.find { it.id == "tx-1" }!!
        assertEquals(origTx1.title, desTx1.title)
        assertEquals(origTx1.amountPaise, desTx1.amountPaise)
        assertEquals(origTx1.referenceNumber, desTx1.referenceNumber)
        assertEquals(origTx1.accountId, desTx1.accountId)

        val origSplit = originalBackup.data.expenseSplits.find { it.id == "sp-1" }!!
        val desSplit = deserialized.data.expenseSplits.find { it.id == "sp-1" }!!
        assertEquals(origSplit.amountPaise, desSplit.amountPaise)
        assertEquals(origSplit.payerMemberId, desSplit.payerMemberId)
    }
}
