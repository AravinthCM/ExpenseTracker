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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class BackupValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupJsonSerializer {

    fun serialize(backup: ExpenseTrackerBackup): String {
        val root = JSONObject()
        root.put("format", backup.format)
        root.put("schemaVersion", backup.schemaVersion)
        root.put("exportedAt", backup.exportedAt)
        root.put("appVersion", backup.appVersion)
        root.put("databaseVersion", backup.databaseVersion)

        // Metadata
        val metaObj = JSONObject()
        metaObj.put("totalTransactions", backup.metadata.totalTransactions)
        metaObj.put("totalAccounts", backup.metadata.totalAccounts)
        metaObj.put("totalCategories", backup.metadata.totalCategories)
        metaObj.put("totalSplitGroups", backup.metadata.totalSplitGroups)
        metaObj.put("totalRepayments", backup.metadata.totalRepayments)
        val countsObj = JSONObject()
        backup.metadata.recordCounts.forEach { (k, v) -> countsObj.put(k, v) }
        metaObj.put("recordCounts", countsObj)
        root.put("metadata", metaObj)

        // Data Payload
        val dataObj = JSONObject()

        // 1. Accounts
        val accountsArr = JSONArray()
        backup.data.accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("openingBalancePaise", acc.openingBalancePaise)
            obj.put("isPrimary", acc.isPrimary)
            obj.put("isArchived", acc.isArchived)
            obj.put("createdAt", acc.createdAt)
            obj.put("updatedAt", acc.updatedAt)
            obj.putOptOrNull("accountNumber", acc.accountNumber)
            accountsArr.put(obj)
        }
        dataObj.put("accounts", accountsArr)

        // 2. Categories
        val categoriesArr = JSONArray()
        backup.data.categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            categoriesArr.put(obj)
        }
        dataObj.put("categories", categoriesArr)

        // 3. Category Mappings
        val mappingsArr = JSONArray()
        backup.data.categoryMappings.forEach { map ->
            val obj = JSONObject()
            obj.put("id", map.id)
            obj.put("pattern", map.pattern)
            obj.put("categoryName", map.categoryName)
            obj.put("priority", map.priority)
            mappingsArr.put(obj)
        }
        dataObj.put("categoryMappings", mappingsArr)

        // 4. Transactions
        val txArr = JSONArray()
        backup.data.transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("title", tx.title)
            obj.put("amount", tx.amount)
            obj.put("amountPaise", tx.amountPaise)
            obj.put("type", tx.type)
            obj.put("category", tx.category)
            obj.put("date", tx.date)
            obj.put("source", tx.source)
            obj.put("timestamp", tx.timestamp)
            obj.put("originalText", tx.originalText)
            obj.putOptOrNull("accountId", tx.accountId)
            obj.putOptOrNull("referenceNumber", tx.referenceNumber)
            txArr.put(obj)
        }
        dataObj.put("transactions", txArr)

        // 5. Split Groups
        val groupsArr = JSONArray()
        backup.data.splitGroups.forEach { grp ->
            val obj = JSONObject()
            obj.put("id", grp.id)
            obj.put("name", grp.name)
            obj.put("createdAt", grp.createdAt)
            obj.put("updatedAt", grp.updatedAt)
            groupsArr.put(obj)
        }
        dataObj.put("splitGroups", groupsArr)

        // 6. Split Group Members
        val membersArr = JSONArray()
        backup.data.splitGroupMembers.forEach { mem ->
            val obj = JSONObject()
            obj.put("id", mem.id)
            obj.put("groupId", mem.groupId)
            obj.put("name", mem.name)
            obj.put("isMe", mem.isMe)
            obj.put("createdAt", mem.createdAt)
            membersArr.put(obj)
        }
        dataObj.put("splitGroupMembers", membersArr)

        // 7. Expense Splits
        val splitsArr = JSONArray()
        backup.data.expenseSplits.forEach { sp ->
            val obj = JSONObject()
            obj.put("id", sp.id)
            obj.put("transactionId", sp.transactionId)
            obj.put("groupId", sp.groupId)
            obj.put("amountPaise", sp.amountPaise)
            obj.put("payerMemberId", sp.payerMemberId)
            obj.put("createdAt", sp.createdAt)
            splitsArr.put(obj)
        }
        dataObj.put("expenseSplits", splitsArr)

        // 8. Expense Split Shares
        val sharesArr = JSONArray()
        backup.data.expenseSplitShares.forEach { sh ->
            val obj = JSONObject()
            obj.put("id", sh.id)
            obj.put("splitId", sh.splitId)
            obj.put("memberId", sh.memberId)
            obj.put("shareAmountPaise", sh.shareAmountPaise)
            sharesArr.put(obj)
        }
        dataObj.put("expenseSplitShares", sharesArr)

        // 9. Repayments
        val repaymentsArr = JSONArray()
        backup.data.repayments.forEach { rep ->
            val obj = JSONObject()
            obj.put("id", rep.id)
            obj.put("groupId", rep.groupId)
            obj.put("memberId", rep.memberId)
            obj.put("amountPaise", rep.amountPaise)
            obj.put("unallocatedAmountPaise", rep.unallocatedAmountPaise)
            obj.put("paymentDate", rep.paymentDate)
            obj.put("timestamp", rep.timestamp)
            obj.putOptOrNull("linkedTransactionId", rep.linkedTransactionId)
            obj.putOptOrNull("note", rep.note)
            repaymentsArr.put(obj)
        }
        dataObj.put("repayments", repaymentsArr)

        // 10. Repayment Allocations
        val allocationsArr = JSONArray()
        backup.data.repaymentAllocations.forEach { alloc ->
            val obj = JSONObject()
            obj.put("id", alloc.id)
            obj.put("repaymentId", alloc.repaymentId)
            obj.put("shareId", alloc.shareId)
            obj.put("allocatedAmountPaise", alloc.allocatedAmountPaise)
            allocationsArr.put(obj)
        }
        dataObj.put("repaymentAllocations", allocationsArr)

        // 11. Recurring Expenses
        val recurringArr = JSONArray()
        backup.data.recurringExpenses.forEach { rec ->
            val obj = JSONObject()
            obj.put("id", rec.id)
            obj.put("merchant", rec.merchant)
            obj.put("amount", rec.amount)
            obj.put("frequency", rec.frequency)
            obj.put("intervalDays", rec.intervalDays)
            obj.put("lastTransactionTimestamp", rec.lastTransactionTimestamp)
            obj.put("confirmedAt", rec.confirmedAt)
            obj.put("active", rec.active)
            obj.put("dismissed", rec.dismissed)
            recurringArr.put(obj)
        }
        dataObj.put("recurringExpenses", recurringArr)

        // 12. Payment Templates
        val templatesArr = JSONArray()
        backup.data.paymentTemplates.forEach { pt ->
            val obj = JSONObject()
            obj.put("id", pt.id)
            obj.put("name", pt.name)
            obj.put("transactionType", pt.transactionType)
            obj.put("sampleText", pt.sampleText)
            obj.put("templatePattern", pt.templatePattern)
            obj.put("enabled", pt.enabled)
            obj.put("createdAt", pt.createdAt)
            obj.put("updatedAt", pt.updatedAt)
            templatesArr.put(obj)
        }
        dataObj.put("paymentTemplates", templatesArr)

        // 13. Balance Observations
        val obsArr = JSONArray()
        backup.data.balanceObservations.forEach { obs ->
            val obj = JSONObject()
            obj.put("id", obs.id)
            obj.putOptOrNull("accountId", obs.accountId)
            obj.putOptOrNull("accountSuffix", obs.accountSuffix)
            obj.put("balancePaise", obs.balancePaise)
            obj.put("balanceType", obs.balanceType)
            obj.put("currency", obs.currency)
            obj.put("timestamp", obs.timestamp)
            obj.put("source", obs.source)
            obj.putOptOrNull("senderOrPackage", obs.senderOrPackage)
            obj.putOptOrNull("referenceNumber", obs.referenceNumber)
            obj.put("rawText", obs.rawText)
            obj.put("isResolved", obs.isResolved)
            obj.put("isReconciled", obs.isReconciled)
            obj.putOptOrNull("reconciledAt", obs.reconciledAt)
            obj.put("createdAt", obs.createdAt)
            obsArr.put(obj)
        }
        dataObj.put("balanceObservations", obsArr)

        // 14. Source Account Mappings
        val samArr = JSONArray()
        backup.data.sourceAccountMappings.forEach { sam ->
            val obj = JSONObject()
            obj.put("id", sam.id)
            obj.put("accountId", sam.accountId)
            obj.put("sourcePattern", sam.sourcePattern)
            obj.putOptOrNull("senderOrPackage", sam.senderOrPackage)
            obj.putOptOrNull("matchingContext", sam.matchingContext)
            obj.put("isConfirmed", sam.isConfirmed)
            obj.put("createdAt", sam.createdAt)
            obj.put("updatedAt", sam.updatedAt)
            samArr.put(obj)
        }
        dataObj.put("sourceAccountMappings", samArr)

        root.put("data", dataObj)
        return root.toString(2)
    }

    fun serializeToStream(backup: ExpenseTrackerBackup, outputStream: OutputStream) {
        val jsonString = serialize(backup)
        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        writer.use { it.write(jsonString) }
    }

    fun deserializeFromStream(inputStream: java.io.InputStream): ExpenseTrackerBackup {
        val reader = java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)
        val jsonString = reader.use { it.readText() }
        return deserialize(jsonString)
    }


    fun deserialize(jsonString: String): ExpenseTrackerBackup {
        if (jsonString.isBlank()) {
            throw BackupValidationException("Empty backup content provided")
        }

        val root: JSONObject = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            throw BackupValidationException("Malformed JSON backup: ${e.message}", e)
        }

        val format = root.optString("format")
        if (format != BACKUP_FORMAT_IDENTIFIER) {
            throw BackupValidationException("Invalid backup format: '$format'. Expected '$BACKUP_FORMAT_IDENTIFIER'")
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion < 1) {
            throw BackupValidationException("Invalid or missing schemaVersion: $schemaVersion")
        }
        if (schemaVersion > CURRENT_BACKUP_SCHEMA_VERSION) {
            throw BackupValidationException("Unsupported schemaVersion $schemaVersion (highest supported is $CURRENT_BACKUP_SCHEMA_VERSION)")
        }

        val exportedAt = root.optString("exportedAt")
        val appVersion = root.optString("appVersion", "1.0")
        val databaseVersion = root.optInt("databaseVersion", 12)

        val dataObj = root.optJSONObject("data") ?: throw BackupValidationException("Missing 'data' object in backup")

        // Parse Accounts
        val accountsList = mutableListOf<AccountEntity>()
        val accountsArr = dataObj.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accountsArr.length()) {
            val obj = accountsArr.getJSONObject(i)
            accountsList.add(
                AccountEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    openingBalancePaise = obj.getLong("openingBalancePaise"),
                    isPrimary = obj.optBoolean("isPrimary", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    accountNumber = obj.getNullableString("accountNumber")
                )
            )
        }

        // Parse Categories
        val categoriesList = mutableListOf<CategoryEntity>()
        val categoriesArr = dataObj.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until categoriesArr.length()) {
            val obj = categoriesArr.getJSONObject(i)
            categoriesList.add(
                CategoryEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name")
                )
            )
        }

        // Parse Category Mappings
        val categoryMappingsList = mutableListOf<CategoryMappingEntity>()
        val mappingsArr = dataObj.optJSONArray("categoryMappings") ?: JSONArray()
        for (i in 0 until mappingsArr.length()) {
            val obj = mappingsArr.getJSONObject(i)
            categoryMappingsList.add(
                CategoryMappingEntity(
                    id = obj.getString("id"),
                    pattern = obj.getString("pattern"),
                    categoryName = obj.getString("categoryName"),
                    priority = obj.optInt("priority", 0)
                )
            )
        }

        // Parse Transactions
        val transactionsList = mutableListOf<BackupTransaction>()
        val txArr = dataObj.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArr.length()) {
            val obj = txArr.getJSONObject(i)
            val amtDouble = obj.getDouble("amount")
            val amtPaise = if (obj.has("amountPaise")) {
                obj.getLong("amountPaise")
            } else {
                Math.round(amtDouble * 100.0)
            }
            transactionsList.add(
                BackupTransaction(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    amount = amtDouble,
                    amountPaise = amtPaise,
                    type = obj.getString("type"),
                    category = obj.getString("category"),
                    date = obj.getString("date"),
                    source = obj.getString("source"),
                    timestamp = obj.getLong("timestamp"),
                    originalText = obj.optString("originalText", ""),
                    accountId = obj.getNullableString("accountId"),
                    referenceNumber = obj.getNullableString("referenceNumber")
                )
            )
        }

        // Parse Split Groups
        val groupsList = mutableListOf<SplitGroupEntity>()
        val groupsArr = dataObj.optJSONArray("splitGroups") ?: JSONArray()
        for (i in 0 until groupsArr.length()) {
            val obj = groupsArr.getJSONObject(i)
            groupsList.add(
                SplitGroupEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // Parse Split Group Members
        val membersList = mutableListOf<SplitGroupMemberEntity>()
        val membersArr = dataObj.optJSONArray("splitGroupMembers") ?: JSONArray()
        for (i in 0 until membersArr.length()) {
            val obj = membersArr.getJSONObject(i)
            membersList.add(
                SplitGroupMemberEntity(
                    id = obj.getString("id"),
                    groupId = obj.getString("groupId"),
                    name = obj.getString("name"),
                    isMe = obj.optBoolean("isMe", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        // Parse Expense Splits
        val splitsList = mutableListOf<ExpenseSplitEntity>()
        val splitsArr = dataObj.optJSONArray("expenseSplits") ?: JSONArray()
        for (i in 0 until splitsArr.length()) {
            val obj = splitsArr.getJSONObject(i)
            splitsList.add(
                ExpenseSplitEntity(
                    id = obj.getString("id"),
                    transactionId = obj.getString("transactionId"),
                    groupId = obj.getString("groupId"),
                    amountPaise = obj.getLong("amountPaise"),
                    payerMemberId = obj.getString("payerMemberId"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        // Parse Expense Split Shares
        val sharesList = mutableListOf<ExpenseSplitShareEntity>()
        val sharesArr = dataObj.optJSONArray("expenseSplitShares") ?: JSONArray()
        for (i in 0 until sharesArr.length()) {
            val obj = sharesArr.getJSONObject(i)
            sharesList.add(
                ExpenseSplitShareEntity(
                    id = obj.getString("id"),
                    splitId = obj.getString("splitId"),
                    memberId = obj.getString("memberId"),
                    shareAmountPaise = obj.getLong("shareAmountPaise")
                )
            )
        }

        // Parse Repayments
        val repaymentsList = mutableListOf<RepaymentEntity>()
        val repaymentsArr = dataObj.optJSONArray("repayments") ?: JSONArray()
        for (i in 0 until repaymentsArr.length()) {
            val obj = repaymentsArr.getJSONObject(i)
            repaymentsList.add(
                RepaymentEntity(
                    id = obj.getString("id"),
                    groupId = obj.getString("groupId"),
                    memberId = obj.getString("memberId"),
                    amountPaise = obj.getLong("amountPaise"),
                    unallocatedAmountPaise = obj.optLong("unallocatedAmountPaise", 0L),
                    paymentDate = obj.getString("paymentDate"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    linkedTransactionId = obj.getNullableString("linkedTransactionId"),
                    note = obj.getNullableString("note")
                )
            )
        }

        // Parse Repayment Allocations
        val allocationsList = mutableListOf<RepaymentAllocationEntity>()
        val allocationsArr = dataObj.optJSONArray("repaymentAllocations") ?: JSONArray()
        for (i in 0 until allocationsArr.length()) {
            val obj = allocationsArr.getJSONObject(i)
            allocationsList.add(
                RepaymentAllocationEntity(
                    id = obj.getString("id"),
                    repaymentId = obj.getString("repaymentId"),
                    shareId = obj.getString("shareId"),
                    allocatedAmountPaise = obj.getLong("allocatedAmountPaise")
                )
            )
        }

        // Parse Recurring Expenses
        val recurringList = mutableListOf<RecurringExpenseEntity>()
        val recurringArr = dataObj.optJSONArray("recurringExpenses") ?: JSONArray()
        for (i in 0 until recurringArr.length()) {
            val obj = recurringArr.getJSONObject(i)
            recurringList.add(
                RecurringExpenseEntity(
                    id = obj.getString("id"),
                    merchant = obj.getString("merchant"),
                    amount = obj.getDouble("amount"),
                    frequency = obj.getString("frequency"),
                    intervalDays = obj.getInt("intervalDays"),
                    lastTransactionTimestamp = obj.getLong("lastTransactionTimestamp"),
                    confirmedAt = obj.getLong("confirmedAt"),
                    active = obj.optBoolean("active", true),
                    dismissed = obj.optBoolean("dismissed", false)
                )
            )
        }

        // Parse Payment Templates
        val templatesList = mutableListOf<PaymentTemplateEntity>()
        val templatesArr = dataObj.optJSONArray("paymentTemplates") ?: JSONArray()
        for (i in 0 until templatesArr.length()) {
            val obj = templatesArr.getJSONObject(i)
            templatesList.add(
                PaymentTemplateEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    transactionType = obj.getString("transactionType"),
                    sampleText = obj.getString("sampleText"),
                    templatePattern = obj.getString("templatePattern"),
                    enabled = obj.optBoolean("enabled", true),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // Parse Balance Observations
        val obsList = mutableListOf<BalanceObservationEntity>()
        val obsArr = dataObj.optJSONArray("balanceObservations") ?: JSONArray()
        for (i in 0 until obsArr.length()) {
            val obj = obsArr.getJSONObject(i)
            obsList.add(
                BalanceObservationEntity(
                    id = obj.getString("id"),
                    accountId = obj.getNullableString("accountId"),
                    accountSuffix = obj.getNullableString("accountSuffix"),
                    balancePaise = obj.getLong("balancePaise"),
                    balanceType = obj.optString("balanceType", "AVAILABLE"),
                    currency = obj.optString("currency", "INR"),
                    timestamp = obj.getLong("timestamp"),
                    source = obj.getString("source"),
                    senderOrPackage = obj.getNullableString("senderOrPackage"),
                    referenceNumber = obj.getNullableString("referenceNumber"),
                    rawText = obj.optString("rawText", ""),
                    isResolved = obj.optBoolean("isResolved", false),
                    isReconciled = obj.optBoolean("isReconciled", false),
                    reconciledAt = obj.getNullableLong("reconciledAt"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        // Parse Source Account Mappings
        val samList = mutableListOf<SourceAccountMappingEntity>()
        val samArr = dataObj.optJSONArray("sourceAccountMappings") ?: JSONArray()
        for (i in 0 until samArr.length()) {
            val obj = samArr.getJSONObject(i)
            samList.add(
                SourceAccountMappingEntity(
                    id = obj.getString("id"),
                    accountId = obj.getString("accountId"),
                    sourcePattern = obj.getString("sourcePattern"),
                    senderOrPackage = obj.getNullableString("senderOrPackage"),
                    matchingContext = obj.getNullableString("matchingContext"),
                    isConfirmed = obj.optBoolean("isConfirmed", true),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val dataPayload = BackupDataPayload(
            accounts = accountsList,
            categories = categoriesList,
            categoryMappings = categoryMappingsList,
            transactions = transactionsList,
            splitGroups = groupsList,
            splitGroupMembers = membersList,
            expenseSplits = splitsList,
            expenseSplitShares = sharesList,
            repayments = repaymentsList,
            repaymentAllocations = allocationsList,
            recurringExpenses = recurringList,
            paymentTemplates = templatesList,
            balanceObservations = obsList,
            sourceAccountMappings = samList
        )

        val metadata = BackupMetadata(
            totalTransactions = transactionsList.size,
            totalAccounts = accountsList.size,
            totalCategories = categoriesList.size,
            totalSplitGroups = groupsList.size,
            totalRepayments = repaymentsList.size,
            recordCounts = mapOf(
                "accounts" to accountsList.size,
                "categories" to categoriesList.size,
                "categoryMappings" to categoryMappingsList.size,
                "transactions" to transactionsList.size,
                "splitGroups" to groupsList.size,
                "splitGroupMembers" to membersList.size,
                "expenseSplits" to splitsList.size,
                "expenseSplitShares" to sharesList.size,
                "repayments" to repaymentsList.size,
                "repaymentAllocations" to allocationsList.size,
                "recurringExpenses" to recurringList.size,
                "paymentTemplates" to templatesList.size,
                "balanceObservations" to obsList.size,
                "sourceAccountMappings" to samList.size
            )
        )

        return ExpenseTrackerBackup(
            format = format,
            schemaVersion = schemaVersion,
            exportedAt = exportedAt,
            appVersion = appVersion,
            databaseVersion = databaseVersion,
            data = dataPayload,
            metadata = metadata
        )
    }

    private fun JSONObject.putOptOrNull(name: String, value: Any?) {
        if (value == null) {
            put(name, JSONObject.NULL)
        } else {
            put(name, value)
        }
    }

    private fun JSONObject.getNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val str = getString(name)
        return if (str.equals("null", ignoreCase = true)) null else str
    }

    private fun JSONObject.getNullableLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return optLong(name)
    }
}
