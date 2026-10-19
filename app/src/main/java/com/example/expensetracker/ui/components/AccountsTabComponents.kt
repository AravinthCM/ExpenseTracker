package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.data.local.BalanceObservationEntity
import com.example.expensetracker.model.AccountWithBalance
import com.example.expensetracker.model.ReconciliationResult
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import kotlin.math.roundToLong

@Composable
fun AccountsTabContent(
    accountsWithBalances: List<AccountWithBalance>,
    transactions: List<Transaction> = emptyList(),
    unassignedCount: Int,
    unresolvedObservations: List<BalanceObservationEntity> = emptyList(),
    allObservations: List<BalanceObservationEntity> = emptyList(),
    reconciliationResults: Map<String, ReconciliationResult> = emptyMap(),
    onAddAccountClick: () -> Unit,
    onEditAccountClick: (AccountEntity) -> Unit,
    onSetPrimaryAccountClick: (String) -> Unit,
    onToggleArchivedClick: (AccountEntity) -> Unit,
    onDeleteAccountClick: (AccountEntity) -> Unit,
    onAssignUnassignedClick: (String) -> Unit,
    onAssignSelectedUnassigned: (List<String>, String) -> Unit = { _, _ -> },
    onAddReconciliationExpense: (AccountEntity, Long, Long) -> Unit = { _, _, _ -> },
    onReviewReconciliationTransactions: (String) -> Unit = {},
    onResolveObservation: (observationId: String, accountId: String, rememberMapping: Boolean) -> Unit = { _, _, _ -> },
    onDeleteObservation: (observationId: String) -> Unit = {}
) {
    val appColors = LocalAppColors.current
    var reviewingObservation by remember { mutableStateOf<BalanceObservationEntity?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var confirmDeletion by remember { mutableStateOf(false) }
    var reviewingUnassigned by remember { mutableStateOf(false) }
    var selectedUnassignedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTargetAccountId by remember { mutableStateOf<String?>(null) }

    val unassignedTransactions = transactions.filter { transaction ->
        transaction.accountId == null || accountsWithBalances.none { it.account.id == transaction.accountId }
    }

    if (reviewingUnassigned) {
        AlertDialog(
            onDismissRequest = { reviewingUnassigned = false },
            title = { Text("Review unassigned transactions", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select records to assign. Amounts, dates, descriptions, and categories will remain unchanged.")
                    unassignedTransactions.forEach { transaction ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = transaction.id in selectedUnassignedIds,
                                onCheckedChange = { checked ->
                                    selectedUnassignedIds = if (checked) selectedUnassignedIds + transaction.id else selectedUnassignedIds - transaction.id
                                }
                            )
                            Column {
                                Text(transaction.title, fontWeight = FontWeight.SemiBold)
                                Text("${transaction.date} • ${transaction.type.name} • ${CurrencyFormatter.format(transaction.amount)}", fontSize = 11.sp)
                            }
                        }
                    }
                    Text("Assign to:", fontWeight = FontWeight.Bold)
                    accountsWithBalances.filter { !it.account.isArchived }.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedTargetAccountId = account.account.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTargetAccountId == account.account.id,
                                onClick = { selectedTargetAccountId = account.account.id }
                            )
                            Text(account.account.name)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTargetAccountId?.let { target ->
                            onAssignSelectedUnassigned(selectedUnassignedIds.toList(), target)
                            selectedUnassignedIds = emptySet()
                            selectedTargetAccountId = null
                            reviewingUnassigned = false
                        }
                    },
                    enabled = selectedUnassignedIds.isNotEmpty() && selectedTargetAccountId != null
                ) { Text("Assign selected") }
            },
            dismissButton = { TextButton(onClick = { reviewingUnassigned = false }) { Text("Cancel") } }
        )
    }

    deletingAccount?.let { account ->
        val currentAccount = accountsWithBalances.firstOrNull { it.account.id == account.id }?.account ?: account
        val requiresReplacementPrimary = currentAccount.isPrimary && !currentAccount.isArchived
        val replacementAccounts = accountsWithBalances
            .map { it.account }
            .filter { !it.isArchived && it.id != currentAccount.id }
        val transactionCount = transactions.count { it.accountId == account.id }
        val observationCount = allObservations.count { it.accountId == account.id }
        AlertDialog(
            onDismissRequest = {
                deletingAccount = null
                confirmDeletion = false
            },
            title = { Text(if (confirmDeletion) "Confirm permanent deletion" else "Delete account?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account: ${account.name}${account.accountNumber?.let { " (••••$it)" } ?: ""}")
                    if (!confirmDeletion) {
                        Text("This account has $transactionCount transaction(s) and $observationCount balance observation(s).")
                        Text("Transactions will be preserved but become unassigned. Balance observations will become unassigned, and source-account mappings will be removed. This deletion cannot be undone.")
                        if (requiresReplacementPrimary) {
                            Text("Choose another active account as primary before archiving or deleting this account.", fontWeight = FontWeight.Bold)
                            replacementAccounts.forEach { replacement ->
                                TextButton(onClick = { onSetPrimaryAccountClick(replacement.id) }) {
                                    Text("Make ${replacement.name} primary")
                                }
                            }
                        }
                    } else {
                        Text("The account will be deleted permanently. Historical transactions will remain without an account assignment.")
                    }
                }
            },
            confirmButton = {
                if (!confirmDeletion) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            onToggleArchivedClick(account)
                            deletingAccount = null
                        }, enabled = !requiresReplacementPrimary) { Text("Archive") }
                            Button(
                                onClick = { confirmDeletion = true },
                                enabled = !requiresReplacementPrimary
                            ) { Text("Continue") }
                    }
                } else {
                    Button(
                        onClick = {
                            onDeleteAccountClick(account)
                            deletingAccount = null
                            confirmDeletion = false
                        },
                        enabled = !requiresReplacementPrimary,
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.negative)
                    ) { Text("Delete permanently") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    deletingAccount = null
                    confirmDeletion = false
                }) { Text("Cancel") }
            }
        )
    }

    if (reviewingObservation != null) {
        AssignObservationAccountDialog(
            observation = reviewingObservation!!,
            accounts = accountsWithBalances.map { it.account },
            onDismiss = { reviewingObservation = null },
            onConfirm = { accountId, rememberMapping ->
                onResolveObservation(reviewingObservation!!.id, accountId, rememberMapping)
                reviewingObservation = null
            },
            onDelete = {
                onDeleteObservation(reviewingObservation!!.id)
                reviewingObservation = null
            }
        )
    }

    val primaryAccount = accountsWithBalances.find { it.account.isPrimary && !it.account.isArchived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Unresolved Observations Review Banner
        if (unresolvedObservations.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.negativeSoft)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = appColors.negative, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Unresolved Balance Observations (${unresolvedObservations.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = appColors.negative
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bank balance alerts whose account could not be safely identified. Review and assign them to an account.",
                            fontSize = 12.sp,
                            color = appColors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        unresolvedObservations.forEach { obs ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(appColors.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "₹${CurrencyFormatter.format(obs.balancePaise / 100.0)} • ${obs.source}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.ink
                                    )
                                    if (!obs.accountSuffix.isNullOrBlank()) {
                                        Text(
                                            text = "Account suffix: ${obs.accountSuffix}",
                                            fontSize = 11.5.sp,
                                            color = appColors.accent
                                        )
                                    }
                                }
                                Button(
                                    onClick = { reviewingObservation = obs },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = appColors.accent,
                                        contentColor = appColors.accentInk
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Assign", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Banner for Unassigned Transactions if any
        if (unassignedCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.accentSoft)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "$unassignedCount unassigned transaction(s)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = appColors.accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Historical transactions captured before creating accounts are currently unassigned.",
                            fontSize = 12.sp,
                            color = appColors.inkMuted
                        )
                        if (primaryAccount != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { reviewingUnassigned = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.accentInk
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Review and assign", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Add Account Button
        item {
            Button(
                onClick = onAddAccountClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = appColors.accentInk
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Bank / Cash Account", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }

        if (accountsWithBalances.isEmpty()) {
            item {
                Text(
                    text = "No bank or cash accounts configured yet. Tap above to add one.",
                    fontSize = 13.sp,
                    color = appColors.inkFaint,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(
                items = accountsWithBalances,
                key = { it.account.id }
            ) { item ->
                AccountCardItem(
                    accountWithBalance = item,
                    reconciliationResult = reconciliationResults[item.account.id],
                    onEditClick = { onEditAccountClick(item.account) },
                    onSetPrimaryClick = { onSetPrimaryAccountClick(item.account.id) },
                    onToggleArchivedClick = { onToggleArchivedClick(item.account) },
                    onDeleteClick = { deletingAccount = item.account },
                    onAssignUnassignedClick = { onAssignUnassignedClick(item.account.id) },
                    onAddReconciliationExpense = { amountPaise, timestamp ->
                        onAddReconciliationExpense(item.account, amountPaise, timestamp)
                    },
                    onReviewReconciliationTransactions = { onReviewReconciliationTransactions(item.account.id) }
                )
            }
        }
    }
}

@Composable
fun AccountCardItem(
    accountWithBalance: AccountWithBalance,
    reconciliationResult: ReconciliationResult? = null,
    onEditClick: () -> Unit,
    onSetPrimaryClick: () -> Unit,
    onToggleArchivedClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAssignUnassignedClick: () -> Unit,
    onAddReconciliationExpense: (Long, Long) -> Unit = { _, _ -> },
    onReviewReconciliationTransactions: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val account = accountWithBalance.account

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, appColors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = if (account.isArchived) appColors.inkFaint else appColors.ink
                            )
                            if (!account.accountNumber.isNullOrBlank()) {
                                Text(
                                    text = "••••${account.accountNumber}",
                                    fontSize = 12.sp,
                                    color = appColors.inkMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (account.isPrimary) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(appColors.positiveSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRIMARY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.positive
                            )
                        }
                    }

                    if (account.isArchived) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(appColors.surfaceAlt)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ARCHIVED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.inkFaint
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = appColors.inkMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = appColors.negative, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Balance Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(text = "Current balance", fontSize = 11.sp, color = appColors.inkFaint)
                    Text(
                        text = CurrencyFormatter.format(accountWithBalance.currentBalanceDouble),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (accountWithBalance.currentBalancePaise >= 0) appColors.positive else appColors.negative
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Legacy opening balance", fontSize = 11.sp, color = appColors.inkFaint)
                    Text(
                        text = CurrencyFormatter.format(account.openingBalancePaise / 100.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.inkMuted
                    )
                }
            }

            // Reconciliation Status Badge
            if (reconciliationResult?.latestObservation != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (reconciliationResult.isReconciled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(appColors.positiveSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = appColors.positive, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reconciled with bank (₹${CurrencyFormatter.format(reconciliationResult.observedBalanceDouble ?: 0.0)})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.positive
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(appColors.negativeSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = appColors.negative, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = reconciliationResult.statusMessage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.negative
                        )
                    }
                }
                if (reconciliationResult.discrepancyPaise != null && reconciliationResult.discrepancyPaise != 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (reconciliationResult.discrepancyPaise < 0L) {
                            TextButton(onClick = {
                                onAddReconciliationExpense(
                                    kotlin.math.abs(reconciliationResult.discrepancyPaise),
                                    reconciliationResult.latestObservation.timestamp
                                )
                            }) { Text("+ Add missing expense") }
                        } else {
                            TextButton(onClick = onReviewReconciliationTransactions) { Text("Review recorded transactions") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!account.isPrimary && !account.isArchived) {
                    TextButton(
                        onClick = onSetPrimaryClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = appColors.accent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Make primary", fontSize = 11.5.sp, color = appColors.accent, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = onToggleArchivedClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (account.isArchived) "Unarchive" else "Archive",
                        fontSize = 11.5.sp,
                        color = appColors.inkMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AddOrEditAccountDialog(
    initialAccount: AccountEntity? = null,
    onDismiss: () -> Unit,
    onSave: (id: String?, name: String, currentBalancePaise: Long, isPrimary: Boolean, accountNumber: String?) -> String?
) {
    val appColors = LocalAppColors.current

    var nameText by remember { mutableStateOf(initialAccount?.name ?: "") }
    var accountNumberText by remember { mutableStateOf(initialAccount?.accountNumber ?: "") }
    var balanceText by remember { mutableStateOf(initialAccount?.let { (it.openingBalancePaise / 100.0).toString() } ?: "0") }
    var isPrimary by remember { mutableStateOf(initialAccount?.isPrimary ?: false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialAccount == null) "Add Account" else "Edit Account",
                fontWeight = FontWeight.Bold,
                color = appColors.ink
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        errorMessage = null
                    },
                    label = { Text("Account Name (e.g., Canara Bank, SBI, Cash)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accountNumberText,
                    onValueChange = {
                        accountNumberText = it
                        errorMessage = null
                    },
                    label = { Text("Account Number / Suffix (optional, e.g. 528)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        errorMessage = null
                    },
                    label = { Text("Current Balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPrimary = !isPrimary }
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as primary account for new transactions",
                        fontSize = 12.5.sp,
                        color = appColors.ink
                    )
                }

                errorMessage?.let { err ->
                    Text(text = err, fontSize = 12.sp, color = appColors.negative)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedBalanceDouble = balanceText.toDoubleOrNull()
                    if (nameText.isBlank()) {
                        errorMessage = "Account name cannot be empty."
                        return@TextButton
                    }
                    if (parsedBalanceDouble == null) {
                        errorMessage = "Enter a valid current balance."
                        return@TextButton
                    }

                    val paise = (parsedBalanceDouble * 100.0).roundToLong()
                    val error = onSave(initialAccount?.id, nameText, paise, isPrimary, accountNumberText.trim().takeIf { it.isNotBlank() })
                    if (error != null) {
                        errorMessage = error
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = appColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = appColors.inkMuted)
            }
        },
        containerColor = appColors.surface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun AssignObservationAccountDialog(
    observation: BalanceObservationEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: String, rememberMapping: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val appColors = LocalAppColors.current
    val activeAccounts = accounts.filter { !it.isArchived }
    var selectedAccountId by remember { mutableStateOf(activeAccounts.firstOrNull()?.id ?: "") }
    var rememberMapping by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Assign Account to Observation", fontWeight = FontWeight.Bold, color = appColors.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                        text = "Bank balance of ₹${CurrencyFormatter.format(observation.balancePaise / 100.0)} was observed, but the account could not be automatically identified.",
                    fontSize = 12.5.sp,
                    color = appColors.inkMuted
                )
                if (!observation.accountSuffix.isNullOrBlank()) {
                    Text(
                        text = "Extracted Suffix: ${observation.accountSuffix}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.accent
                    )
                }
                Text(
                    text = "Source: ${observation.source}${observation.senderOrPackage?.let { " ($it)" } ?: ""}",
                    fontSize = 11.5.sp,
                    color = appColors.inkFaint
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Select Account:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appColors.ink)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activeAccounts.forEach { acc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAccountId = acc.id }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = acc.name + (acc.accountNumber?.let { " (••••$it)" } ?: ""), fontSize = 13.sp, color = appColors.ink)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberMapping = !rememberMapping }
                        .padding(top = 6.dp)
                ) {
                    Checkbox(checked = rememberMapping, onCheckedChange = { rememberMapping = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Remember mapping for this source pattern", fontSize = 11.5.sp, color = appColors.ink)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedAccountId.isNotBlank()) {
                        onConfirm(selectedAccountId, rememberMapping)
                        onDismiss()
                    }
                },
                enabled = selectedAccountId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = appColors.accentInk)
            ) {
                Text("Assign & Resolve", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) {
                    Text("Dismiss", color = appColors.negative)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = appColors.inkMuted)
                }
            }
        },
        containerColor = appColors.surface,
        shape = RoundedCornerShape(18.dp)
    )
}
