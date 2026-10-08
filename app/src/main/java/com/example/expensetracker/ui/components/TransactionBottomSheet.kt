package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddOrEditTransactionBottomSheet(
    initialTransaction: Transaction? = null,
    availableCategories: List<String> = emptyList(),
    accounts: List<com.example.expensetracker.data.local.AccountEntity> = emptyList(),
    defaultAccountId: String? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(initialTransaction?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(initialTransaction?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var titleText by remember { mutableStateOf(initialTransaction?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(initialTransaction?.category ?: TransactionCategory.FOOD) }
    var selectedAccountId by remember {
        mutableStateOf(
            if (initialTransaction != null) initialTransaction.accountId else (defaultAccountId ?: accounts.find { it.isPrimary }?.id)
        )
    }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var dateText by remember {
        mutableStateOf(
            initialTransaction?.date ?: SimpleDateFormat("E, dd MMM yyyy", Locale.getDefault()).format(Date())
        )
    }

    var amountError by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = appColors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(appColors.border, RoundedCornerShape(3.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTransaction == null) "Add transaction" else "Edit transaction",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = appColors.ink
                )

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(appColors.surfaceAlt)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.inkMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Type Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.surfaceAlt, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val types = listOf(
                    Triple(TransactionType.EXPENSE, "Expense", appColors.negativeSoft to appColors.negative),
                    Triple(TransactionType.INCOME, "Income", appColors.positiveSoft to appColors.positive),
                    Triple(TransactionType.TRANSFER, "Transfer", appColors.transferSoft to appColors.transfer)
                )

                types.forEach { (t, label, colors) ->
                    val isActive = type == t
                    val bg = if (isActive) colors.first else Color.Transparent
                    val textColor = if (isActive) colors.second else appColors.inkMuted

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable {
                                type = t
                                if (t == TransactionType.INCOME && (selectedCategory == TransactionCategory.FOOD || selectedCategory == TransactionCategory.GROCERY)) {
                                    selectedCategory = TransactionCategory.SALARY
                                }
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.8.sp
                            ),
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Amount Field
            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                color = appColors.inkMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.bg, RoundedCornerShape(11.dp))
                    .border(1.dp, if (amountError != null) appColors.negative else appColors.border, RoundedCornerShape(11.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹ ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.inkFaint
                )
                BasicTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.ink
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            amountError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, fontSize = 11.sp, color = appColors.negative)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Merchant / Description Field
            Text(
                text = "Merchant / description",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                color = appColors.inkMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.bg, RoundedCornerShape(11.dp))
                    .border(1.dp, if (titleError != null) appColors.negative else appColors.border, RoundedCornerShape(11.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = titleText,
                    onValueChange = {
                        titleText = it
                        titleError = null
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        color = appColors.ink
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            titleError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, fontSize = 11.sp, color = appColors.negative)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Category Chip Grid
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                color = appColors.inkMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TransactionCategory.values().forEach { cat ->
                    val isActive = selectedCategory == cat
                    val bg = if (isActive) appColors.accentSoft else appColors.bg
                    val borderC = if (isActive) appColors.accent else appColors.border
                    val textC = if (isActive) appColors.accent else appColors.inkMuted

                    val catName = cat.name.lowercase().replaceFirstChar { it.uppercase() }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(bg)
                            .border(1.dp, borderC, RoundedCornerShape(100.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = catName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = textC
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Account Field (if accounts available)
            if (accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = appColors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                val selectedAccName = accounts.find { it.id == selectedAccountId }?.let {
                    if (it.isPrimary) "${it.name} (Primary)" else it.name
                } ?: "None (Unassigned)"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.bg, RoundedCornerShape(11.dp))
                        .border(1.dp, appColors.border, RoundedCornerShape(11.dp))
                        .clickable { accountDropdownExpanded = true }
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = selectedAccName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.ink
                        )
                    )

                    androidx.compose.material3.DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("None (Unassigned)") },
                            onClick = {
                                selectedAccountId = null
                                accountDropdownExpanded = false
                            }
                        )
                        accounts.forEach { acc ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(if (acc.isPrimary) "${acc.name} (Primary)" else acc.name)
                                },
                                onClick = {
                                    selectedAccountId = acc.id
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Date Field
            Text(
                text = "Date",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                color = appColors.inkMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.bg, RoundedCornerShape(11.dp))
                    .border(1.dp, appColors.border, RoundedCornerShape(11.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        color = appColors.ink
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Transaction Primary Button
            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull()
                    val trimmedTitle = titleText.trim()

                    var hasErr = false
                    if (parsedAmount == null || parsedAmount <= 0) {
                        amountError = "Enter valid amount"
                        hasErr = true
                    }
                    if (trimmedTitle.isBlank()) {
                        titleError = "Enter merchant / description"
                        hasErr = true
                    }

                    if (!hasErr && parsedAmount != null) {
                        val savedTx = initialTransaction?.copy(
                            title = trimmedTitle,
                            amount = parsedAmount,
                            type = type,
                            category = selectedCategory,
                            date = dateText,
                            accountId = selectedAccountId
                        ) ?: Transaction(
                            id = UUID.randomUUID().toString(),
                            title = trimmedTitle,
                            amount = parsedAmount,
                            type = type,
                            category = selectedCategory,
                            date = dateText,
                            source = TransactionSource.MANUAL,
                            accountId = selectedAccountId
                        )
                        onSave(savedTx)
                    }
                },
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = appColors.accentInk
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (initialTransaction == null) "Save transaction" else "Update transaction",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailBottomSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sign = when (transaction.type) {
        TransactionType.EXPENSE -> "−"
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> ""
    }

    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> appColors.negative
        TransactionType.INCOME -> appColors.positive
        TransactionType.TRANSFER -> appColors.transfer
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = appColors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(appColors.border, RoundedCornerShape(3.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = appColors.ink,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(appColors.surfaceAlt)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.inkMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big Amount Display
            Text(
                text = "$sign${CurrencyFormatter.format(transaction.amount)}",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = amountColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Detail Rows
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DetailRow(label = "Type", value = transaction.type.name.lowercase().replaceFirstChar { it.uppercase() })
                DetailRow(label = "Category", value = transaction.category.name.lowercase().replaceFirstChar { it.uppercase() })
                val sourceFormatted = when (transaction.source.name) {
                    "GPAY" -> "GPay"
                    "BANK_SMS" -> "Bank SMS"
                    "BANK_EMAIL" -> "Bank Email"
                    "NOTIFICATION" -> "Notification"
                    "MANUAL" -> "Manual"
                    else -> transaction.source.name
                }
                DetailRow(label = "Source", value = sourceFormatted)
                DetailRow(label = "Date", value = transaction.date)
                if (transaction.timestamp > 0) {
                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(transaction.timestamp))
                    DetailRow(label = "Time", value = timeStr)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Original Notification Box
            if (transaction.originalText.isNotBlank()) {
                Text(
                    text = "Original notification",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.inkFaint
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.surfaceAlt, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = transaction.originalText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = appColors.inkMuted
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Action Buttons (Edit & Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Edit Button
                Button(
                    onClick = {
                        onDismiss()
                        onEdit(transaction)
                    },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.surfaceAlt,
                        contentColor = appColors.ink
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(15.dp),
                        tint = appColors.ink
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Edit", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }

                // Delete Button
                Button(
                    onClick = {
                        showDeleteConfirmDialog = true
                    },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.negativeSoft,
                        contentColor = appColors.negative
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(15.dp),
                        tint = appColors.negative
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Delete", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete transaction?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = appColors.ink
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${transaction.title}' (${CurrencyFormatter.format(transaction.amount)})? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.inkMuted
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDismiss()
                        onDelete(transaction)
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Bold,
                        color = appColors.negative
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.inkMuted
                    )
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = appColors.inkMuted
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.ink
        )
    }
}
