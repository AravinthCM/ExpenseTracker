package com.example.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.data.local.GPayMemberMappingEntity
import com.example.expensetracker.data.local.GPayRequestEntity
import com.example.expensetracker.data.local.TransactionEditLogEntity
import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.data.local.toDomainModel
import com.example.expensetracker.data.local.toEntity
import com.example.expensetracker.model.GPayMatchStatus
import com.example.expensetracker.model.GPayRequestMatcher
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.service.DetectedRecurringCandidate
import com.example.expensetracker.service.RecurringExpenseDetector

import com.example.expensetracker.service.TransactionNotificationHelper
import com.example.expensetracker.service.CategoryMapper
import com.example.expensetracker.data.preferences.BalancePrivacyPreferences

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    data class CategoryLearningPreview(
        val transaction: Transaction,
        val oldCategory: String,
        val newCategory: String,
        val matchingTransactionCount: Int
    )

    private val db = AppDatabase.getDatabase(application)
    private val transactionDao = db.transactionDao()
    private val categoryDao = db.categoryDao()
    private val categoryMappingDao = db.categoryMappingDao()
    private val recurringExpenseDao = db.recurringExpenseDao()
    private val testNotificationDao = db.testNotificationDao()
    private val paymentTemplateDao = db.paymentTemplateDao()
    private val paymentTemplateRepository = com.example.expensetracker.data.repository.PaymentTemplateRepository(paymentTemplateDao)
    private val splitGroupDao = db.splitGroupDao()
    private val expenseSplitDao = db.expenseSplitDao()
    private val repaymentDao = db.repaymentDao()
    private val accountDao = db.accountDao()
    private val balanceObservationDao = db.balanceObservationDao()
    private val sourceAccountMappingDao = db.sourceAccountMappingDao()
    private val gPayRequestDao = db.gPayRequestDao()
    private val gPayMemberMappingDao = db.gPayMemberMappingDao()
    private val transactionEditLogDao = db.transactionEditLogDao()
    private val balancePrivacyPreferences = BalancePrivacyPreferences(application)

    val accountsState: StateFlow<List<com.example.expensetracker.data.local.AccountEntity>> = accountDao.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val primaryAccountState: StateFlow<com.example.expensetracker.data.local.AccountEntity?> = accountDao.getPrimaryAccountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val accountsWithBalancesState: StateFlow<List<com.example.expensetracker.model.AccountWithBalance>> = kotlinx.coroutines.flow.combine(
        accountsState,
        transactionDao.getAllTransactions()
    ) { accounts, txEntities ->
        accounts.map { account ->
            com.example.expensetracker.model.AccountWithBalance(
                account = account,
                currentBalancePaise = com.example.expensetracker.model.AccountBalanceCalculator.calculateAccountBalancePaise(account, txEntities)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unresolvedObservationsState: StateFlow<List<com.example.expensetracker.data.local.BalanceObservationEntity>> = balanceObservationDao.getUnresolvedObservations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allObservationsState: StateFlow<List<com.example.expensetracker.data.local.BalanceObservationEntity>> = balanceObservationDao.getAllObservations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sourceAccountMappingsState: StateFlow<List<com.example.expensetracker.data.local.SourceAccountMappingEntity>> = sourceAccountMappingDao.getAllMappings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val reconciliationResultsState: StateFlow<Map<String, com.example.expensetracker.model.ReconciliationResult>> = kotlinx.coroutines.flow.combine(
        accountsState,
        transactionDao.getAllTransactions(),
        balanceObservationDao.getAllObservations()
    ) { accounts: List<com.example.expensetracker.data.local.AccountEntity>,
        txEntities: List<com.example.expensetracker.data.local.TransactionEntity>,
        observations: List<com.example.expensetracker.data.local.BalanceObservationEntity> ->
        accounts.associate { account ->
            account.id to com.example.expensetracker.model.BalanceReconciliationEngine.reconcileAccount(
                account = account,
                transactions = txEntities,
                observations = observations
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _selectedAccountId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val selectedAccountIdState: StateFlow<String?> = _selectedAccountId

    val isBalanceVisibleState: StateFlow<Boolean> = balancePrivacyPreferences.isBalanceVisible
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun selectAccount(accountId: String?) {
        _selectedAccountId.value = accountId
    }

    fun toggleBalanceVisibility() {
        viewModelScope.launch {
            balancePrivacyPreferences.setBalanceVisible(!isBalanceVisibleState.value)
        }
    }

    val splitGroupsState: StateFlow<List<com.example.expensetracker.data.local.SplitGroupWithMembers>> = splitGroupDao.getAllGroupsWithMembers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val expenseSplitsState: StateFlow<List<com.example.expensetracker.data.local.ExpenseSplitWithShares>> = expenseSplitDao.getAllSplitsWithShares()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val repaymentsState: StateFlow<List<com.example.expensetracker.data.local.RepaymentWithAllocations>> = repaymentDao.getAllRepaymentsWithAllocations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val paymentTemplatesState: StateFlow<List<com.example.expensetracker.data.local.PaymentTemplateEntity>> = paymentTemplateRepository.allTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transactionsState: StateFlow<List<Transaction>> = transactionDao.getAllTransactions()
        .map { entities -> entities.map { it.toDomainModel() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transactionEditLogsState: StateFlow<List<TransactionEditLogEntity>> = transactionEditLogDao.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categoriesState: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .map { list -> list.sortedBy { it.name.trim().lowercase() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categoryMappingsState: StateFlow<List<CategoryMappingEntity>> = categoryMappingDao.getAllMappings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recurringExpensesState: StateFlow<List<RecurringExpenseEntity>> = recurringExpenseDao.getAllRecurringExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val testNotificationsState: StateFlow<List<com.example.expensetracker.data.local.TestNotificationEntity>> = testNotificationDao.getAllTestNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearTestNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            testNotificationDao.clearAll()
        }
    }

    // --- Account Operations ---
    fun saveAccount(
        id: String?,
        name: String,
        currentBalancePaise: Long,
        isPrimary: Boolean,
        accountNumber: String? = null
    ): String? = runBlocking(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@runBlocking "Account name cannot be empty."

        val existingAccounts = accountDao.getAllAccountsSync()
        val duplicate = existingAccounts.find {
            it.name.equals(trimmed, ignoreCase = true) && (id == null || it.id != id)
        }
        if (duplicate != null) {
            return@runBlocking "Account name '$trimmed' already exists."
        }

        val targetId = if (!id.isNullOrBlank()) id else UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val existingAccount = if (!id.isNullOrBlank()) accountDao.getAccountByIdSync(id) else null

        val accountEntity = com.example.expensetracker.data.local.AccountEntity(
            id = targetId,
            name = trimmed,
            openingBalancePaise = existingAccount?.openingBalancePaise ?: 0L,
            isPrimary = isPrimary,
            isArchived = existingAccount?.isArchived ?: false,
            createdAt = existingAccount?.createdAt ?: now,
            updatedAt = now,
            accountNumber = accountNumber?.trim()?.takeIf { it.isNotBlank() },
            balanceBaselinePaise = currentBalancePaise,
            balanceBaselineTimestamp = now
        )

        if (existingAccount == null) {
            accountDao.insertAccount(accountEntity)
        } else {
            accountDao.updateAccount(accountEntity)
        }
        if (isPrimary) {
            accountDao.setPrimaryAccount(targetId)
        } else if (existingAccounts.isEmpty() || existingAccounts.size == 1 && existingAccounts[0].id == targetId) {
            // If this is the only account, default it to primary
            accountDao.setPrimaryAccount(targetId)
        }
        return@runBlocking null
    }

    // --- Observation & Mapping Operations ---
    fun resolveObservation(observationId: String, accountId: String, rememberMapping: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val obs = balanceObservationDao.getObservationById(observationId) ?: return@launch
            balanceObservationDao.resolveObservation(observationId, accountId)

            val resolvedObs = obs.copy(accountId = accountId, isResolved = true)
            val account = accountDao.getAccountByIdSync(accountId)
            val updatedAccount = account?.let {
                com.example.expensetracker.model.BalanceObservationApplier.applyIfNewer(it, resolvedObs)
            }
            if (updatedAccount != null) {
                accountDao.updateAccount(updatedAccount)
            }

            if (rememberMapping && !obs.source.isBlank()) {
                val mapping = com.example.expensetracker.data.local.SourceAccountMappingEntity(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    sourcePattern = obs.source,
                    senderOrPackage = obs.senderOrPackage,
                    matchingContext = obs.accountSuffix,
                    isConfirmed = true
                )
                sourceAccountMappingDao.insert(mapping)
            }
        }
    }

    fun deleteObservation(observationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            balanceObservationDao.deleteById(observationId)
        }
    }

    fun saveSourceAccountMapping(
        id: String?,
        accountId: String,
        sourcePattern: String,
        senderOrPackage: String?,
        matchingContext: String?,
        isConfirmed: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetId = if (!id.isNullOrBlank()) id else UUID.randomUUID().toString()
            val mapping = com.example.expensetracker.data.local.SourceAccountMappingEntity(
                id = targetId,
                accountId = accountId,
                sourcePattern = sourcePattern.trim(),
                senderOrPackage = senderOrPackage?.trim()?.takeIf { it.isNotBlank() },
                matchingContext = matchingContext?.trim()?.takeIf { it.isNotBlank() },
                isConfirmed = isConfirmed,
                updatedAt = System.currentTimeMillis()
            )
            sourceAccountMappingDao.insert(mapping)
        }
    }

    fun deleteSourceAccountMapping(mappingId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceAccountMappingDao.deleteById(mappingId)
        }
    }

    fun setPrimaryAccount(accountId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (accountDao.getAccountByIdSync(accountId)?.isArchived == false) {
                accountDao.setPrimaryAccount(accountId)
            }
        }
    }

    fun setAccountArchived(accountId: String, isArchived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = accountDao.getAccountByIdSync(accountId) ?: return@launch
            if (isArchived && account.isPrimary) return@launch
            accountDao.setArchivedStatus(accountId, isArchived)
        }
    }

    fun deleteAccount(account: com.example.expensetracker.data.local.AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
        val current = accountDao.getAccountByIdSync(account.id)
            ?: return@launch
        if (current.isPrimary && !current.isArchived) {
            return@launch
        }
            db.runInTransaction {
                transactionDao.reassignTransactions(account.id, null)
                accountDao.deleteAccountById(account.id)
            }
        }
    }

    fun assignUnassignedTransactionsToAccount(accountId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (accountDao.getAccountByIdSync(accountId)?.isArchived == false) {
                transactionDao.assignUnassignedTransactionsToAccount(accountId)
            }
        }
    }

    fun assignUnassignedTransactionIdsToAccount(transactionIds: List<String>, accountId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (accountDao.getAccountByIdSync(accountId)?.isArchived == false && transactionIds.isNotEmpty()) {
                transactionDao.assignUnassignedTransactionIdsToAccount(transactionIds, accountId)
            }
        }
    }

    fun reassignTransactions(fromAccountId: String, toAccountId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.reassignTransactions(fromAccountId, toAccountId)
        }
    }

    // --- Transaction Operations ---
    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val primary = accountDao.getPrimaryAccountSync()
            val finalAccountId = transaction.accountId ?: primary?.id
            val finalTx = if (transaction.accountId == null && finalAccountId != null) {
                transaction.copy(accountId = finalAccountId)
            } else {
                transaction
            }
            transactionDao.insertTransaction(finalTx.toEntity())

            // Trigger success notification ONLY after Room insert succeeds
            TransactionNotificationHelper.showTransactionAddedNotification(
                context = getApplication(),
                type = finalTx.type,
                amount = finalTx.amount,
                name = finalTx.title,
                source = finalTx.source.name,
                balance = null
            )

            // Update matching confirmed recurring expense if present
            val confirmedList = recurringExpenseDao.getAllRecurringExpensesSync().filter { it.active && !it.dismissed }
            val matched = RecurringExpenseDetector.findMatchingConfirmedRecurring(finalTx, confirmedList)
            if (matched != null) {
                recurringExpenseDao.updateRecurringExpense(
                    matched.copy(lastTransactionTimestamp = finalTx.timestamp)
                )
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            updateTransactionWithAudit(
                updated = transaction.toEntity(),
                editSource = "MANUAL_EDIT"
            )
        }
    }

    private fun updateTransactionWithAudit(updated: TransactionEntity, editSource: String) {
        val existing = transactionDao.getTransactionByIdSync(updated.id)
        if (existing == null) {
            transactionDao.updateTransaction(updated)
            return
        }

        val log = buildEditLog(existing, updated, editSource)
        transactionDao.updateTransaction(updated)
        transactionEditLogDao.insert(log)
    }

    private fun buildEditLog(
        previous: TransactionEntity,
        updated: TransactionEntity,
        editSource: String
    ): TransactionEditLogEntity {
        val changes = mutableListOf<Pair<String, Pair<String, String>>>()

        fun addChange(field: String, old: Any?, new: Any?) {
            val oldText = old?.toString() ?: "null"
            val newText = new?.toString() ?: "null"
            if (oldText != newText) {
                changes.add(field to (oldText to newText))
            }
        }

        addChange("title", previous.title, updated.title)
        addChange("amount", previous.amount, updated.amount)
        addChange("type", previous.type, updated.type)
        addChange("category", previous.category, updated.category)
        addChange("date", previous.date, updated.date)
        addChange("accountId", previous.accountId, updated.accountId)
        addChange("referenceNumber", previous.referenceNumber, updated.referenceNumber)
        addChange("balanceIncludedInAccount", previous.balanceIncludedInAccount, updated.balanceIncludedInAccount)

        val hasFieldChanges = changes.isNotEmpty()
        val changedFields = if (hasFieldChanges) {
            changes.joinToString(",") { it.first }
        } else {
            "none"
        }
        val details = if (hasFieldChanges) {
            changes.joinToString("\n") { (field, values) ->
                "$field: ${values.first} -> ${values.second}"
            }
        } else {
            "Edit saved with no field-level value changes."
        }

        return TransactionEditLogEntity(
            id = UUID.randomUUID().toString(),
            transactionId = updated.id,
            editedAt = System.currentTimeMillis(),
            editSource = editSource,
            changedFields = changedFields,
            details = details
        )
    }

    fun previewCategoryLearning(original: Transaction, updated: Transaction): CategoryLearningPreview? {
        if (original.type != TransactionType.EXPENSE || updated.type != TransactionType.EXPENSE) return null
        if (original.category == updated.category || updated.title.trim().isBlank()) return null
        val normalizedTitle = updated.title.trim().lowercase()
        val count = runBlocking(Dispatchers.IO) {
            transactionDao.getAllTransactionsSync().count {
                it.id != updated.id &&
                        it.type.equals("EXPENSE", ignoreCase = true) &&
                        it.title.trim().lowercase() == normalizedTitle
            }
        }
        return CategoryLearningPreview(updated, original.displayCategoryName, updated.displayCategoryName, count)
    }

    fun updateTransactionOnly(transaction: Transaction) {
        updateTransaction(transaction)
    }

    fun convertSplitExpenseToRegular(transactionId: String): String? = runBlocking(Dispatchers.IO) {
        val split = expenseSplitDao.getSplitForTransactionSync(transactionId)
            ?: return@runBlocking "This transaction is already a regular expense."
        if (repaymentDao.countAllocationsForSplitSync(split.split.id) > 0) {
            return@runBlocking "This split has repayment allocations and cannot be converted safely. Review or remove those settlements first."
        }
        db.runInTransaction {
            expenseSplitDao.deleteSplitById(split.split.id)
        }
        return@runBlocking null
    }

    fun updateTransactionAndLearn(original: Transaction, updated: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val escapedPattern = Regex.escape(updated.title.trim())
            if (!CategoryMapper.isValidRegex("(?i)$escapedPattern")) {
                updateTransactionWithAudit(updated.toEntity(), "MANUAL_EDIT")
                return@launch
            }
            db.runInTransaction {
                updateTransactionWithAudit(updated.toEntity(), "MANUAL_EDIT")
                val matchingIds = transactionDao.getAllTransactionsSync()
                    .filter {
                        it.id != updated.id &&
                                it.type.equals("EXPENSE", ignoreCase = true) &&
                                it.title.trim().equals(updated.title.trim(), ignoreCase = true)
                    }
                if (matchingIds.isNotEmpty()) {
                    val matchingById = matchingIds.associateBy { it.id }
                    transactionDao.updateCategoryForTransactionIds(matchingById.keys.toList(), updated.displayCategoryName)
                    matchingById.values.forEach { tx ->
                        if (!tx.category.equals(updated.displayCategoryName, ignoreCase = true)) {
                            transactionEditLogDao.insert(
                                TransactionEditLogEntity(
                                    id = UUID.randomUUID().toString(),
                                    transactionId = tx.id,
                                    editedAt = System.currentTimeMillis(),
                                    editSource = "CATEGORY_LEARN",
                                    changedFields = "category",
                                    details = "category: ${tx.category} -> ${updated.displayCategoryName}"
                                )
                            )
                        }
                    }
                }
                val pattern = "(?i)$escapedPattern"
                val existing = categoryMappingDao.getMappingByPatternSync(pattern)
                if (existing == null) {
                    categoryMappingDao.insertMapping(
                        CategoryMappingEntity(
                            id = UUID.randomUUID().toString(),
                            pattern = pattern,
                            categoryName = updated.displayCategoryName,
                            priority = 0
                        )
                    )
                } else if (!existing.categoryName.equals(updated.displayCategoryName, ignoreCase = true)) {
                    categoryMappingDao.updateMapping(existing.copy(categoryName = updated.displayCategoryName))
                }
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.deleteTransactionById(transaction.id)
        }
    }

    // --- Recurring Expense Operations ---
    fun confirmRecurringCandidate(candidate: DetectedRecurringCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = RecurringExpenseEntity(
                id = UUID.randomUUID().toString(),
                merchant = candidate.merchant,
                amount = candidate.amount,
                frequency = candidate.frequency,
                intervalDays = candidate.intervalDays,
                lastTransactionTimestamp = candidate.lastTransactionTimestamp,
                confirmedAt = System.currentTimeMillis(),
                active = true,
                dismissed = false
            )
            recurringExpenseDao.insertRecurringExpense(entity)
        }
    }

    fun dismissRecurringCandidate(candidate: DetectedRecurringCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = RecurringExpenseEntity(
                id = UUID.randomUUID().toString(),
                merchant = candidate.merchant,
                amount = candidate.amount,
                frequency = candidate.frequency,
                intervalDays = candidate.intervalDays,
                lastTransactionTimestamp = candidate.lastTransactionTimestamp,
                confirmedAt = System.currentTimeMillis(),
                active = false,
                dismissed = true
            )
            recurringExpenseDao.insertRecurringExpense(entity)
        }
    }

    // --- Category Operations ---
    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = trimmed.lowercase()
            if (categoryDao.getAllCategoriesSync().any { it.name.trim().lowercase() == normalized }) return@launch
            categoryDao.insertCategory(
                CategoryEntity(id = UUID.randomUUID().toString(), name = trimmed)
            )
        }
    }

    fun updateCategory(category: CategoryEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.updateCategoryForTransactions(oldCategory = category.name, newCategory = trimmed)
            categoryMappingDao.updateCategoryForMappings(oldCategory = category.name, newCategory = trimmed)
            categoryDao.updateCategory(category.copy(name = trimmed))
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Safe deletion: move affected transactions and mappings to "Other" before deleting category
            if (!category.name.equals("Other", ignoreCase = true)) {
                transactionDao.updateCategoryForTransactions(oldCategory = category.name, newCategory = "Other")
                categoryMappingDao.updateCategoryForMappings(oldCategory = category.name, newCategory = "Other")
            }
            categoryDao.deleteCategoryById(category.id)
        }
    }

    // --- Category Mapping Operations ---
    fun addCategoryMapping(pattern: String, categoryName: String, priority: Int = 0) {
        val trimmedPattern = pattern.trim()
        if (trimmedPattern.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            categoryMappingDao.insertMapping(
                CategoryMappingEntity(
                    id = UUID.randomUUID().toString(),
                    pattern = trimmedPattern,
                    categoryName = categoryName,
                    priority = priority
                )
            )
        }
    }

    fun updateCategoryMapping(mapping: CategoryMappingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryMappingDao.updateMapping(mapping)
        }
    }

    fun deleteCategoryMapping(mapping: CategoryMappingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryMappingDao.deleteMapping(mapping)
        }
    }

    // --- Payment Template Operations ---
    fun addPaymentTemplate(name: String, transactionType: String, sampleText: String, templatePattern: String) {
        val trimmedName = name.trim()
        val trimmedPattern = templatePattern.trim()
        if (trimmedName.isBlank() || trimmedPattern.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entity = com.example.expensetracker.data.local.PaymentTemplateEntity(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                transactionType = transactionType,
                sampleText = sampleText.trim(),
                templatePattern = trimmedPattern,
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
            paymentTemplateRepository.insert(entity)
        }
    }

    fun updatePaymentTemplate(template: com.example.expensetracker.data.local.PaymentTemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            paymentTemplateRepository.update(template.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun togglePaymentTemplateEnabled(template: com.example.expensetracker.data.local.PaymentTemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            paymentTemplateRepository.update(template.copy(enabled = !template.enabled, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePaymentTemplate(template: com.example.expensetracker.data.local.PaymentTemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            paymentTemplateRepository.delete(template)
        }
    }

    // --- Split Group Operations ---
    fun saveSplitGroup(
        groupId: String?,
        groupName: String,
        members: List<com.example.expensetracker.model.DraftMember>
    ): com.example.expensetracker.model.ValidationResult {
        val validation = com.example.expensetracker.model.SplitGroupValidator.validate(groupName, members)
        if (!validation.isValid) {
            return validation
        }

        val sanitizedGroupName = com.example.expensetracker.model.SplitGroupValidator.sanitizeGroupName(groupName)
        val now = System.currentTimeMillis()
        val targetGroupId = if (!groupId.isNullOrBlank()) groupId else UUID.randomUUID().toString()

        val groupEntity = com.example.expensetracker.data.local.SplitGroupEntity(
            id = targetGroupId,
            name = sanitizedGroupName,
            createdAt = now,
            updatedAt = now
        )

        val memberEntities = members.map { draft ->
            com.example.expensetracker.data.local.SplitGroupMemberEntity(
                id = if (draft.id.isNotBlank()) draft.id else UUID.randomUUID().toString(),
                groupId = targetGroupId,
                name = com.example.expensetracker.model.SplitGroupValidator.sanitizeMemberName(draft.name),
                isMe = draft.isMe,
                openingDebtPaise = draft.openingDebtPaise,
                createdAt = now
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            splitGroupDao.saveGroupWithMembers(groupEntity, memberEntities)
        }

        return validation
    }

    fun deleteSplitGroup(group: com.example.expensetracker.data.local.SplitGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            splitGroupDao.deleteGroup(group)
        }
    }

    // --- Expense Split Operations ---
    fun createEqualExpenseSplit(
        transactionId: String,
        groupId: String
    ): com.example.expensetracker.model.SplitCalculationResult = runBlocking(Dispatchers.IO) {
        val transaction = transactionDao.getTransactionByIdSync(transactionId)
            ?: return@runBlocking com.example.expensetracker.model.SplitCalculationResult.Error("Transaction not found.")

        val group = splitGroupDao.getGroupWithMembersByIdSync(groupId)
            ?: return@runBlocking com.example.expensetracker.model.SplitCalculationResult.Error("Split group not found.")

        val existingSplit = expenseSplitDao.getSplitForTransactionAndGroupSync(transactionId, groupId)
        if (existingSplit != null) {
            return@runBlocking com.example.expensetracker.model.SplitCalculationResult.Error("Transaction has already been split with this group.")
        }

        val result = com.example.expensetracker.model.ExpenseSplitCalculator.calculateEqualSplit(transaction, group)
        if (result is com.example.expensetracker.model.SplitCalculationResult.Success) {
            expenseSplitDao.saveSplitWithShares(result.split, result.shares)
        }
        return@runBlocking result
    }

    fun createCustomExpenseSplit(
        transactionId: String,
        groupId: String,
        customSharesPaise: Map<String, Long>
    ): com.example.expensetracker.model.SplitCalculationResult = runBlocking(Dispatchers.IO) {
        val transaction = transactionDao.getTransactionByIdSync(transactionId)
            ?: return@runBlocking com.example.expensetracker.model.SplitCalculationResult.Error("Transaction not found.")

        val group = splitGroupDao.getGroupWithMembersByIdSync(groupId)
            ?: return@runBlocking com.example.expensetracker.model.SplitCalculationResult.Error("Split group not found.")

        val existingSplit = expenseSplitDao.getSplitForTransactionAndGroupSync(transactionId, groupId)
        if (existingSplit != null) {
            return@runBlocking com.example.expensetracker.model.SplitCalculationResult.Error("Transaction has already been split with this group.")
        }

        val result = com.example.expensetracker.model.ExpenseSplitCalculator.calculateCustomSplit(transaction, group, customSharesPaise)
        if (result is com.example.expensetracker.model.SplitCalculationResult.Success) {
            expenseSplitDao.saveSplitWithShares(result.split, result.shares)
        }
        return@runBlocking result
    }

    fun deleteExpenseSplit(splitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseSplitDao.deleteSplitById(splitId)
        }
    }

    fun removeSplit(splitId: String): String? = runBlocking(Dispatchers.IO) {
        val split = expenseSplitDao.getAllSplitsWithSharesSync().firstOrNull { it.split.id == splitId }
            ?: return@runBlocking null
        if (repaymentDao.countAllocationsForSplitSync(splitId) > 0) {
            return@runBlocking "This split has repayment allocations and cannot be removed safely. Undo or review those repayments first."
        }
        db.runInTransaction {
            expenseSplitDao.deleteSplitById(split.split.id)
        }
        null
    }

    fun removeSplitsForTransaction(transactionId: String): String? = runBlocking(Dispatchers.IO) {
        val splits = expenseSplitDao.getAllSplitsWithSharesSync()
            .filter { it.split.transactionId == transactionId }
        if (splits.any { repaymentDao.countAllocationsForSplitSync(it.split.id) > 0 }) {
            return@runBlocking "This transaction has repayment allocations and cannot be removed from its split safely. Undo or review those repayments first."
        }
        db.runInTransaction {
            splits.forEach { expenseSplitDao.deleteSplitById(it.split.id) }
        }
        null
    }

    fun recordRepayment(
        groupId: String,
        memberId: String,
        amountPaise: Long,
        paymentDate: String,
        linkedTransactionId: String? = null,
        note: String? = null
    ): com.example.expensetracker.model.RepaymentRecordResult = runBlocking(Dispatchers.IO) {
        val splits = expenseSplitDao.getAllSplitsWithSharesSync()
        val groups = splitGroupDao.getAllGroupsWithMembersSync()
        val transactions = transactionDao.getAllTransactionsSync()
        val repayments = repaymentDao.getAllRepaymentsWithAllocationsSync()

        val result = com.example.expensetracker.model.ExpenseSplitCalculator.calculateRepaymentAllocation(
            groupId = groupId,
            memberId = memberId,
            repaymentAmountPaise = amountPaise,
            paymentDate = paymentDate,
            linkedTransactionId = linkedTransactionId,
            note = note,
            splitsWithShares = splits,
            groupsWithMembers = groups,
            transactions = transactions,
            repaymentsWithAllocations = repayments
        )

        if (result is com.example.expensetracker.model.RepaymentRecordResult.Success) {
            repaymentDao.saveRepaymentWithAllocations(result.repayment, result.allocations)
        }

        return@runBlocking result
    }

    fun deleteRepayment(repaymentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repaymentDao.deleteRepaymentById(repaymentId)
        }
    }

    // --- GPay Requests ---
    val gPayRequestsState: StateFlow<List<GPayRequestEntity>> = gPayRequestDao.getAllRequests()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val gPayMemberMappingsState: StateFlow<List<GPayMemberMappingEntity>> = gPayMemberMappingDao.getAllMappings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Assigns (or reassigns) the group for a request that couldn't be auto-resolved, then re-attempts member matching. */
    fun assignGPayRequestGroup(requestId: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = gPayRequestDao.getByIdSync(requestId) ?: return@launch
            val group = splitGroupDao.getGroupWithMembersByIdSync(groupId) ?: return@launch
            val mappings = gPayMemberMappingDao.getAllMappingsSync()
            val memberMatch = GPayRequestMatcher.matchMember(request.requesterRawName, group, mappings)
            val (matchedMemberId, matchStatus) = when (memberMatch) {
                is GPayRequestMatcher.MemberMatchResult.Matched -> memberMatch.member.id to GPayMatchStatus.AUTO_MATCHED
                GPayRequestMatcher.MemberMatchResult.Ambiguous -> null to GPayMatchStatus.AMBIGUOUS_MEMBER
                GPayRequestMatcher.MemberMatchResult.NotFound -> null to GPayMatchStatus.NEEDS_MEMBER
            }
            gPayRequestDao.upsert(
                request.copy(
                    matchedGroupId = groupId,
                    matchedMemberId = matchedMemberId,
                    matchStatus = matchStatus,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Assigns (or corrects) the member for a request; optionally remembers the mapping for future requests. */
    fun assignGPayRequestMember(requestId: String, memberId: String, rememberMapping: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = gPayRequestDao.getByIdSync(requestId) ?: return@launch
            val groupId = request.matchedGroupId ?: return@launch
            gPayRequestDao.upsert(
                request.copy(
                    matchedMemberId = memberId,
                    matchStatus = GPayMatchStatus.AUTO_MATCHED,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (rememberMapping) {
                gPayMemberMappingDao.upsert(
                    GPayMemberMappingEntity(
                        groupId = groupId,
                        memberId = memberId,
                        normalizedRequesterName = request.requesterNormalizedName
                    )
                )
            }
        }
    }

    fun deleteGPayMemberMapping(mappingId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gPayMemberMappingDao.deleteById(mappingId)
        }
    }

    fun dismissGPayRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = gPayRequestDao.getByIdSync(requestId) ?: return@launch
            if (request.processingStatus == com.example.expensetracker.model.GPayProcessingStatus.PENDING) {
                gPayRequestDao.upsert(
                    request.copy(
                        processingStatus = com.example.expensetracker.model.GPayProcessingStatus.DISMISSED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /** Marks a pending request as closed; optionally links the real payment transaction used to settle it. */
    fun closeGPayRequest(requestId: String, transactionId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = gPayRequestDao.getByIdSync(requestId) ?: return@launch
            if (request.processingStatus != com.example.expensetracker.model.GPayProcessingStatus.PENDING) return@launch

            gPayRequestDao.upsert(
                request.copy(
                    processingStatus = com.example.expensetracker.model.GPayProcessingStatus.DISMISSED,
                    paymentConfirmationTransactionId = transactionId ?: request.paymentConfirmationTransactionId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Creates the group expense from a resolved (AUTO_MATCHED, still PENDING) GPay request. */
    fun confirmGPayRequestAsExpense(requestId: String): String? = runBlocking(Dispatchers.IO) {
        val request = gPayRequestDao.getByIdSync(requestId)
            ?: return@runBlocking "Request not found."
        val groupId = request.matchedGroupId
            ?: return@runBlocking "Assign a group before adding this as an expense."
        val group = splitGroupDao.getGroupWithMembersByIdSync(groupId)
            ?: return@runBlocking "Split group not found."

        when (val result = com.example.expensetracker.model.GPayExpenseCreator.createFromRequest(request, group)) {
            is com.example.expensetracker.model.GPayExpenseCreationResult.Success -> {
                db.runInTransaction {
                    transactionDao.insertTransaction(result.transaction)
                    expenseSplitDao.saveSplitWithShares(result.split, result.shares)
                    gPayRequestDao.upsert(
                        request.copy(
                            processingStatus = com.example.expensetracker.model.GPayProcessingStatus.ADDED_AS_EXPENSE,
                            linkedTransactionId = result.transaction.id,
                            linkedSplitId = result.split.id,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                null
            }
            is com.example.expensetracker.model.GPayExpenseCreationResult.Error -> result.message
        }
    }

    /** Confirms a detected real GPay payment as the repayment settling the given request. */
    fun confirmGPayRepayment(requestId: String, paymentDate: String, note: String? = null): com.example.expensetracker.model.RepaymentRecordResult = runBlocking(Dispatchers.IO) {
        val request = gPayRequestDao.getByIdSync(requestId)
            ?: return@runBlocking com.example.expensetracker.model.RepaymentRecordResult.Error("Request not found.")
        val groupId = request.matchedGroupId
            ?: return@runBlocking com.example.expensetracker.model.RepaymentRecordResult.Error("Request has no resolved group.")
        val memberId = request.matchedMemberId
            ?: return@runBlocking com.example.expensetracker.model.RepaymentRecordResult.Error("Request has no resolved member.")
        val linkedTransactionId = request.paymentConfirmationTransactionId
            ?: return@runBlocking com.example.expensetracker.model.RepaymentRecordResult.Error("No payment confirmation captured yet for this request.")
        if (request.repaymentId != null) {
            return@runBlocking com.example.expensetracker.model.RepaymentRecordResult.Error("A repayment has already been recorded for this request.")
        }

        val result = recordRepayment(
            groupId = groupId,
            memberId = memberId,
            amountPaise = request.amountPaise,
            paymentDate = paymentDate,
            linkedTransactionId = linkedTransactionId,
            note = note
        )
        if (result is com.example.expensetracker.model.RepaymentRecordResult.Success) {
            gPayRequestDao.upsert(request.copy(repaymentId = result.repayment.id, updatedAt = System.currentTimeMillis()))
        }
        result
    }

    val backupManager = com.example.expensetracker.data.backup.BackupManager(db)

    private val _backupExportState = kotlinx.coroutines.flow.MutableStateFlow<com.example.expensetracker.data.backup.BackupExportState>(
        com.example.expensetracker.data.backup.BackupExportState.Idle
    )
    val backupExportState: StateFlow<com.example.expensetracker.data.backup.BackupExportState> = _backupExportState

    fun resetBackupExportState() {
        _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Idle
    }

    fun exportBackupToUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Loading
            try {
                val backup = backupManager.createBackupSnapshot(appVersion = "1.0")
                val result = backupManager.exportToUri(getApplication(), uri, backup)
                if (result.isSuccess) {
                    val summary = "Successfully exported ${backup.metadata.totalTransactions} transactions, ${backup.metadata.totalAccounts} accounts, ${backup.metadata.totalCategories} categories."
                    _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Success(
                        message = "Backup saved successfully",
                        details = summary
                    )
                } else {
                    _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Error(
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to write backup file"
                    )
                }
            } catch (e: Exception) {
                _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Error(
                    e.localizedMessage ?: "Error during backup creation"
                )
            }
        }
    }

    fun exportBackupForShare(onReadyToShare: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Loading
            try {
                val backup = backupManager.createBackupSnapshot(appVersion = "1.0")
                val result = backupManager.exportToCacheFile(getApplication(), backup)
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    val intent = backupManager.createShareIntent(getApplication(), file)
                    val summary = "Created backup package with ${backup.metadata.totalTransactions} transactions and ${backup.metadata.totalAccounts} accounts."
                    _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Success(
                        message = "Backup file ready to share",
                        details = summary
                    )
                    onReadyToShare(intent)
                } else {
                    _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Error(
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to generate backup file"
                    )
                }
            } catch (e: Exception) {
                _backupExportState.value = com.example.expensetracker.data.backup.BackupExportState.Error(
                    e.localizedMessage ?: "Error preparing backup share"
                )
            }
        }
    }

    private val _backupImportState = kotlinx.coroutines.flow.MutableStateFlow<com.example.expensetracker.data.backup.BackupImportState>(
        com.example.expensetracker.data.backup.BackupImportState.Idle
    )
    val backupImportState: StateFlow<com.example.expensetracker.data.backup.BackupImportState> = _backupImportState

    fun resetBackupImportState() {
        _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Idle
    }

    fun dismissImportPreview() {
        _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Idle
    }

    fun loadAndValidateBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Validating
            try {
                val readResult = backupManager.readBackupFromUri(getApplication(), uri)
                if (readResult.isSuccess) {
                    val backup = readResult.getOrThrow()
                    val summary = backupManager.validateAndPreview(backup)
                    _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.PreviewReady(summary)
                } else {
                    _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Error(
                        readResult.exceptionOrNull()?.localizedMessage ?: "Failed to read backup file"
                    )
                }
            } catch (e: Exception) {
                _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Error(
                    e.localizedMessage ?: "Error parsing backup file"
                )
            }
        }
    }

    fun confirmImport(mode: com.example.expensetracker.data.backup.ImportMode) {
        val currentState = _backupImportState.value
        if (currentState !is com.example.expensetracker.data.backup.BackupImportState.PreviewReady) return

        viewModelScope.launch {
            _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Importing
            try {
                val backup = currentState.summary.backup
                val result = backupManager.importBackup(backup, mode, getApplication())
                if (result.isSuccess) {
                    val importRes = result.getOrThrow()
                    val msg = when (mode) {
                        com.example.expensetracker.data.backup.ImportMode.RESTORE_REPLACE ->
                            "Database restored successfully (${importRes.transactionsImported} transactions, ${importRes.accountsImported} accounts)."
                        com.example.expensetracker.data.backup.ImportMode.MERGE ->
                            "Data merged successfully (${importRes.transactionsImported} added, ${importRes.duplicatesSkipped} duplicates skipped)."
                    }
                    _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Success(importRes, msg)
                } else {
                    _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Error(
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to perform import"
                    )
                }
            } catch (e: Exception) {
                _backupImportState.value = com.example.expensetracker.data.backup.BackupImportState.Error(
                    e.localizedMessage ?: "Error during import operation"
                )
            }
        }
    }
}


