package com.example.expensetracker.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.expensetracker.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BackupManager(
    private val db: AppDatabase
) {

    suspend fun createBackupSnapshot(appVersion: String = "1.0"): ExpenseTrackerBackup = withContext(Dispatchers.IO) {
        db.runInTransaction<ExpenseTrackerBackup> {
            val accounts = db.accountDao().getAllAccountsSync()
            val categories = db.categoryDao().getAllCategoriesSync()
            val categoryMappings = db.categoryMappingDao().getAllMappingsSync()
            val transactions = db.transactionDao().getAllTransactionsSync().map { it.toBackupTransaction() }

            val groupsWithMembers = db.splitGroupDao().getAllGroupsWithMembersSync()
            val splitGroups = groupsWithMembers.map { it.group }
            val splitGroupMembers = groupsWithMembers.flatMap { it.members }

            val splitsWithShares = db.expenseSplitDao().getAllSplitsWithSharesSync()
            val expenseSplits = splitsWithShares.map { it.split }
            val expenseSplitShares = splitsWithShares.flatMap { it.shares }

            val repaymentsWithAllocations = db.repaymentDao().getAllRepaymentsWithAllocationsSync()
            val repayments = repaymentsWithAllocations.map { it.repayment }
            val repaymentAllocations = repaymentsWithAllocations.flatMap { it.allocations }

            val recurringExpenses = db.recurringExpenseDao().getAllRecurringExpensesSync()
            val paymentTemplates = db.paymentTemplateDao().getAllTemplatesSync()
            val balanceObservations = db.balanceObservationDao().getAllObservationsSync()
            val sourceAccountMappings = db.sourceAccountMappingDao().getAllMappingsSync()

            val recordCounts = mapOf(
                "accounts" to accounts.size,
                "categories" to categories.size,
                "categoryMappings" to categoryMappings.size,
                "transactions" to transactions.size,
                "splitGroups" to splitGroups.size,
                "splitGroupMembers" to splitGroupMembers.size,
                "expenseSplits" to expenseSplits.size,
                "expenseSplitShares" to expenseSplitShares.size,
                "repayments" to repayments.size,
                "repaymentAllocations" to repaymentAllocations.size,
                "recurringExpenses" to recurringExpenses.size,
                "paymentTemplates" to paymentTemplates.size,
                "balanceObservations" to balanceObservations.size,
                "sourceAccountMappings" to sourceAccountMappings.size
            )

            val metadata = BackupMetadata(
                totalTransactions = transactions.size,
                totalAccounts = accounts.size,
                totalCategories = categories.size,
                totalSplitGroups = splitGroups.size,
                totalRepayments = repayments.size,
                recordCounts = recordCounts
            )

            val payload = BackupDataPayload(
                accounts = accounts,
                categories = categories,
                categoryMappings = categoryMappings,
                transactions = transactions,
                splitGroups = splitGroups,
                splitGroupMembers = splitGroupMembers,
                expenseSplits = expenseSplits,
                expenseSplitShares = expenseSplitShares,
                repayments = repayments,
                repaymentAllocations = repaymentAllocations,
                recurringExpenses = recurringExpenses,
                paymentTemplates = paymentTemplates,
                balanceObservations = balanceObservations,
                sourceAccountMappings = sourceAccountMappings
            )

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val exportedAt = dateFormat.format(Date())

            ExpenseTrackerBackup(
                format = BACKUP_FORMAT_IDENTIFIER,
                schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
                exportedAt = exportedAt,
                appVersion = appVersion,
                databaseVersion = 12,
                data = payload,
                metadata = metadata
            )
        }
    }

    suspend fun exportToStream(backup: ExpenseTrackerBackup, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        BackupJsonSerializer.serializeToStream(backup, outputStream)
    }

    suspend fun exportToUri(context: Context, uri: Uri, backup: ExpenseTrackerBackup): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                BackupJsonSerializer.serializeToStream(backup, outputStream)
            } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
        }
    }

    suspend fun exportToCacheFile(context: Context, backup: ExpenseTrackerBackup): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val backupDir = File(context.cacheDir, "backups").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(backupDir, "expense_tracker_backup_$timestamp.json")
            FileOutputStream(file).use { outputStream ->
                BackupJsonSerializer.serializeToStream(backup, outputStream)
            }
            file
        }
    }

    fun createShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share ExpenseTracker Backup")
    }

    private val importer = BackupImporter(db)

    suspend fun readBackupFromUri(context: Context, uri: Uri): Result<ExpenseTrackerBackup> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BackupJsonSerializer.deserializeFromStream(inputStream)
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")
        }
    }

    suspend fun validateAndPreview(backup: ExpenseTrackerBackup): ImportPreviewSummary = withContext(Dispatchers.IO) {
        BackupValidator.generatePreviewSummary(backup, db)
    }

    suspend fun importBackup(
        backup: ExpenseTrackerBackup,
        mode: ImportMode,
        context: Context? = null
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        when (mode) {
            ImportMode.RESTORE_REPLACE -> importer.restoreBackup(backup, context)
            ImportMode.MERGE -> importer.mergeBackup(backup)
        }
    }
}

