package com.example.expensetracker.data.backup

import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.data.local.RepaymentAllocationEntity
import com.example.expensetracker.data.local.RepaymentEntity
import com.example.expensetracker.data.local.SourceAccountMappingEntity
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.TransactionEntity

const val BACKUP_FORMAT_IDENTIFIER = "ExpenseTrackerBackup"
const val CURRENT_BACKUP_SCHEMA_VERSION = 1

data class ExpenseTrackerBackup(
    val format: String = BACKUP_FORMAT_IDENTIFIER,
    val schemaVersion: Int = CURRENT_BACKUP_SCHEMA_VERSION,
    val exportedAt: String,
    val appVersion: String,
    val databaseVersion: Int,
    val data: BackupDataPayload,
    val metadata: BackupMetadata
)

data class BackupTransaction(
    val id: String,
    val title: String,
    val amount: Double,
    val amountPaise: Long,
    val type: String,
    val category: String,
    val date: String,
    val source: String,
    val timestamp: Long,
    val originalText: String = "",
    val accountId: String? = null,
    val referenceNumber: String? = null
) {
    fun toEntity(): TransactionEntity {
        return TransactionEntity(
            id = id,
            title = title,
            amount = amount,
            type = type,
            category = category,
            date = date,
            source = source,
            timestamp = timestamp,
            originalText = originalText,
            accountId = accountId,
            referenceNumber = referenceNumber
        )
    }
}

fun TransactionEntity.toBackupTransaction(): BackupTransaction {
    return BackupTransaction(
        id = id,
        title = title,
        amount = amount,
        amountPaise = Math.round(amount * 100.0),
        type = type,
        category = category,
        date = date,
        source = source,
        timestamp = timestamp,
        originalText = originalText,
        accountId = accountId,
        referenceNumber = referenceNumber
    )
}

data class BackupDataPayload(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val categoryMappings: List<CategoryMappingEntity>,
    val transactions: List<BackupTransaction>,
    val splitGroups: List<SplitGroupEntity>,
    val splitGroupMembers: List<SplitGroupMemberEntity>,
    val expenseSplits: List<ExpenseSplitEntity>,
    val expenseSplitShares: List<ExpenseSplitShareEntity>,
    val repayments: List<RepaymentEntity>,
    val repaymentAllocations: List<RepaymentAllocationEntity>,
    val recurringExpenses: List<RecurringExpenseEntity>,
    val paymentTemplates: List<PaymentTemplateEntity>,
    val balanceObservations: List<BalanceObservationEntity>,
    val sourceAccountMappings: List<SourceAccountMappingEntity>
)

data class BackupMetadata(
    val totalTransactions: Int,
    val totalAccounts: Int,
    val totalCategories: Int,
    val totalSplitGroups: Int,
    val totalRepayments: Int,
    val recordCounts: Map<String, Int>
)

sealed class BackupExportState {
    object Idle : BackupExportState()
    object Loading : BackupExportState()
    data class Success(val message: String, val details: String? = null) : BackupExportState()
    data class Error(val errorMessage: String) : BackupExportState()
}

data class ValidationError(
    val entity: String,
    val recordId: String?,
    val message: String
)

data class ValidationWarning(
    val entity: String,
    val recordId: String?,
    val message: String
)

data class BackupValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val backup: ExpenseTrackerBackup? = null
)

data class IdConflict(
    val entity: String,
    val id: String,
    val existingNameOrTitle: String,
    val incomingNameOrTitle: String
)

data class DuplicateTransactionCandidate(
    val incomingTransaction: BackupTransaction,
    val existingTransaction: TransactionEntity,
    val reason: String
)

enum class ImportMode {
    RESTORE_REPLACE,
    MERGE
}

data class ImportPreviewSummary(
    val backup: ExpenseTrackerBackup,
    val accountsCount: Int,
    val categoriesCount: Int,
    val transactionsCount: Int,
    val incomeCount: Int,
    val expenseCount: Int,
    val transferCount: Int,
    val splitGroupsCount: Int,
    val splitGroupMembersCount: Int,
    val expenseSplitsCount: Int,
    val expenseSplitSharesCount: Int,
    val repaymentsCount: Int,
    val repaymentAllocationsCount: Int,
    val recurringExpensesCount: Int,
    val paymentTemplatesCount: Int,
    val balanceObservationsCount: Int,
    val sourceAccountMappingsCount: Int,
    val totalAmountPaise: Long,
    val validationErrors: List<ValidationError> = emptyList(),
    val validationWarnings: List<ValidationWarning> = emptyList(),
    val idConflicts: List<IdConflict> = emptyList(),
    val duplicateCandidates: List<DuplicateTransactionCandidate> = emptyList(),
    val isRestoreSafe: Boolean = true
)

data class ImportResult(
    val mode: ImportMode,
    val accountsImported: Int,
    val transactionsImported: Int,
    val categoriesImported: Int,
    val splitGroupsImported: Int,
    val repaymentsImported: Int,
    val duplicatesSkipped: Int = 0,
    val safetyBackupFile: String? = null
)

sealed class BackupImportState {
    object Idle : BackupImportState()
    object Validating : BackupImportState()
    data class PreviewReady(val summary: ImportPreviewSummary) : BackupImportState()
    object Importing : BackupImportState()
    data class Success(val result: ImportResult, val message: String) : BackupImportState()
    data class Error(val errorMessage: String, val errors: List<ValidationError> = emptyList()) : BackupImportState()
}


