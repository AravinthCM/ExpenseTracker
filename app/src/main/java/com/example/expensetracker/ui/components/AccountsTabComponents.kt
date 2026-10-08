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
import com.example.expensetracker.model.AccountWithBalance
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import kotlin.math.roundToLong

@Composable
fun AccountsTabContent(
    accountsWithBalances: List<AccountWithBalance>,
    unassignedCount: Int,
    onAddAccountClick: () -> Unit,
    onEditAccountClick: (AccountEntity) -> Unit,
    onSetPrimaryAccountClick: (String) -> Unit,
    onToggleArchivedClick: (AccountEntity) -> Unit,
    onDeleteAccountClick: (AccountEntity) -> Unit,
    onAssignUnassignedClick: (String) -> Unit
) {
    val appColors = LocalAppColors.current

    val primaryAccount = accountsWithBalances.find { it.account.isPrimary && !it.account.isArchived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                                onClick = { onAssignUnassignedClick(primaryAccount.account.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.accentInk
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Assign all to ${primaryAccount.account.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    onEditClick = { onEditAccountClick(item.account) },
                    onSetPrimaryClick = { onSetPrimaryAccountClick(item.account.id) },
                    onToggleArchivedClick = { onToggleArchivedClick(item.account) },
                    onDeleteClick = { onDeleteAccountClick(item.account) },
                    onAssignUnassignedClick = { onAssignUnassignedClick(item.account.id) }
                )
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
    onAssignUnassignedClick: () -> Unit
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
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = if (account.isArchived) appColors.inkFaint else appColors.ink
                    )

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
                    Text(text = "Opening balance", fontSize = 11.sp, color = appColors.inkFaint)
                    Text(
                        text = CurrencyFormatter.format(account.openingBalancePaise / 100.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.inkMuted
                    )
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
    onSave: (id: String?, name: String, openingBalancePaise: Long, isPrimary: Boolean) -> String?
) {
    val appColors = LocalAppColors.current

    var nameText by remember { mutableStateOf(initialAccount?.name ?: "") }
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
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        errorMessage = null
                    },
                    label = { Text("Opening Balance (₹)") },
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
                        errorMessage = "Enter a valid opening balance."
                        return@TextButton
                    }

                    val paise = (parsedBalanceDouble * 100.0).roundToLong()
                    val error = onSave(initialAccount?.id, nameText, paise, isPrimary)
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
