package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.model.AccountWithBalance
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import kotlin.math.roundToLong

@Composable
fun AccountsTabContent(
    accountsWithBalances: List<AccountWithBalance>,
    transactions: List<Transaction> = emptyList(),
    unassignedCount: Int,
    onAddAccountClick: () -> Unit,
    onEditAccountClick: (AccountEntity) -> Unit,
    onSetPrimaryAccountClick: (String) -> Unit,
    onToggleArchivedClick: (AccountEntity) -> Unit,
    onDeleteAccountClick: (AccountEntity) -> Unit,
    onAssignUnassignedClick: (String) -> Unit,
    onAssignSelectedUnassigned: (List<String>, String) -> Unit = { _, _ -> }
) {
    val appColors = LocalAppColors.current
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var confirmDeletion by remember { mutableStateOf(false) }
    var reviewingUnassigned by remember { mutableStateOf(false) }
    var selectedUnassignedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTargetAccountId by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val activeAccounts = accountsWithBalances.filter { !it.account.isArchived }
    val archivedAccounts = accountsWithBalances.filter { it.account.isArchived }
    val totalBalance = activeAccounts.sumOf { it.currentBalanceDouble }

    val unassignedTransactions = transactions.filter { transaction ->
        transaction.accountId == null || accountsWithBalances.none { it.account.id == transaction.accountId }
    }

    // ── Review unassigned dialog ──
    if (reviewingUnassigned) {
        AlertDialog(
            onDismissRequest = { reviewingUnassigned = false },
            title = { Text("Assign transactions", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Pick the transactions and the account they belong to. Amounts, dates and categories stay unchanged.",
                        fontSize = 13.sp,
                        color = appColors.inkMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedUnassignedIds.size} of ${unassignedTransactions.size} selected", fontSize = 12.sp, color = appColors.inkFaint)
                        TextButton(onClick = {
                            selectedUnassignedIds =
                                if (selectedUnassignedIds.size == unassignedTransactions.size) emptySet()
                                else unassignedTransactions.map { it.id }.toSet()
                        }) {
                            Text(if (selectedUnassignedIds.size == unassignedTransactions.size) "Clear" else "Select all", fontSize = 12.sp)
                        }
                    }
                    unassignedTransactions.forEach { transaction ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = transaction.id in selectedUnassignedIds,
                                onCheckedChange = { checked ->
                                    selectedUnassignedIds =
                                        if (checked) selectedUnassignedIds + transaction.id
                                        else selectedUnassignedIds - transaction.id
                                }
                            )
                            Column {
                                Text(transaction.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${transaction.date} • ${transaction.type.name} • ${CurrencyFormatter.format(transaction.amount)}",
                                    fontSize = 11.sp,
                                    color = appColors.inkFaint
                                )
                            }
                        }
                    }
                    Text("Assign to", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    activeAccounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetAccountId = account.account.id },
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

    // ── Delete flow ──
    deletingAccount?.let { account ->
        val currentAccount = accountsWithBalances.firstOrNull { it.account.id == account.id }?.account ?: account
        val requiresReplacementPrimary = currentAccount.isPrimary && !currentAccount.isArchived
        val replacementAccounts = accountsWithBalances
            .map { it.account }
            .filter { !it.isArchived && it.id != currentAccount.id }
        val transactionCount = transactions.count { it.accountId == account.id }

        AlertDialog(
            onDismissRequest = {
                deletingAccount = null
                confirmDeletion = false
            },
            title = {
                Text(
                    if (confirmDeletion) "Confirm permanent deletion" else "Remove account?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account: ${account.name}${account.accountNumber?.let { " (••••$it)" } ?: ""}")
                    if (!confirmDeletion) {
                        Text("This account has $transactionCount transaction(s).")
                        Text("Archive keeps everything but hides the account. Deleting keeps transactions but leaves them unassigned, and cannot be undone.")
                        if (requiresReplacementPrimary) {
                            Text("Choose another active account as primary first.", fontWeight = FontWeight.Bold)
                            replacementAccounts.forEach { replacement ->
                                TextButton(onClick = { onSetPrimaryAccountClick(replacement.id) }) {
                                    Text("Make ${replacement.name} primary")
                                }
                            }
                        }
                    } else {
                        Text("The account will be deleted permanently. Historical transactions will remain without an account.")
                    }
                }
            },
            confirmButton = {
                if (!confirmDeletion) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onToggleArchivedClick(account)
                                deletingAccount = null
                            },
                            enabled = !requiresReplacementPrimary
                        ) { Text("Archive") }
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

    if (accountsWithBalances.isEmpty() && unassignedCount == 0) {
        ManageEmptyState(
            title = "No accounts yet",
            message = "Add your bank accounts and cash so every transaction and balance has a home.",
            actionLabel = "Add account",
            onAction = onAddAccountClick
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Needs-attention banner
        if (unassignedCount > 0) {
            item(key = "unassigned") {
                Surface(shape = RoundedCornerShape(14.dp), color = appColors.accentSoft, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$unassignedCount transaction(s) have no account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = appColors.accent
                            )
                            Text("Captured before you added accounts.", fontSize = 12.sp, color = appColors.inkMuted)
                        }
                        if (activeAccounts.isNotEmpty()) {
                            TextButton(onClick = { reviewingUnassigned = true }) {
                                Text("Assign", fontWeight = FontWeight.Bold, color = appColors.accent)
                            }
                        }
                    }
                }
            }
        }

        // Total across active accounts
        if (activeAccounts.isNotEmpty()) {
            item(key = "total") {
                Surface(shape = RoundedCornerShape(16.dp), color = appColors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appColors.accentSoft.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        Text("Total balance", fontSize = 12.sp, color = appColors.inkMuted)
                        Text(
                            CurrencyFormatter.format(totalBalance),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalBalance >= 0) appColors.ink else appColors.negative
                        )
                        Text(
                            "Across ${activeAccounts.size} active account${if (activeAccounts.size == 1) "" else "s"}",
                            fontSize = 12.sp,
                            color = appColors.inkFaint
                        )
                    }
                }
            }
        }

        items(items = activeAccounts, key = { "a_" + it.account.id }) { item ->
            AccountCardItem(
                accountWithBalance = item,
                onEditClick = { onEditAccountClick(item.account) },
                onSetPrimaryClick = { onSetPrimaryAccountClick(item.account.id) },
                onToggleArchivedClick = { onToggleArchivedClick(item.account) },
                onDeleteClick = { deletingAccount = item.account },
                onAssignUnassignedClick = { onAssignUnassignedClick(item.account.id) }
            )
        }

        if (archivedAccounts.isNotEmpty()) {
            item(key = "archived_toggle") {
                TextButton(onClick = { showArchived = !showArchived }) {
                    Text(
                        if (showArchived) "Hide archived" else "Archived (${archivedAccounts.size})",
                        color = appColors.inkMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (showArchived) {
                items(items = archivedAccounts, key = { "z_" + it.account.id }) { item ->
                    AccountCardItem(
                        accountWithBalance = item,
                        onEditClick = { onEditAccountClick(item.account) },
                        onSetPrimaryClick = { onSetPrimaryAccountClick(item.account.id) },
                        onToggleArchivedClick = { onToggleArchivedClick(item.account) },
                        onDeleteClick = { deletingAccount = item.account },
                        onAssignUnassignedClick = { onAssignUnassignedClick(item.account.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AccountCardItem(
    accountWithBalance: AccountWithBalance,
    onEditClick: () -> Unit,
    onSetPrimaryClick: () -> Unit,
    onToggleArchivedClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAssignUnassignedClick: () -> Unit // kept for source compatibility
) {
    val appColors = LocalAppColors.current
    val account = accountWithBalance.account

    val actions = buildList {
        add(ManageMenuAction("Edit") { onEditClick() })
        if (!account.isPrimary && !account.isArchived) {
            add(ManageMenuAction("Make primary") { onSetPrimaryClick() })
        }
        add(ManageMenuAction(if (account.isArchived) "Unarchive" else "Archive") { onToggleArchivedClick() })
        add(ManageMenuAction("Delete", destructive = true) { onDeleteClick() })
    }

    ManageSurface(onClick = onEditClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (account.isArchived) appColors.surfaceAlt else appColors.accentSoft,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    account.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = if (account.isArchived) appColors.inkFaint else appColors.accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        account.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (account.isArchived) appColors.inkFaint else appColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (account.isPrimary) ManagePill("PRIMARY", appColors.positiveSoft, appColors.positive)
                    if (account.isArchived) ManagePill("ARCHIVED", appColors.surfaceAlt, appColors.inkFaint)
                }
                if (!account.accountNumber.isNullOrBlank()) {
                    Text("••••${account.accountNumber}", fontSize = 12.sp, color = appColors.inkFaint)
                }
            }
            Text(
                CurrencyFormatter.format(accountWithBalance.currentBalanceDouble),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    account.isArchived -> appColors.inkFaint
                    accountWithBalance.currentBalancePaise >= 0 -> appColors.ink
                    else -> appColors.negative
                },
                maxLines = 1
            )
            ManageOverflowMenu(actions = actions)
        }
    }
}

@Composable
fun AddOrEditAccountDialog(
    initialAccount: AccountEntity? = null,
    initialCurrentBalancePaise: Long? = null,
    onDismiss: () -> Unit,
    onSave: (id: String?, name: String, currentBalancePaise: Long, isPrimary: Boolean, accountNumber: String?) -> String?
) {
    val appColors = LocalAppColors.current

    var nameText by remember { mutableStateOf(initialAccount?.name ?: "") }
    var accountNumberText by remember { mutableStateOf(initialAccount?.accountNumber ?: "") }
    // Prefill with the account's actual displayed Current balance (baseline + ledger since baseline),
    // NOT the legacy opening balance, so editing doesn't appear to reset the balance to ₹0.
    val prefillBalancePaise = com.example.expensetracker.model.AccountBalanceCalculator.resolveEditPrefillBalancePaise(
        initialAccount,
        initialCurrentBalancePaise
    )
    var balanceText by remember { mutableStateOf((prefillBalancePaise / 100.0).toString()) }
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
                    label = { Text("Current Balance (₹) — manual override") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "This sets a manual balance checkpoint as of now. It does not edit or delete any past transactions. " +
                        "You can update this manually any time.",
                    fontSize = 11.sp,
                    color = appColors.inkFaint
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPrimary = !isPrimary }
                ) {
                    Checkbox(checked = isPrimary, onCheckedChange = { isPrimary = it })
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