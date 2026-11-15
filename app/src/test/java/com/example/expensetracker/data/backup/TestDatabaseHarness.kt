package com.example.expensetracker.data.backup

import androidx.room.DatabaseConfiguration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.expensetracker.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.Callable

class TestDatabaseHarness : AppDatabase() {

    val accountsTable = mutableListOf<AccountEntity>()
    val categoriesTable = mutableListOf<CategoryEntity>()
    val categoryMappingsTable = mutableListOf<CategoryMappingEntity>()
    val transactionsTable = mutableListOf<TransactionEntity>()
    val splitGroupsTable = mutableListOf<SplitGroupEntity>()
    val splitGroupMembersTable = mutableListOf<SplitGroupMemberEntity>()
    val expenseSplitsTable = mutableListOf<ExpenseSplitEntity>()
    val expenseSplitSharesTable = mutableListOf<ExpenseSplitShareEntity>()
    val repaymentsTable = mutableListOf<RepaymentEntity>()
    val repaymentAllocationsTable = mutableListOf<RepaymentAllocationEntity>()
    val recurringExpensesTable = mutableListOf<RecurringExpenseEntity>()
    val paymentTemplatesTable = mutableListOf<PaymentTemplateEntity>()
    val balanceObservationsTable = mutableListOf<BalanceObservationEntity>()
    val sourceAccountMappingsTable = mutableListOf<SourceAccountMappingEntity>()
    val gPayRequestsTable = mutableListOf<GPayRequestEntity>()
    val gPayMemberMappingsTable = mutableListOf<GPayMemberMappingEntity>()

    var enforceForeignKeys = true

    data class DbSnapshot(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val categoryMappings: List<CategoryMappingEntity>,
        val transactions: List<TransactionEntity>,
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

    private fun takeSnapshot(): DbSnapshot {
        return DbSnapshot(
            accounts = accountsTable.toList(),
            categories = categoriesTable.toList(),
            categoryMappings = categoryMappingsTable.toList(),
            transactions = transactionsTable.toList(),
            splitGroups = splitGroupsTable.toList(),
            splitGroupMembers = splitGroupMembersTable.toList(),
            expenseSplits = expenseSplitsTable.toList(),
            expenseSplitShares = expenseSplitSharesTable.toList(),
            repayments = repaymentsTable.toList(),
            repaymentAllocations = repaymentAllocationsTable.toList(),
            recurringExpenses = recurringExpensesTable.toList(),
            paymentTemplates = paymentTemplatesTable.toList(),
            balanceObservations = balanceObservationsTable.toList(),
            sourceAccountMappings = sourceAccountMappingsTable.toList()
        )
    }

    private fun restoreSnapshot(s: DbSnapshot) {
        accountsTable.clear(); accountsTable.addAll(s.accounts)
        categoriesTable.clear(); categoriesTable.addAll(s.categories)
        categoryMappingsTable.clear(); categoryMappingsTable.addAll(s.categoryMappings)
        transactionsTable.clear(); transactionsTable.addAll(s.transactions)
        splitGroupsTable.clear(); splitGroupsTable.addAll(s.splitGroups)
        splitGroupMembersTable.clear(); splitGroupMembersTable.addAll(s.splitGroupMembers)
        expenseSplitsTable.clear(); expenseSplitsTable.addAll(s.expenseSplits)
        expenseSplitSharesTable.clear(); expenseSplitSharesTable.addAll(s.expenseSplitShares)
        repaymentsTable.clear(); repaymentsTable.addAll(s.repayments)
        repaymentAllocationsTable.clear(); repaymentAllocationsTable.addAll(s.repaymentAllocations)
        recurringExpensesTable.clear(); recurringExpensesTable.addAll(s.recurringExpenses)
        paymentTemplatesTable.clear(); paymentTemplatesTable.addAll(s.paymentTemplates)
        balanceObservationsTable.clear(); balanceObservationsTable.addAll(s.balanceObservations)
        sourceAccountMappingsTable.clear(); sourceAccountMappingsTable.addAll(s.sourceAccountMappings)
    }

    override fun <V> runInTransaction(callable: Callable<V>): V {
        val snapshot = takeSnapshot()
        return try {
            callable.call()
        } catch (e: Throwable) {
            restoreSnapshot(snapshot)
            throw e
        }
    }

    override fun runInTransaction(body: Runnable) {
        val snapshot = takeSnapshot()
        try {
            body.run()
        } catch (e: Throwable) {
            restoreSnapshot(snapshot)
            throw e
        }
    }

    private val fakeDatabase = object : SupportSQLiteDatabase {
        override fun execSQL(sql: String) {
            val upper = sql.trim().uppercase()
            when {
                upper.contains("DELETE FROM REPAYMENT_ALLOCATIONS") -> repaymentAllocationsTable.clear()
                upper.contains("DELETE FROM REPAYMENTS") -> repaymentsTable.clear()
                upper.contains("DELETE FROM EXPENSE_SPLIT_SHARES") -> expenseSplitSharesTable.clear()
                upper.contains("DELETE FROM EXPENSE_SPLITS") -> expenseSplitsTable.clear()
                upper.contains("DELETE FROM SPLIT_GROUP_MEMBERS") -> splitGroupMembersTable.clear()
                upper.contains("DELETE FROM SPLIT_GROUPS") -> splitGroupsTable.clear()
                upper.contains("DELETE FROM BALANCE_OBSERVATIONS") -> balanceObservationsTable.clear()
                upper.contains("DELETE FROM SOURCE_ACCOUNT_MAPPINGS") -> sourceAccountMappingsTable.clear()
                upper.contains("DELETE FROM TRANSACTIONS") -> transactionsTable.clear()
                upper.contains("DELETE FROM PAYMENT_TEMPLATES") -> paymentTemplatesTable.clear()
                upper.contains("DELETE FROM RECURRING_EXPENSES") -> recurringExpensesTable.clear()
                upper.contains("DELETE FROM CATEGORY_MAPPINGS") -> categoryMappingsTable.clear()
                upper.contains("DELETE FROM CATEGORIES") -> categoriesTable.clear()
                upper.contains("DELETE FROM ACCOUNTS") -> accountsTable.clear()
            }
        }
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) { execSQL(sql) }
        override fun close() {}
        override fun compileStatement(sql: String) = throw UnsupportedOperationException()
        override fun beginTransaction() {}
        override fun beginTransactionNonExclusive() {}
        override fun beginTransactionWithListener(transactionListener: android.database.sqlite.SQLiteTransactionListener) {}
        override fun beginTransactionWithListenerNonExclusive(transactionListener: android.database.sqlite.SQLiteTransactionListener) {}
        override fun endTransaction() {}
        override fun setTransactionSuccessful() {}
        override fun inTransaction(): Boolean = false
        override val isDbLockedByCurrentThread: Boolean get() = false
        override fun yieldIfContendedSafely() = false
        override fun yieldIfContendedSafely(sleepAmount: Long) = false
        override var version: Int
            get() = 12
            set(value) {}
        override val maximumSize: Long get() = 0L
        override fun setMaximumSize(numBytes: Long) = 0L
        override var pageSize: Long
            get() = 0L
            set(value) {}
        override fun query(query: String) = throw UnsupportedOperationException()
        override fun query(query: String, bindArgs: Array<out Any?>) = throw UnsupportedOperationException()
        override fun query(query: SupportSQLiteQuery) = throw UnsupportedOperationException()
        override fun query(query: SupportSQLiteQuery, cancellationSignal: android.os.CancellationSignal?) = throw UnsupportedOperationException()
        override fun insert(table: String, conflictAlgorithm: Int, values: android.content.ContentValues) = 0L
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?) = 0
        override fun update(table: String, conflictAlgorithm: Int, values: android.content.ContentValues, whereClause: String?, whereArgs: Array<out Any?>?) = 0
        override val isReadOnly: Boolean get() = false
        override val isOpen: Boolean get() = true
        override fun needUpgrade(newVersion: Int) = false
        override val path: String? get() = ":memory:"
        override fun setLocale(locale: java.util.Locale) {}
        override fun setMaxSqlCacheSize(cacheSize: Int) {}
        override fun setForeignKeyConstraintsEnabled(enable: Boolean) {}
        override fun enableWriteAheadLogging() = false
        override fun disableWriteAheadLogging() {}
        override val isWriteAheadLoggingEnabled: Boolean get() = false
        override val attachedDbs: List<android.util.Pair<String, String>>? get() = emptyList()
        override val isDatabaseIntegrityOk: Boolean get() = true
    }

    private val fakeOpenHelper = object : SupportSQLiteOpenHelper {
        override val databaseName: String? get() = ":memory:"
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {}
        override val writableDatabase: SupportSQLiteDatabase get() = fakeDatabase
        override val readableDatabase: SupportSQLiteDatabase get() = fakeDatabase
        override fun close() {}
    }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = fakeOpenHelper

    // --- DAOs ---
    override fun accountDao(): AccountDao = object : AccountDao {
        override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(accountsTable.toList())
        override fun getAllAccountsSync(): List<AccountEntity> = accountsTable.toList()
        override fun getActiveAccounts(): Flow<List<AccountEntity>> = flowOf(accountsTable.filter { !it.isArchived })
        override fun getActiveAccountsSync(): List<AccountEntity> = accountsTable.filter { !it.isArchived }
        override fun getAccountByIdSync(id: String): AccountEntity? = accountsTable.find { it.id == id }
        override fun getPrimaryAccountSync(): AccountEntity? = accountsTable.find { it.isPrimary && !it.isArchived }
        override fun getPrimaryAccountFlow(): Flow<AccountEntity?> = flowOf(getPrimaryAccountSync())
        override fun getActiveAccountCountSync(): Int = accountsTable.count { !it.isArchived }
        override fun insertAccount(account: AccountEntity) {
            val existing = accountsTable.find { it.id == account.id }
            if (existing != null) {
                throw IllegalStateException("Account with ID '${account.id}' already exists")
            }
            accountsTable.add(account)
        }
        override fun updateAccount(account: AccountEntity) {
            accountsTable.removeAll { it.id == account.id }
            accountsTable.add(account)
        }
        override fun deleteAccountById(id: String) { accountsTable.removeAll { it.id == id } }
        override fun clearPrimaryFlag() {
            val updated = accountsTable.map { it.copy(isPrimary = false) }
            accountsTable.clear(); accountsTable.addAll(updated)
        }
        override fun setPrimaryFlag(accountId: String) {
            val updated = accountsTable.map { if (it.id == accountId) it.copy(isPrimary = true) else it }
            accountsTable.clear(); accountsTable.addAll(updated)
        }
        override fun setArchivedStatus(accountId: String, isArchived: Boolean, updatedAt: Long) {
            val updated = accountsTable.map { if (it.id == accountId) it.copy(isArchived = isArchived, updatedAt = updatedAt) else it }
            accountsTable.clear(); accountsTable.addAll(updated)
        }
    }

    override fun categoryDao(): CategoryDao = object : CategoryDao {
        override fun getAllCategories(): Flow<List<CategoryEntity>> = flowOf(categoriesTable.toList())
        override fun getAllCategoriesSync(): List<CategoryEntity> = categoriesTable.toList()
        override fun insertCategory(category: CategoryEntity) {
            categoriesTable.removeAll { it.id == category.id }
            categoriesTable.add(category)
        }
        override fun updateCategory(category: CategoryEntity) { insertCategory(category) }
        override fun deleteCategory(category: CategoryEntity) { deleteCategoryById(category.id) }
        override fun deleteCategoryById(id: String) { categoriesTable.removeAll { it.id == id } }
    }

    override fun categoryMappingDao(): CategoryMappingDao = object : CategoryMappingDao {
        override fun getAllMappings(): Flow<List<CategoryMappingEntity>> = flowOf(categoryMappingsTable.toList())
        override fun getAllMappingsSync(): List<CategoryMappingEntity> = categoryMappingsTable.toList()
        override fun getMappingByPatternSync(pattern: String): CategoryMappingEntity? = categoryMappingsTable.firstOrNull { it.pattern == pattern }
        override fun insertMapping(mapping: CategoryMappingEntity) {
            categoryMappingsTable.removeAll { it.id == mapping.id }
            categoryMappingsTable.add(mapping)
        }
        override fun updateMapping(mapping: CategoryMappingEntity) { insertMapping(mapping) }
        override fun deleteMapping(mapping: CategoryMappingEntity) { categoryMappingsTable.removeAll { it.id == mapping.id } }
        override fun updateCategoryForMappings(oldCategory: String, newCategory: String) {
            val updated = categoryMappingsTable.map {
                if (it.categoryName.equals(oldCategory, ignoreCase = true)) it.copy(categoryName = newCategory) else it
            }
            categoryMappingsTable.clear(); categoryMappingsTable.addAll(updated)
        }
    }

    override fun transactionDao(): TransactionDao = object : TransactionDao {
        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flowOf(transactionsTable.toList())
        override fun getAllTransactionsSync(): List<TransactionEntity> = transactionsTable.toList()
        override fun getTransactionsInWindow(minTime: Long, maxTime: Long): List<TransactionEntity> =
            transactionsTable.filter { it.timestamp in minTime..maxTime }
        override fun getTransactionByIdSync(id: String): TransactionEntity? = transactionsTable.find { it.id == id }
        override fun getTransactionByReferenceSync(referenceNumber: String): TransactionEntity? =
            transactionsTable.find { it.referenceNumber == referenceNumber }
        override fun insertTransaction(transaction: TransactionEntity): Long {
            if (enforceForeignKeys && transaction.accountId != null) {
                if (accountsTable.none { it.id == transaction.accountId }) {
                    throw IllegalStateException("Foreign key violation: Account '${transaction.accountId}' does not exist")
                }
            }
            if (transactionsTable.any { it.id == transaction.id }) {
                return -1L // IGNORE conflict
            }
            if (!transaction.referenceNumber.isNullOrBlank() && transactionsTable.any { it.referenceNumber == transaction.referenceNumber }) {
                return -1L // UNIQUE constraint
            }
            transactionsTable.add(transaction)
            return transactionsTable.size.toLong()
        }
        override fun updateTransaction(transaction: TransactionEntity) {
            transactionsTable.removeAll { it.id == transaction.id }
            transactionsTable.add(transaction)
        }
        override fun deleteTransaction(transaction: TransactionEntity) { deleteTransactionById(transaction.id) }
        override fun deleteTransactionById(id: String) { transactionsTable.removeAll { it.id == id } }
        override fun updateCategoryForTransactions(oldCategory: String, newCategory: String) {
            val updated = transactionsTable.map {
                if (it.category.equals(oldCategory, ignoreCase = true)) it.copy(category = newCategory) else it
            }
            transactionsTable.clear(); transactionsTable.addAll(updated)
        }
        override fun updateAccountForTransaction(transactionId: String, accountId: String?) {
            val updated = transactionsTable.map { if (it.id == transactionId) it.copy(accountId = accountId) else it }
            transactionsTable.clear(); transactionsTable.addAll(updated)
        }
        override fun reassignTransactions(fromAccountId: String, toAccountId: String?) {
            val updated = transactionsTable.map { if (it.accountId == fromAccountId) it.copy(accountId = toAccountId) else it }
            transactionsTable.clear(); transactionsTable.addAll(updated)
        }
        override fun assignUnassignedTransactionsToAccount(accountId: String) {
            val updated = transactionsTable.map { if (it.accountId == null) it.copy(accountId = accountId) else it }
            transactionsTable.clear(); transactionsTable.addAll(updated)
        }
        override fun assignUnassignedTransactionIdsToAccount(transactionIds: List<String>, accountId: String): Int {
            var updatedCount = 0
            val updated = transactionsTable.map { transaction ->
                if (transaction.id in transactionIds && (transaction.accountId == null || accountsTable.none { it.id == transaction.accountId })) {
                    updatedCount++
                    transaction.copy(accountId = accountId)
                } else transaction
            }
            transactionsTable.clear(); transactionsTable.addAll(updated)
            return updatedCount
        }
        override fun updateCategoryForTransactionIds(transactionIds: List<String>, newCategory: String): Int {
            var updatedCount = 0
            val updated = transactionsTable.map { transaction ->
                if (transaction.id in transactionIds) {
                    updatedCount++
                    transaction.copy(category = newCategory)
                } else transaction
            }
            transactionsTable.clear(); transactionsTable.addAll(updated)
            return updatedCount
        }
        override fun getUnassignedTransactionsSync(): List<TransactionEntity> = transactionsTable.filter {
            it.accountId == null || accountsTable.none { account -> account.id == it.accountId }
        }
    }

    override fun splitGroupDao(): SplitGroupDao = object : SplitGroupDao {
        override fun getAllGroupsWithMembers(): Flow<List<SplitGroupWithMembers>> = flowOf(getAllGroupsWithMembersSync())
        override fun getAllGroupsWithMembersSync(): List<SplitGroupWithMembers> {
            return splitGroupsTable.map { group ->
                SplitGroupWithMembers(
                    group = group,
                    members = splitGroupMembersTable.filter { it.groupId == group.id }
                )
            }
        }
        override fun getGroupWithMembersByIdSync(id: String): SplitGroupWithMembers? {
            val grp = splitGroupsTable.find { it.id == id } ?: return null
            return SplitGroupWithMembers(grp, splitGroupMembersTable.filter { it.groupId == id })
        }
        override fun insertGroup(group: SplitGroupEntity) {
            splitGroupsTable.removeAll { it.id == group.id }
            splitGroupsTable.add(group)
        }
        override fun updateGroup(group: SplitGroupEntity) { insertGroup(group) }
        override fun insertMembers(members: List<SplitGroupMemberEntity>) {
            members.forEach { m ->
                if (enforceForeignKeys && splitGroupsTable.none { it.id == m.groupId }) {
                    throw IllegalStateException("Foreign key violation: SplitGroup '${m.groupId}' does not exist")
                }
                splitGroupMembersTable.removeAll { it.id == m.id }
                splitGroupMembersTable.add(m)
            }
        }
        override fun deleteMembersForGroup(groupId: String) { splitGroupMembersTable.removeAll { it.groupId == groupId } }
        override fun deleteGroup(group: SplitGroupEntity) { deleteGroupById(group.id) }
        override fun deleteGroupById(id: String) {
            splitGroupsTable.removeAll { it.id == id }
            splitGroupMembersTable.removeAll { it.groupId == id }
        }
    }

    override fun expenseSplitDao(): ExpenseSplitDao = object : ExpenseSplitDao {
        override fun getAllSplitsWithShares(): Flow<List<ExpenseSplitWithShares>> = flowOf(getAllSplitsWithSharesSync())
        override fun getAllSplitsWithSharesSync(): List<ExpenseSplitWithShares> {
            return expenseSplitsTable.map { split ->
                ExpenseSplitWithShares(
                    split = split,
                    shares = expenseSplitSharesTable.filter { it.splitId == split.id }
                )
            }
        }
        override fun getSplitForTransactionAndGroupSync(transactionId: String, groupId: String): ExpenseSplitWithShares? {
            val sp = expenseSplitsTable.find { it.transactionId == transactionId && it.groupId == groupId } ?: return null
            return ExpenseSplitWithShares(sp, expenseSplitSharesTable.filter { it.splitId == sp.id })
        }
        override fun getSplitForTransaction(transactionId: String): Flow<ExpenseSplitWithShares?> {
            val sp = expenseSplitsTable.find { it.transactionId == transactionId }
            return flowOf(sp?.let { ExpenseSplitWithShares(it, expenseSplitSharesTable.filter { s -> s.splitId == it.id }) })
        }
        override fun getSplitForTransactionSync(transactionId: String): ExpenseSplitWithShares? {
            val sp = expenseSplitsTable.find { it.transactionId == transactionId } ?: return null
            return ExpenseSplitWithShares(sp, expenseSplitSharesTable.filter { it.splitId == sp.id })
        }
        override fun getSplitsForGroupSync(groupId: String): List<ExpenseSplitWithShares> {
            return expenseSplitsTable.filter { it.groupId == groupId }.map { sp ->
                ExpenseSplitWithShares(sp, expenseSplitSharesTable.filter { it.splitId == sp.id })
            }
        }
        override fun insertSplit(split: ExpenseSplitEntity) {
            if (enforceForeignKeys) {
                if (transactionsTable.none { it.id == split.transactionId }) {
                    throw IllegalStateException("Foreign key violation: Transaction '${split.transactionId}' does not exist")
                }
                if (splitGroupsTable.none { it.id == split.groupId }) {
                    throw IllegalStateException("Foreign key violation: SplitGroup '${split.groupId}' does not exist")
                }
                if (splitGroupMembersTable.none { it.id == split.payerMemberId }) {
                    throw IllegalStateException("Foreign key violation: Member '${split.payerMemberId}' does not exist")
                }
            }
            expenseSplitsTable.removeAll { it.id == split.id }
            expenseSplitsTable.add(split)
        }
        override fun insertShares(shares: List<ExpenseSplitShareEntity>) {
            shares.forEach { sh ->
                if (enforceForeignKeys) {
                    if (expenseSplitsTable.none { it.id == sh.splitId }) {
                        throw IllegalStateException("Foreign key violation: Split '${sh.splitId}' does not exist")
                    }
                    if (splitGroupMembersTable.none { it.id == sh.memberId }) {
                        throw IllegalStateException("Foreign key violation: Member '${sh.memberId}' does not exist")
                    }
                }
                expenseSplitSharesTable.removeAll { it.id == sh.id }
                expenseSplitSharesTable.add(sh)
            }
        }
        override fun deleteSharesForSplit(splitId: String) { expenseSplitSharesTable.removeAll { it.splitId == splitId } }
        override fun deleteSplit(split: ExpenseSplitEntity) { deleteSplitById(split.id) }
        override fun deleteSplitById(id: String) {
            expenseSplitsTable.removeAll { it.id == id }
            expenseSplitSharesTable.removeAll { it.splitId == id }
        }
    }

    override fun repaymentDao(): RepaymentDao = object : RepaymentDao {
        override fun getAllRepaymentsWithAllocations(): Flow<List<RepaymentWithAllocations>> = flowOf(getAllRepaymentsWithAllocationsSync())
        override fun getAllRepaymentsWithAllocationsSync(): List<RepaymentWithAllocations> {
            return repaymentsTable.map { rep ->
                RepaymentWithAllocations(
                    repayment = rep,
                    allocations = repaymentAllocationsTable.filter { it.repaymentId == rep.id }
                )
            }
        }
        override fun getRepaymentsForMemberAndGroupSync(memberId: String, groupId: String): List<RepaymentWithAllocations> {
            return repaymentsTable.filter { it.memberId == memberId && it.groupId == groupId }.map { rep ->
                RepaymentWithAllocations(rep, repaymentAllocationsTable.filter { it.repaymentId == rep.id })
            }
        }
        override fun getRepaymentForLinkedTransactionSync(transactionId: String): RepaymentEntity? =
            repaymentsTable.find { it.linkedTransactionId == transactionId }
        override fun isTransactionLinkedToRepaymentSync(transactionId: String): Int =
            repaymentsTable.count { it.linkedTransactionId == transactionId }
        override fun countAllocationsForSplitSync(splitId: String): Int = repaymentAllocationsTable.count { allocation ->
            expenseSplitSharesTable.any { share -> share.id == allocation.shareId && share.splitId == splitId }
        }
        override fun insertRepayment(repayment: RepaymentEntity) {
            if (enforceForeignKeys) {
                if (splitGroupsTable.none { it.id == repayment.groupId }) {
                    throw IllegalStateException("Foreign key violation: SplitGroup '${repayment.groupId}' does not exist")
                }
                if (splitGroupMembersTable.none { it.id == repayment.memberId }) {
                    throw IllegalStateException("Foreign key violation: Member '${repayment.memberId}' does not exist")
                }
            }
            repaymentsTable.removeAll { it.id == repayment.id }
            repaymentsTable.add(repayment)
        }
        override fun insertAllocations(allocations: List<RepaymentAllocationEntity>) {
            allocations.forEach { alloc ->
                if (enforceForeignKeys) {
                    if (repaymentsTable.none { it.id == alloc.repaymentId }) {
                        throw IllegalStateException("Foreign key violation: Repayment '${alloc.repaymentId}' does not exist")
                    }
                    if (expenseSplitSharesTable.none { it.id == alloc.shareId }) {
                        throw IllegalStateException("Foreign key violation: Share '${alloc.shareId}' does not exist")
                    }
                }
                repaymentAllocationsTable.removeAll { it.id == alloc.id }
                repaymentAllocationsTable.add(alloc)
            }
        }
        override fun deleteRepayment(repayment: RepaymentEntity) { deleteRepaymentById(repayment.id) }
        override fun deleteRepaymentById(id: String) {
            repaymentsTable.removeAll { it.id == id }
            repaymentAllocationsTable.removeAll { it.repaymentId == id }
        }
    }

    override fun recurringExpenseDao(): RecurringExpenseDao = object : RecurringExpenseDao {
        override fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>> = flowOf(recurringExpensesTable.toList())
        override fun getAllRecurringExpensesSync(): List<RecurringExpenseEntity> = recurringExpensesTable.toList()
        override fun insertRecurringExpense(entity: RecurringExpenseEntity) {
            recurringExpensesTable.removeAll { it.id == entity.id }
            recurringExpensesTable.add(entity)
        }
        override fun updateRecurringExpense(entity: RecurringExpenseEntity) { insertRecurringExpense(entity) }
        override fun deleteRecurringExpense(entity: RecurringExpenseEntity) { recurringExpensesTable.removeAll { it.id == entity.id } }
    }

    override fun paymentTemplateDao(): PaymentTemplateDao = object : PaymentTemplateDao {
        override fun getAllTemplatesFlow(): Flow<List<PaymentTemplateEntity>> = flowOf(paymentTemplatesTable.toList())
        override fun getAllTemplatesSync(): List<PaymentTemplateEntity> = paymentTemplatesTable.toList()
        override fun getEnabledTemplatesSync(): List<PaymentTemplateEntity> = paymentTemplatesTable.filter { it.enabled }
        override fun getTemplateById(id: String): PaymentTemplateEntity? = paymentTemplatesTable.find { it.id == id }
        override fun insert(template: PaymentTemplateEntity) {
            paymentTemplatesTable.removeAll { it.id == template.id }
            paymentTemplatesTable.add(template)
        }
        override fun update(template: PaymentTemplateEntity) { insert(template) }
        override fun delete(template: PaymentTemplateEntity) { deleteById(template.id) }
        override fun deleteById(id: String) { paymentTemplatesTable.removeAll { it.id == id } }
    }

    override fun balanceObservationDao(): BalanceObservationDao = object : BalanceObservationDao {
        override fun getAllObservationsFlow(): Flow<List<BalanceObservationEntity>> = flowOf(balanceObservationsTable.toList())
        override fun getAllObservations(): Flow<List<BalanceObservationEntity>> = flowOf(balanceObservationsTable.toList())
        override fun getAllObservationsSync(): List<BalanceObservationEntity> = balanceObservationsTable.toList()
        override fun getUnresolvedObservationsFlow(): Flow<List<BalanceObservationEntity>> = flowOf(balanceObservationsTable.filter { !it.isResolved })
        override fun getUnresolvedObservations(): Flow<List<BalanceObservationEntity>> = flowOf(balanceObservationsTable.filter { !it.isResolved })
        override fun getUnresolvedObservationsSync(): List<BalanceObservationEntity> = balanceObservationsTable.filter { !it.isResolved }
        override fun getObservationsForAccountSync(accountId: String): List<BalanceObservationEntity> = balanceObservationsTable.filter { it.accountId == accountId }
        override fun getObservationsForAccountFlow(accountId: String): Flow<List<BalanceObservationEntity>> = flowOf(getObservationsForAccountSync(accountId))
        override fun getObservationByIdSync(id: String): BalanceObservationEntity? = balanceObservationsTable.find { it.id == id }
        override fun getObservationById(id: String): BalanceObservationEntity? = balanceObservationsTable.find { it.id == id }
        override fun findDuplicateByReference(referenceNumber: String, balancePaise: Long): BalanceObservationEntity? =
            balanceObservationsTable.find { it.referenceNumber == referenceNumber && it.balancePaise == balancePaise }
        override fun findDuplicateByReferenceSync(referenceNumber: String, balancePaise: Long): BalanceObservationEntity? =
            balanceObservationsTable.find { it.referenceNumber == referenceNumber && it.balancePaise == balancePaise }
        override fun findDuplicateByTimestampSync(source: String, balancePaise: Long, windowStart: Long, windowEnd: Long): BalanceObservationEntity? =
            balanceObservationsTable.find { it.source == source && it.balancePaise == balancePaise && it.timestamp in windowStart..windowEnd }
        override fun insert(observation: BalanceObservationEntity): Long {
            balanceObservationsTable.removeAll { it.id == observation.id }
            balanceObservationsTable.add(observation)
            return balanceObservationsTable.size.toLong()
        }
        override fun update(observation: BalanceObservationEntity) { insert(observation) }
        override fun delete(observation: BalanceObservationEntity) { deleteById(observation.id) }
        override fun deleteById(id: String) { balanceObservationsTable.removeAll { it.id == id } }
        override fun resolveObservation(id: String, accountId: String) {
            val updated = balanceObservationsTable.map { if (it.id == id) it.copy(accountId = accountId, isResolved = true) else it }
            balanceObservationsTable.clear(); balanceObservationsTable.addAll(updated)
        }
        override fun markReconciled(id: String, reconciledAt: Long) {
            val updated = balanceObservationsTable.map { if (it.id == id) it.copy(isReconciled = true, reconciledAt = reconciledAt) else it }
            balanceObservationsTable.clear(); balanceObservationsTable.addAll(updated)
        }
    }

    override fun sourceAccountMappingDao(): SourceAccountMappingDao = object : SourceAccountMappingDao {
        override fun getAllMappingsFlow(): Flow<List<SourceAccountMappingEntity>> = flowOf(sourceAccountMappingsTable.toList())
        override fun getAllMappings(): Flow<List<SourceAccountMappingEntity>> = flowOf(sourceAccountMappingsTable.toList())
        override fun getAllMappingsSync(): List<SourceAccountMappingEntity> = sourceAccountMappingsTable.toList()
        override fun getConfirmedMappingsSync(): List<SourceAccountMappingEntity> = sourceAccountMappingsTable.filter { it.isConfirmed }
        override fun getMappingsForAccountSync(accountId: String): List<SourceAccountMappingEntity> = sourceAccountMappingsTable.filter { it.accountId == accountId }
        override fun insertMapping(mapping: SourceAccountMappingEntity) {
            sourceAccountMappingsTable.removeAll { it.id == mapping.id }
            sourceAccountMappingsTable.add(mapping)
        }
        override fun insert(mapping: SourceAccountMappingEntity) { insertMapping(mapping) }
        override fun updateMapping(mapping: SourceAccountMappingEntity) { insertMapping(mapping) }
        override fun deleteMapping(mapping: SourceAccountMappingEntity) { deleteById(mapping.id) }
        override fun deleteMappingById(id: String) { deleteById(id) }
        override fun deleteById(id: String) { sourceAccountMappingsTable.removeAll { it.id == id } }
    }

    override fun testNotificationDao(): TestNotificationDao = object : TestNotificationDao {
        override fun getAllTestNotifications(): Flow<List<TestNotificationEntity>> = flowOf(emptyList())
        override fun getAllTestNotificationsSync(): List<TestNotificationEntity> = emptyList()
        override fun insert(entity: TestNotificationEntity) {}
        override fun clearAll() {}
        override fun pruneOldRecords() {}
    }

    override fun gPayRequestDao(): GPayRequestDao = object : GPayRequestDao {
        override fun getAllRequests(): Flow<List<GPayRequestEntity>> = flowOf(gPayRequestsTable.toList())
        override fun getAllRequestsSync(): List<GPayRequestEntity> = gPayRequestsTable.toList()
        override fun findByRequestKeySync(requestKey: String): GPayRequestEntity? = gPayRequestsTable.firstOrNull { it.requestKey == requestKey }
        override fun getByIdSync(id: String): GPayRequestEntity? = gPayRequestsTable.firstOrNull { it.id == id }
        override fun upsert(entity: GPayRequestEntity) {
            gPayRequestsTable.removeAll { it.id == entity.id }
            gPayRequestsTable.add(entity)
        }
        override fun deleteById(id: String) { gPayRequestsTable.removeAll { it.id == id } }
    }

    override fun gPayMemberMappingDao(): GPayMemberMappingDao = object : GPayMemberMappingDao {
        override fun getAllMappings(): Flow<List<GPayMemberMappingEntity>> = flowOf(gPayMemberMappingsTable.toList())
        override fun getAllMappingsSync(): List<GPayMemberMappingEntity> = gPayMemberMappingsTable.toList()
        override fun upsert(entity: GPayMemberMappingEntity) {
            gPayMemberMappingsTable.removeAll { it.id == entity.id }
            gPayMemberMappingsTable.add(entity)
        }
        override fun deleteById(id: String) { gPayMemberMappingsTable.removeAll { it.id == id } }
    }

    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return androidx.room.InvalidationTracker(this, "accounts", "categories", "transactions")
    }

    override fun clearAllTables() {
        accountsTable.clear()
        categoriesTable.clear()
        categoryMappingsTable.clear()
        transactionsTable.clear()
        splitGroupsTable.clear()
        splitGroupMembersTable.clear()
        expenseSplitsTable.clear()
        expenseSplitSharesTable.clear()
        repaymentsTable.clear()
        repaymentAllocationsTable.clear()
        recurringExpensesTable.clear()
        paymentTemplatesTable.clear()
        balanceObservationsTable.clear()
        sourceAccountMappingsTable.clear()
        gPayRequestsTable.clear()
        gPayMemberMappingsTable.clear()
    }
}
