package com.example.expensetracker.data.backup

import android.content.Context
import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.data.local.ExpenseSplitEntity
import com.example.expensetracker.data.local.ExpenseSplitShareEntity
import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.data.local.RepaymentAllocationEntity
import com.example.expensetracker.data.local.RepaymentEntity
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupMemberEntity
import com.example.expensetracker.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BackupImporter(
    private val db: AppDatabase
) {

    suspend fun restoreBackup(
        backup: ExpenseTrackerBackup,
        context: Context? = null
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validation = BackupValidator.validateBackup(backup)
            if (!validation.isValid) {
                val errorMsg = validation.errors.joinToString("; ") { "[${it.entity}] ${it.message}" }
                throw BackupValidationException("Backup validation failed: $errorMsg")
            }

            // Create emergency safety backup if database currently has data and context is available
            var safetyBackupPath: String? = null
            if (context != null) {
                val currentTxCount = db.transactionDao().getAllTransactionsSync().size
                if (currentTxCount > 0) {
                    val safetyBackup = BackupManager(db).createBackupSnapshot(appVersion = "1.0")
                    val backupDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val safetyFile = File(backupDir, "emergency_pre_restore_backup_$timestamp.json")
                    FileOutputStream(safetyFile).use { out ->
                        BackupJsonSerializer.serializeToStream(safetyBackup, out)
                    }
                    safetyBackupPath = safetyFile.absolutePath
                }
            }

            val data = backup.data

            db.runInTransaction {
                // Delete existing records in child-to-parent order to respect foreign keys
                db.openHelper.writableDatabase.execSQL("DELETE FROM repayment_allocations")
                db.openHelper.writableDatabase.execSQL("DELETE FROM repayments")
                db.openHelper.writableDatabase.execSQL("DELETE FROM expense_split_shares")
                db.openHelper.writableDatabase.execSQL("DELETE FROM expense_splits")
                db.openHelper.writableDatabase.execSQL("DELETE FROM split_group_members")
                db.openHelper.writableDatabase.execSQL("DELETE FROM split_groups")
                db.openHelper.writableDatabase.execSQL("DELETE FROM balance_observations")
                db.openHelper.writableDatabase.execSQL("DELETE FROM source_account_mappings")
                db.openHelper.writableDatabase.execSQL("DELETE FROM transactions")
                db.openHelper.writableDatabase.execSQL("DELETE FROM payment_templates")
                db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_expenses")
                db.openHelper.writableDatabase.execSQL("DELETE FROM category_mappings")
                db.openHelper.writableDatabase.execSQL("DELETE FROM categories")
                db.openHelper.writableDatabase.execSQL("DELETE FROM accounts")

                val canonicalCategories = mutableListOf<CategoryEntity>()
                val categoryNamesByKey = mutableMapOf<String, String>()
                data.categories.forEach { category ->
                    val normalizedName = category.name.trim()
                    if (normalizedName.isBlank()) {
                        throw BackupValidationException("Imported category ${category.id} has a blank name")
                    }
                    val existing = canonicalCategories.firstOrNull {
                        normalizeCategoryKey(it.name) == normalizeCategoryKey(normalizedName)
                    }
                    val canonical = existing ?: category.copy(name = normalizedName).also { canonicalCategories.add(it) }
                    categoryNamesByKey[category.id] = canonical.name
                }

                // Insert in parent-to-child order to satisfy foreign keys
                data.accounts.forEach { db.accountDao().insertAccount(it) }
                canonicalCategories.forEach { db.categoryDao().insertCategory(it) }
                data.categoryMappings.forEach { mapping ->
                    val categoryName = data.categories.firstOrNull { it.id == mapping.categoryName }?.let {
                        categoryNamesByKey[it.id]
                    } ?: mapping.categoryName.trim()
                    db.categoryMappingDao().insertMapping(mapping.copy(categoryName = categoryName))
                }
                data.recurringExpenses.forEach { db.recurringExpenseDao().insertRecurringExpense(it) }
                data.paymentTemplates.forEach { db.paymentTemplateDao().insert(it) }
                data.sourceAccountMappings.forEach { db.sourceAccountMappingDao().insert(it) }
                data.balanceObservations.forEach { db.balanceObservationDao().insert(it) }

                val resolvedCategories = canonicalCategories.toMutableList()
                data.transactions.forEach {
                    val categoryName = resolveImportedCategoryName(
                        categoryValue = it.category,
                        sourceCategories = data.categories,
                        existingCategories = resolvedCategories,
                        categoryNamesByKey = categoryNamesByKey,
                        insertCategory = { category ->
                            db.categoryDao().insertCategory(category)
                            resolvedCategories.add(category)
                        }
                    )
                    db.transactionDao().insertTransaction(it.toEntity().copy(category = categoryName))
                }

                data.splitGroups.forEach { db.splitGroupDao().insertGroup(it) }
                if (data.splitGroupMembers.isNotEmpty()) {
                    db.splitGroupDao().insertMembers(data.splitGroupMembers)
                }

                data.expenseSplits.forEach { db.expenseSplitDao().insertSplit(it) }
                if (data.expenseSplitShares.isNotEmpty()) {
                    db.expenseSplitDao().insertShares(data.expenseSplitShares)
                }

                data.repayments.forEach { db.repaymentDao().insertRepayment(it) }
                if (data.repaymentAllocations.isNotEmpty()) {
                    db.repaymentDao().insertAllocations(data.repaymentAllocations)
                }
            }

            ImportResult(
                mode = ImportMode.RESTORE_REPLACE,
                accountsImported = data.accounts.size,
                transactionsImported = data.transactions.size,
                categoriesImported = data.categories.size,
                splitGroupsImported = data.splitGroups.size,
                repaymentsImported = data.repayments.size,
                duplicatesSkipped = 0,
                safetyBackupFile = safetyBackupPath
            )
        }
    }

    suspend fun mergeBackup(
        backup: ExpenseTrackerBackup
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validation = BackupValidator.validateBackup(backup)
            if (!validation.isValid) {
                val errorMsg = validation.errors.joinToString("; ") { "[${it.entity}] ${it.message}" }
                throw BackupValidationException("Backup validation failed: $errorMsg")
            }

            val data = backup.data
            var accountsAdded = 0
            var transactionsAdded = 0
            var categoriesAdded = 0
            var splitGroupsAdded = 0
            var repaymentsAdded = 0
            var duplicatesSkipped = 0

            db.runInTransaction {
                // 1. Categories
                val existingCategories = db.categoryDao().getAllCategoriesSync()
                val existingCatByName = existingCategories.groupBy { normalizeCategoryKey(it.name) }
                    .mapValues { (_, categories) -> categories.minBy { it.id } }
                    .toMutableMap()
                val categoryNamesByKey = mutableMapOf<String, String>()
                    val resolvedCategories = existingCatByName.values.toMutableList()
                data.categories.forEach { cat ->
                    val normalizedName = cat.name.trim()
                    if (normalizedName.isBlank()) {
                        throw BackupValidationException("Imported category ${cat.id} has a blank name")
                    }
                    val key = normalizeCategoryKey(normalizedName)
                    val localCategory = existingCatByName[key]
                    if (localCategory == null) {
                        val categoryId = if (existingCategories.none { it.id == cat.id }) cat.id else UUID.randomUUID().toString()
                        val insertedCategory = cat.copy(id = categoryId, name = normalizedName)
                        db.categoryDao().insertCategory(insertedCategory)
                        existingCatByName[key] = insertedCategory
                        resolvedCategories.add(insertedCategory)
                        categoriesAdded++
                        categoryNamesByKey[cat.id] = insertedCategory.name
                    } else {
                        categoryNamesByKey[cat.id] = localCategory.name
                    }
                }

                // 2. Category Mappings
                val existingCategoryMappings = db.categoryMappingDao().getAllMappingsSync()
                val existingPatterns = existingCategoryMappings.map { it.pattern.trim().lowercase() }.toSet()
                data.categoryMappings.forEach { map ->
                    if (map.pattern.trim().lowercase() !in existingPatterns) {
                        db.categoryMappingDao().insertMapping(map)
                    }
                }

                // 3. Accounts
                val existingAccounts = db.accountDao().getAllAccountsSync()
                val existingAccById = existingAccounts.associateBy { it.id }
                val existingAccByName = existingAccounts.associateBy { it.name.trim().lowercase() }
                val existingAccByNum = existingAccounts.filter { !it.accountNumber.isNullOrBlank() }
                    .associateBy { it.accountNumber!!.trim().lowercase() }

                val accountIdMapping = mutableMapOf<String, String>()

                data.accounts.forEach { acc ->
                    val byId = existingAccById[acc.id]
                    val byName = existingAccByName[acc.name.trim().lowercase()]
                    val byNum = acc.accountNumber?.trim()?.lowercase()?.let { existingAccByNum[it] }

                    val matchedExisting = byId ?: byName ?: byNum
                    if (matchedExisting != null) {
                        accountIdMapping[acc.id] = matchedExisting.id
                    } else {
                        // Insert new account. If primary already exists, set isPrimary to false
                        val hasPrimary = existingAccounts.any { it.isPrimary }
                        val safeAccount = if (hasPrimary && acc.isPrimary) acc.copy(isPrimary = false) else acc
                        db.accountDao().insertAccount(safeAccount)
                        accountIdMapping[acc.id] = safeAccount.id
                        accountsAdded++
                    }
                }

                // 4. Transactions
                val existingTransactions = db.transactionDao().getAllTransactionsSync()
                val existingTxById = existingTransactions.associateBy { it.id }
                val existingTxByRef = existingTransactions.filter { !it.referenceNumber.isNullOrBlank() }
                    .associateBy { it.referenceNumber!! }

                val transactionIdMapping = mutableMapOf<String, String>()

                data.transactions.forEach { tx ->
                    // Check duplicate by reference number
                    if (!tx.referenceNumber.isNullOrBlank() && existingTxByRef.containsKey(tx.referenceNumber)) {
                        val existing = existingTxByRef[tx.referenceNumber]!!
                        transactionIdMapping[tx.id] = existing.id
                        duplicatesSkipped++
                        return@forEach
                    }

                    // Check exact match (same amount, same date, same type, matching title)
                    val exactMatch = existingTransactions.find { ex ->
                        ex.date == tx.date &&
                        ex.type.equals(tx.type, ignoreCase = true) &&
                        Math.abs(ex.amount - tx.amount) < 0.001 &&
                        ex.title.trim().equals(tx.title.trim(), ignoreCase = true)
                    }

                    if (exactMatch != null && tx.referenceNumber.isNullOrBlank()) {
                        transactionIdMapping[tx.id] = exactMatch.id
                        duplicatesSkipped++
                        return@forEach
                    }

                    // Resolve accountId
                    val resolvedAccountId = tx.accountId?.let { accountIdMapping[it] ?: it }

                    // Check ID collision with an existing distinct transaction
                    val targetId = if (existingTxById.containsKey(tx.id)) UUID.randomUUID().toString() else tx.id
                    transactionIdMapping[tx.id] = targetId

                    val entity = tx.toEntity().copy(
                        id = targetId,
                        accountId = resolvedAccountId,
                        category = resolveImportedCategoryName(
                            categoryValue = tx.category,
                            sourceCategories = data.categories,
                            existingCategories = resolvedCategories,
                            categoryNamesByKey = categoryNamesByKey,
                            insertCategory = { category ->
                                db.categoryDao().insertCategory(category)
                                existingCatByName[normalizeCategoryKey(category.name)] = category
                                resolvedCategories.add(category)
                                categoriesAdded++
                            }
                        )
                    )
                    db.transactionDao().insertTransaction(entity)
                    transactionsAdded++
                }

                // 5. Split Groups & Members
                val existingGroupsWithMembers = db.splitGroupDao().getAllGroupsWithMembersSync()
                val existingGroupByName = existingGroupsWithMembers.associateBy { it.group.name.trim().lowercase() }
                val existingGroupById = existingGroupsWithMembers.associateBy { it.group.id }

                val groupIdMapping = mutableMapOf<String, String>()
                val memberIdMapping = mutableMapOf<String, String>()

                val membersByGroup = data.splitGroupMembers.groupBy { it.groupId }

                data.splitGroups.forEach { grp ->
                    val matchedGroup = existingGroupById[grp.id] ?: existingGroupByName[grp.name.trim().lowercase()]
                    if (matchedGroup != null) {
                        groupIdMapping[grp.id] = matchedGroup.group.id
                        // Match members by name
                        val incomingMembers = membersByGroup[grp.id] ?: emptyList()
                        incomingMembers.forEach { inMember ->
                            val matchedMember = matchedGroup.members.find {
                                it.name.trim().equals(inMember.name.trim(), ignoreCase = true)
                            }
                            if (matchedMember != null) {
                                memberIdMapping[inMember.id] = matchedMember.id
                            } else {
                                val newMemberId = UUID.randomUUID().toString()
                                memberIdMapping[inMember.id] = newMemberId
                                db.splitGroupDao().insertMembers(
                                    listOf(inMember.copy(id = newMemberId, groupId = matchedGroup.group.id))
                                )
                            }
                        }
                    } else {
                        groupIdMapping[grp.id] = grp.id
                        db.splitGroupDao().insertGroup(grp)
                        splitGroupsAdded++
                        val incomingMembers = membersByGroup[grp.id] ?: emptyList()
                        incomingMembers.forEach { inMember ->
                            memberIdMapping[inMember.id] = inMember.id
                        }
                        if (incomingMembers.isNotEmpty()) {
                            db.splitGroupDao().insertMembers(incomingMembers)
                        }
                    }
                }

                // 6. Expense Splits & Shares
                val existingSplits = db.expenseSplitDao().getAllSplitsWithSharesSync()
                val existingSplitById = existingSplits.associateBy { it.split.id }
                val splitIdMapping = mutableMapOf<String, String>()
                val shareIdMapping = mutableMapOf<String, String>()
                val sharesBySplit = data.expenseSplitShares.groupBy { it.splitId }

                data.expenseSplits.forEach { split ->
                    val mappedTxId = transactionIdMapping[split.transactionId] ?: split.transactionId
                    val mappedGroupId = groupIdMapping[split.groupId] ?: split.groupId
                    val mappedPayerId = memberIdMapping[split.payerMemberId] ?: split.payerMemberId

                    // Avoid duplicate split for same transaction & group
                    val alreadyHasSplit = existingSplits.any {
                        it.split.transactionId == mappedTxId && it.split.groupId == mappedGroupId
                    }
                    if (alreadyHasSplit) {
                        return@forEach
                    }

                    val targetSplitId = if (existingSplitById.containsKey(split.id)) UUID.randomUUID().toString() else split.id
                    splitIdMapping[split.id] = targetSplitId

                    val newSplit = split.copy(
                        id = targetSplitId,
                        transactionId = mappedTxId,
                        groupId = mappedGroupId,
                        payerMemberId = mappedPayerId
                    )
                    db.expenseSplitDao().insertSplit(newSplit)

                    val shares = sharesBySplit[split.id] ?: emptyList()
                    val newShares = shares.map { share ->
                        val targetShareId = UUID.randomUUID().toString()
                        shareIdMapping[share.id] = targetShareId
                        share.copy(
                            id = targetShareId,
                            splitId = targetSplitId,
                            memberId = memberIdMapping[share.memberId] ?: share.memberId
                        )
                    }
                    if (newShares.isNotEmpty()) {
                        db.expenseSplitDao().insertShares(newShares)
                    }
                }

                // 7. Repayments & Allocations
                val existingRepayments = db.repaymentDao().getAllRepaymentsWithAllocationsSync()
                val existingRepById = existingRepayments.associateBy { it.repayment.id }
                val allocationsByRep = data.repaymentAllocations.groupBy { it.repaymentId }

                data.repayments.forEach { rep ->
                    val mappedGroupId = groupIdMapping[rep.groupId] ?: rep.groupId
                    val mappedMemberId = memberIdMapping[rep.memberId] ?: rep.memberId
                    val mappedLinkedTxId = rep.linkedTransactionId?.let { transactionIdMapping[it] ?: it }

                    // If repayment with same linked transaction exists, skip duplicate
                    if (mappedLinkedTxId != null && existingRepayments.any { it.repayment.linkedTransactionId == mappedLinkedTxId }) {
                        duplicatesSkipped++
                        return@forEach
                    }

                    val targetRepId = if (existingRepById.containsKey(rep.id)) UUID.randomUUID().toString() else rep.id

                    val newRep = rep.copy(
                        id = targetRepId,
                        groupId = mappedGroupId,
                        memberId = mappedMemberId,
                        linkedTransactionId = mappedLinkedTxId
                    )
                    db.repaymentDao().insertRepayment(newRep)
                    repaymentsAdded++

                    val allocs = allocationsByRep[rep.id] ?: emptyList()
                    val newAllocs = allocs.mapNotNull { alloc ->
                        val mappedShareId = shareIdMapping[alloc.shareId] ?: alloc.shareId
                        // Only insert allocation if share exists
                        alloc.copy(
                            id = UUID.randomUUID().toString(),
                            repaymentId = targetRepId,
                            shareId = mappedShareId
                        )
                    }
                    if (newAllocs.isNotEmpty()) {
                        db.repaymentDao().insertAllocations(newAllocs)
                    }
                }

                // 8. Recurring Expenses
                val existingRecurring = db.recurringExpenseDao().getAllRecurringExpensesSync()
                val existingRecurringById = existingRecurring.associateBy { it.id }
                data.recurringExpenses.forEach { rec ->
                    if (!existingRecurringById.containsKey(rec.id)) {
                        db.recurringExpenseDao().insertRecurringExpense(rec)
                    }
                }

                // 9. Payment Templates
                val existingTemplates = db.paymentTemplateDao().getAllTemplatesSync()
                val existingTemplateById = existingTemplates.associateBy { it.id }
                data.paymentTemplates.forEach { tpl ->
                    if (!existingTemplateById.containsKey(tpl.id)) {
                        db.paymentTemplateDao().insert(tpl)
                    }
                }

                // 10. Balance Observations & Source Mappings
                val existingObs = db.balanceObservationDao().getAllObservationsSync()
                val existingObsById = existingObs.associateBy { it.id }
                data.balanceObservations.forEach { obs ->
                    if (!existingObsById.containsKey(obs.id)) {
                        val mappedAccId = obs.accountId?.let { accountIdMapping[it] ?: it }
                        db.balanceObservationDao().insert(obs.copy(accountId = mappedAccId))
                    }
                }

                val existingSourceMappings = db.sourceAccountMappingDao().getAllMappingsSync()
                val existingMappingById = existingSourceMappings.associateBy { it.id }
                data.sourceAccountMappings.forEach { sam ->
                    if (!existingMappingById.containsKey(sam.id)) {
                        val mappedAccId = accountIdMapping[sam.accountId] ?: sam.accountId
                        db.sourceAccountMappingDao().insert(sam.copy(accountId = mappedAccId))
                    }
                }
            }

            ImportResult(
                mode = ImportMode.MERGE,
                accountsImported = accountsAdded,
                transactionsImported = transactionsAdded,
                categoriesImported = categoriesAdded,
                splitGroupsImported = splitGroupsAdded,
                repaymentsImported = repaymentsAdded,
                duplicatesSkipped = duplicatesSkipped,
                safetyBackupFile = null
            )
        }
    }

    private fun normalizeCategoryKey(value: String): String =
        value.trim().lowercase(Locale.ROOT)

    private fun resolveImportedCategoryName(
        categoryValue: String,
        sourceCategories: List<CategoryEntity>,
        existingCategories: List<CategoryEntity>,
        categoryNamesByKey: MutableMap<String, String>,
        insertCategory: (CategoryEntity) -> Unit
    ): String {
        val sourceCategory = sourceCategories.firstOrNull { it.id == categoryValue }
        val requestedName = sourceCategory?.name?.trim() ?: categoryValue.trim()
        if (requestedName.isBlank()) {
            throw BackupValidationException("Imported transaction has a blank category")
        }

        val sourceKey = sourceCategory?.id
        val mappedName = sourceKey?.let { categoryNamesByKey[it] }
        if (mappedName != null) return mappedName

        val matchingCategories = existingCategories.filter {
            normalizeCategoryKey(it.name) == normalizeCategoryKey(requestedName)
        }
        if (matchingCategories.size > 1) {
            throw BackupValidationException("Ambiguous imported category '$requestedName'")
        }
        val existing = matchingCategories.singleOrNull()
        if (existing != null) {
            sourceKey?.let { categoryNamesByKey[it] = existing.name }
            return existing.name
        }

        val created = CategoryEntity(id = UUID.randomUUID().toString(), name = requestedName)
        insertCategory(created)
        sourceKey?.let { categoryNamesByKey[it] = created.name }
        return created.name
    }
}
