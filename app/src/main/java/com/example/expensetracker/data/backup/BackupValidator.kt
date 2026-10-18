package com.example.expensetracker.data.backup

import com.example.expensetracker.data.local.AppDatabase

object BackupValidator {

    fun validateBackup(backup: ExpenseTrackerBackup): BackupValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // 1. Format & Version Check
        if (backup.format != BACKUP_FORMAT_IDENTIFIER) {
            errors.add(
                ValidationError(
                    entity = "BackupHeader",
                    recordId = null,
                    message = "Invalid format identifier: '${backup.format}'. Expected '$BACKUP_FORMAT_IDENTIFIER'"
                )
            )
        }

        if (backup.schemaVersion < 1) {
            errors.add(
                ValidationError(
                    entity = "BackupHeader",
                    recordId = null,
                    message = "Invalid schema version: ${backup.schemaVersion}"
                )
            )
        } else if (backup.schemaVersion > CURRENT_BACKUP_SCHEMA_VERSION) {
            errors.add(
                ValidationError(
                    entity = "BackupHeader",
                    recordId = null,
                    message = "Unsupported schema version ${backup.schemaVersion}. Highest supported version is $CURRENT_BACKUP_SCHEMA_VERSION"
                )
            )
        }

        if (backup.exportedAt.isBlank()) {
            warnings.add(
                ValidationWarning(
                    entity = "BackupHeader",
                    recordId = null,
                    message = "Missing exportedAt timestamp"
                )
            )
        }

        val data = backup.data

        // 2. ID Uniqueness Checks
        validateIdUniqueness("accounts", data.accounts.map { it.id }, errors)
        validateIdUniqueness("categories", data.categories.map { it.id }, errors)
        validateIdUniqueness("categoryMappings", data.categoryMappings.map { it.id }, errors)
        validateIdUniqueness("transactions", data.transactions.map { it.id }, errors)
        validateIdUniqueness("splitGroups", data.splitGroups.map { it.id }, errors)
        validateIdUniqueness("splitGroupMembers", data.splitGroupMembers.map { it.id }, errors)
        validateIdUniqueness("expenseSplits", data.expenseSplits.map { it.id }, errors)
        validateIdUniqueness("expenseSplitShares", data.expenseSplitShares.map { it.id }, errors)
        validateIdUniqueness("repayments", data.repayments.map { it.id }, errors)
        validateIdUniqueness("repaymentAllocations", data.repaymentAllocations.map { it.id }, errors)
        validateIdUniqueness("recurringExpenses", data.recurringExpenses.map { it.id }, errors)
        validateIdUniqueness("paymentTemplates", data.paymentTemplates.map { it.id }, errors)
        validateIdUniqueness("balanceObservations", data.balanceObservations.map { it.id }, errors)
        validateIdUniqueness("sourceAccountMappings", data.sourceAccountMappings.map { it.id }, errors)

        // Sets of available IDs for relationship verification
        val accountIds = data.accounts.map { it.id }.toSet()
        val transactionIds = data.transactions.map { it.id }.toSet()
        val splitGroupIds = data.splitGroups.map { it.id }.toSet()
        val memberIds = data.splitGroupMembers.map { it.id }.toSet()
        val splitIds = data.expenseSplits.map { it.id }.toSet()
        val shareIds = data.expenseSplitShares.map { it.id }.toSet()
        val repaymentIds = data.repayments.map { it.id }.toSet()

        // 3. Transactions Validation
        val validTypes = setOf("EXPENSE", "INCOME", "TRANSFER")
        data.transactions.forEach { tx ->
            if (tx.amount <= 0.0 && tx.amountPaise <= 0L) {
                errors.add(
                    ValidationError(
                        entity = "Transaction",
                        recordId = tx.id,
                        message = "Transaction '${tx.title}' has non-positive amount (${tx.amount})"
                    )
                )
            }
            if (tx.type.uppercase() !in validTypes) {
                errors.add(
                    ValidationError(
                        entity = "Transaction",
                        recordId = tx.id,
                        message = "Transaction '${tx.title}' has invalid transaction type '${tx.type}'"
                    )
                )
            }
            if (tx.accountId != null && tx.accountId !in accountIds) {
                errors.add(
                    ValidationError(
                        entity = "Transaction",
                        recordId = tx.id,
                        message = "Transaction references non-existent account ID '${tx.accountId}'"
                    )
                )
            }
        }

        // 4. Split Group Members FK
        data.splitGroupMembers.forEach { member ->
            if (member.groupId !in splitGroupIds) {
                errors.add(
                    ValidationError(
                        entity = "SplitGroupMember",
                        recordId = member.id,
                        message = "Member '${member.name}' references non-existent group ID '${member.groupId}'"
                    )
                )
            }
        }

        // 5. Expense Splits FK & Math
        data.expenseSplits.forEach { split ->
            if (split.groupId !in splitGroupIds) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplit",
                        recordId = split.id,
                        message = "Split references non-existent group ID '${split.groupId}'"
                    )
                )
            }
            if (split.payerMemberId !in memberIds) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplit",
                        recordId = split.id,
                        message = "Split references non-existent payer member ID '${split.payerMemberId}'"
                    )
                )
            }
            if (split.transactionId !in transactionIds) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplit",
                        recordId = split.id,
                        message = "Split references non-existent transaction ID '${split.transactionId}'"
                    )
                )
            }
            if (split.amountPaise <= 0L) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplit",
                        recordId = split.id,
                        message = "Split amount must be greater than zero"
                    )
                )
            }
        }

        // 6. Expense Split Shares FK & Math
        val sharesBySplit = data.expenseSplitShares.groupBy { it.splitId }
        data.expenseSplitShares.forEach { share ->
            if (share.splitId !in splitIds) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplitShare",
                        recordId = share.id,
                        message = "Share references non-existent split ID '${share.splitId}'"
                    )
                )
            }
            if (share.memberId !in memberIds) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplitShare",
                        recordId = share.id,
                        message = "Share references non-existent member ID '${share.memberId}'"
                    )
                )
            }
            if (share.shareAmountPaise <= 0L) {
                errors.add(
                    ValidationError(
                        entity = "ExpenseSplitShare",
                        recordId = share.id,
                        message = "Share amount must be greater than zero"
                    )
                )
            }
        }

        // Check split shares sum vs split amount
        data.expenseSplits.forEach { split ->
            val shares = sharesBySplit[split.id] ?: emptyList()
            if (shares.isEmpty()) {
                warnings.add(
                    ValidationWarning(
                        entity = "ExpenseSplit",
                        recordId = split.id,
                        message = "Split has no member shares defined"
                    )
                )
            } else {
                val sumShares = shares.sumOf { it.shareAmountPaise }
                if (sumShares != split.amountPaise) {
                    warnings.add(
                        ValidationWarning(
                            entity = "ExpenseSplit",
                            recordId = split.id,
                            message = "Split amount (${split.amountPaise} paise) does not equal sum of shares ($sumShares paise)"
                        )
                    )
                }
            }
        }

        // 7. Repayments FK & Math
        data.repayments.forEach { rep ->
            if (rep.groupId !in splitGroupIds) {
                errors.add(
                    ValidationError(
                        entity = "Repayment",
                        recordId = rep.id,
                        message = "Repayment references non-existent group ID '${rep.groupId}'"
                    )
                )
            }
            if (rep.memberId !in memberIds) {
                errors.add(
                    ValidationError(
                        entity = "Repayment",
                        recordId = rep.id,
                        message = "Repayment references non-existent member ID '${rep.memberId}'"
                    )
                )
            }
            if (rep.linkedTransactionId != null && rep.linkedTransactionId !in transactionIds) {
                warnings.add(
                    ValidationWarning(
                        entity = "Repayment",
                        recordId = rep.id,
                        message = "Repayment references non-existent linked transaction ID '${rep.linkedTransactionId}'"
                    )
                )
            }
            if (rep.amountPaise <= 0L) {
                errors.add(
                    ValidationError(
                        entity = "Repayment",
                        recordId = rep.id,
                        message = "Repayment amount must be positive"
                    )
                )
            }
        }

        // 8. Repayment Allocations FK
        val allocationsByRepayment = data.repaymentAllocations.groupBy { it.repaymentId }
        data.repaymentAllocations.forEach { alloc ->
            if (alloc.repaymentId !in repaymentIds) {
                errors.add(
                    ValidationError(
                        entity = "RepaymentAllocation",
                        recordId = alloc.id,
                        message = "Allocation references non-existent repayment ID '${alloc.repaymentId}'"
                    )
                )
            }
            if (alloc.shareId !in shareIds) {
                errors.add(
                    ValidationError(
                        entity = "RepaymentAllocation",
                        recordId = alloc.id,
                        message = "Allocation references non-existent share ID '${alloc.shareId}'"
                    )
                )
            }
            if (alloc.allocatedAmountPaise <= 0L) {
                errors.add(
                    ValidationError(
                        entity = "RepaymentAllocation",
                        recordId = alloc.id,
                        message = "Allocated amount must be positive"
                    )
                )
            }
        }

        data.repayments.forEach { rep ->
            val allocs = allocationsByRepayment[rep.id] ?: emptyList()
            val totalAllocated = allocs.sumOf { it.allocatedAmountPaise }
            if (totalAllocated + rep.unallocatedAmountPaise != rep.amountPaise) {
                warnings.add(
                    ValidationWarning(
                        entity = "Repayment",
                        recordId = rep.id,
                        message = "Repayment amount (${rep.amountPaise} paise) does not equal allocated ($totalAllocated paise) + unallocated (${rep.unallocatedAmountPaise} paise)"
                    )
                )
            }
        }

        // 9. Source Account Mappings & Balance Observations FK
        data.sourceAccountMappings.forEach { sam ->
            if (sam.accountId !in accountIds) {
                errors.add(
                    ValidationError(
                        entity = "SourceAccountMapping",
                        recordId = sam.id,
                        message = "SourceAccountMapping references non-existent account ID '${sam.accountId}'"
                    )
                )
            }
        }

        data.balanceObservations.forEach { obs ->
            if (obs.accountId != null && obs.accountId !in accountIds) {
                warnings.add(
                    ValidationWarning(
                        entity = "BalanceObservation",
                        recordId = obs.id,
                        message = "BalanceObservation references non-existent account ID '${obs.accountId}'"
                    )
                )
            }
        }

        return BackupValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            backup = backup
        )
    }

    fun generatePreviewSummary(
        backup: ExpenseTrackerBackup,
        db: AppDatabase
    ): ImportPreviewSummary {
        val validation = validateBackup(backup)
        val data = backup.data

        val existingAccounts = db.accountDao().getAllAccountsSync()
        val existingTransactions = db.transactionDao().getAllTransactionsSync()
        val existingSplitGroups = db.splitGroupDao().getAllGroupsWithMembersSync()

        val idConflicts = mutableListOf<IdConflict>()
        val duplicateCandidates = mutableListOf<DuplicateTransactionCandidate>()

        // 1. Check ID collisions with existing database
        val existingAccountIds = existingAccounts.associateBy { it.id }
        data.accounts.forEach { acc ->
            val existing = existingAccountIds[acc.id]
            if (existing != null) {
                idConflicts.add(
                    IdConflict(
                        entity = "Account",
                        id = acc.id,
                        existingNameOrTitle = existing.name,
                        incomingNameOrTitle = acc.name
                    )
                )
            }
        }

        val existingTxIds = existingTransactions.associateBy { it.id }
        val existingTxByRef = existingTransactions.filter { !it.referenceNumber.isNullOrBlank() }
            .associateBy { it.referenceNumber!! }

        data.transactions.forEach { tx ->
            val existing = existingTxIds[tx.id]
            if (existing != null) {
                idConflicts.add(
                    IdConflict(
                        entity = "Transaction",
                        id = tx.id,
                        existingNameOrTitle = "${existing.title} (₹${existing.amount})",
                        incomingNameOrTitle = "${tx.title} (₹${tx.amount})"
                    )
                )
            }

            // Check duplicate by reference number
            if (!tx.referenceNumber.isNullOrBlank()) {
                val refMatch = existingTxByRef[tx.referenceNumber]
                if (refMatch != null) {
                    duplicateCandidates.add(
                        DuplicateTransactionCandidate(
                            incomingTransaction = tx,
                            existingTransaction = refMatch,
                            reason = "Exact match on reference number '${tx.referenceNumber}'"
                        )
                    )
                }
            } else {
                // Check similar transactions (same amount, same date, same type, matching title)
                val similar = existingTransactions.find { ex ->
                    ex.date == tx.date &&
                    ex.type.equals(tx.type, ignoreCase = true) &&
                    Math.abs(ex.amount - tx.amount) < 0.001 &&
                    ex.title.trim().equals(tx.title.trim(), ignoreCase = true)
                }
                if (similar != null) {
                    duplicateCandidates.add(
                        DuplicateTransactionCandidate(
                            incomingTransaction = tx,
                            existingTransaction = similar,
                            reason = "Same title, amount, date and type"
                        )
                    )
                }
            }
        }

        val existingGroupIds = existingSplitGroups.associateBy { it.group.id }
        data.splitGroups.forEach { grp ->
            val existing = existingGroupIds[grp.id]
            if (existing != null) {
                idConflicts.add(
                    IdConflict(
                        entity = "SplitGroup",
                        id = grp.id,
                        existingNameOrTitle = existing.group.name,
                        incomingNameOrTitle = grp.name
                    )
                )
            }
        }

        val incomeCount = data.transactions.count { it.type.equals("INCOME", ignoreCase = true) }
        val expenseCount = data.transactions.count { it.type.equals("EXPENSE", ignoreCase = true) }
        val transferCount = data.transactions.count { it.type.equals("TRANSFER", ignoreCase = true) }
        val totalPaise = data.transactions.sumOf { it.amountPaise }

        return ImportPreviewSummary(
            backup = backup,
            accountsCount = data.accounts.size,
            categoriesCount = data.categories.size,
            transactionsCount = data.transactions.size,
            incomeCount = incomeCount,
            expenseCount = expenseCount,
            transferCount = transferCount,
            splitGroupsCount = data.splitGroups.size,
            splitGroupMembersCount = data.splitGroupMembers.size,
            expenseSplitsCount = data.expenseSplits.size,
            expenseSplitSharesCount = data.expenseSplitShares.size,
            repaymentsCount = data.repayments.size,
            repaymentAllocationsCount = data.repaymentAllocations.size,
            recurringExpensesCount = data.recurringExpenses.size,
            paymentTemplatesCount = data.paymentTemplates.size,
            balanceObservationsCount = data.balanceObservations.size,
            sourceAccountMappingsCount = data.sourceAccountMappings.size,
            totalAmountPaise = totalPaise,
            validationErrors = validation.errors,
            validationWarnings = validation.warnings,
            idConflicts = idConflicts,
            duplicateCandidates = duplicateCandidates,
            isRestoreSafe = validation.isValid
        )
    }

    private fun validateIdUniqueness(entityName: String, ids: List<String>, errors: MutableList<ValidationError>) {
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        for (id in ids) {
            if (!seen.add(id)) {
                duplicates.add(id)
            }
        }
        duplicates.forEach { dupId ->
            errors.add(
                ValidationError(
                    entity = entityName,
                    recordId = dupId,
                    message = "Duplicate ID '$dupId' found in $entityName"
                )
            )
        }
    }
}
