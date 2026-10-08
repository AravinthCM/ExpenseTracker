package com.example.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.data.local.toDomainModel
import com.example.expensetracker.data.local.toEntity
import com.example.expensetracker.model.Transaction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.service.DetectedRecurringCandidate
import com.example.expensetracker.service.RecurringExpenseDetector

import com.example.expensetracker.service.TransactionNotificationHelper

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

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

    val categoriesState: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .map { list -> list.distinctBy { it.name.trim().lowercase() } }
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

    // --- Transaction Operations ---
    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.insertTransaction(transaction.toEntity())

            // Trigger success notification ONLY after Room insert succeeds
            TransactionNotificationHelper.showTransactionAddedNotification(
                context = getApplication(),
                type = transaction.type,
                amount = transaction.amount,
                name = transaction.title,
                source = transaction.source.name
            )

            // Update matching confirmed recurring expense if present
            val confirmedList = recurringExpenseDao.getAllRecurringExpensesSync().filter { it.active && !it.dismissed }
            val matched = RecurringExpenseDetector.findMatchingConfirmedRecurring(transaction, confirmedList)
            if (matched != null) {
                recurringExpenseDao.updateRecurringExpense(
                    matched.copy(lastTransactionTimestamp = transaction.timestamp)
                )
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.updateTransaction(transaction.toEntity())
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
    ): com.example.expensetracker.model.SplitCalculationResult {
        val transaction = transactionDao.getTransactionByIdSync(transactionId)
            ?: return com.example.expensetracker.model.SplitCalculationResult.Error("Transaction not found.")

        val group = splitGroupDao.getGroupWithMembersByIdSync(groupId)
            ?: return com.example.expensetracker.model.SplitCalculationResult.Error("Split group not found.")

        val existingSplit = expenseSplitDao.getSplitForTransactionAndGroupSync(transactionId, groupId)
        if (existingSplit != null) {
            return com.example.expensetracker.model.SplitCalculationResult.Error("Transaction has already been split with this group.")
        }

        val result = com.example.expensetracker.model.ExpenseSplitCalculator.calculateEqualSplit(transaction, group)
        if (result is com.example.expensetracker.model.SplitCalculationResult.Success) {
            viewModelScope.launch(Dispatchers.IO) {
                expenseSplitDao.saveSplitWithShares(result.split, result.shares)
            }
        }
        return result
    }

    fun createCustomExpenseSplit(
        transactionId: String,
        groupId: String,
        customSharesPaise: Map<String, Long>
    ): com.example.expensetracker.model.SplitCalculationResult {
        val transaction = transactionDao.getTransactionByIdSync(transactionId)
            ?: return com.example.expensetracker.model.SplitCalculationResult.Error("Transaction not found.")

        val group = splitGroupDao.getGroupWithMembersByIdSync(groupId)
            ?: return com.example.expensetracker.model.SplitCalculationResult.Error("Split group not found.")

        val existingSplit = expenseSplitDao.getSplitForTransactionAndGroupSync(transactionId, groupId)
        if (existingSplit != null) {
            return com.example.expensetracker.model.SplitCalculationResult.Error("Transaction has already been split with this group.")
        }

        val result = com.example.expensetracker.model.ExpenseSplitCalculator.calculateCustomSplit(transaction, group, customSharesPaise)
        if (result is com.example.expensetracker.model.SplitCalculationResult.Success) {
            viewModelScope.launch(Dispatchers.IO) {
                expenseSplitDao.saveSplitWithShares(result.split, result.shares)
            }
        }
        return result
    }

    fun deleteExpenseSplit(splitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseSplitDao.deleteSplitById(splitId)
        }
    }
}
